package acr.browser.lightning.adblock

import acr.browser.lightning.R
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.isAppScheme
import acr.browser.lightning.utils.isSpecialUrl
import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.collection.LruCache
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.EmptyInputStream
import jp.hazuki.yuzubrowser.adblock.core.*
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest.Companion.TYPE_DOCUMENT
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.getContentType
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import kotlinx.coroutines.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.publicsuffix.PublicSuffix
import okhttp3.internal.toHeaderList
import java.io.*
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AbpBlocker @Inject constructor(
    private val application: Application,
    abpListUpdater: AbpListUpdater,
    private val abpUserRules: AbpUserRules,
    userPreferences: UserPreferences,
    private val logger: Logger
    ) : AdBlocker {

    // use a map of filterContainers instead of several separate containers
    // TODO: could also use list and associate prefix with id, but only if it's considerably faster...
    private val filterContainers = prefixes.associateWith { FilterContainer() }

    // store whether lists are loaded (and delay any request until loading is done)
    private var listsLoaded = false

/*    // element hiding
    //  doesn't work, but maybe it's crucial to inject the js at the right point
    //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
    private var elementBlocker: CosmeticFiltering? = null
    var elementHide = userPreferences.elementHide
*/

    // cache for 3rd party check, allows significantly faster checks
    private val thirdPartyCache = ThirdPartyLruCache(100)

    private val dummyImage: ByteArray by lazy { readByte(application.resources.assets.open("blank.png")) }
    private val dummyResponse by lazy { WebResourceResponse("text/plain", "UTF-8", EmptyInputStream()) }
    private val okHttpClient by lazy { OkHttpClient() } // we only need it for some filters

    init {
        // hilt always loads blocker, even if not used
        //  thus we load the lists only if blocker is actually enabled
        if (userPreferences.adBlockEnabled)
            GlobalScope.launch(Dispatchers.Default) {
                // load lists here if not loaded above
                //  2-5x slower than blocking for some reason -> is there any reasonable compromise?
                loadLists()

                // update all enabled entities/blocklists
                // may take a while depending on how many lists need update, and on internet connection
                if (abpListUpdater.updateAll(false)) { // returns true if anything was updated
                    removeJointLists()
                    loadLists() // update again if files have changed
                }
            }
    }

    fun removeJointLists() {
        val filterDir = application.applicationContext.getFilterDir()
        prefixes.forEach { File(filterDir, it).delete() }
    }

    // load lists
    //  and create files containing filters from all enabled entities (without duplicates)
    fun loadLists() {
        val filterDir = application.applicationContext.getFilterDir()

        // call loadFile for all prefixes and be done if all return true
        // asSequence() should not load all lists and then check, but fail faster if there is a problem
        if (prefixes.asSequence().map {
                    loadFile(File(filterDir, it), it) }.all { true }
        ) {
            listsLoaded = true
            return
        }
        // loading failed or joint lists don't exist: load the normal way and create joint lists

        val entities = AbpDao(application.applicationContext).getAll()
        val abpLoader = AbpLoader(filterDir, entities)

        // toSet() for removal of duplicate entries
        val filters = prefixes.map {
            if (isModify(it))
                it to abpLoader.loadAllModifyFilter(it).toSet()
            else
                it to abpLoader.loadAll(it).toSet()
        }.toMap()

        // use !! to get error
        prefixes.forEach { prefix ->
            filterContainers[prefix]!!.also { filters[prefix]!!.forEach(it::addWithTag) }
        }
        listsLoaded = true

        // create joint files
        prefixes.forEach { prefix -> writeFile(prefix, filters[prefix]!!.sanitize().map {it.second}) }

        /*if (elementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }*/
    }

    private fun Set<Pair<String, UnifiedFilter>>.sanitize(): Collection<Pair<String, UnifiedFilter>> {
        return this.mapNotNull { when {
            // WebResourceRequest.getContentType(pageUri: Uri) never returns TYPE_POPUP
            //  so we can remove filters that act on popup-only
            it.second.contentType == ContentRequest.TYPE_POPUP -> null
            // remove other unnecessary filters?
            //  more unsupported types?
            //  relevant number of cases where there are filters "including" more specific filters?
            //   e.g. ||example.com^ and ||ads.example.com^, or ||example.com^ and ||example.com^$third-party
            //TODO: badfilter should act here! (and thus should probably go to block filters)
            // no, this is too late! because filters are already loaded
            // and with the wildcard thing, it can't be done that easily
            //  -> how to do?
            else -> it
        } }
    }

    private fun loadFile(file: File, prefix: String): Boolean {
        if (file.exists()) {
            try {
                file.inputStream().buffered().use { ins ->
                    val reader = FilterReader(ins)
                    if (reader.checkHeader()) {
                        // use !! to get error
                        if (isModify(prefix))
                            filterContainers[prefix]!!.also { reader.readAllModifyFilters().forEach(it::addWithTag) }
                        else
                            filterContainers[prefix]!!.also { reader.readAll().forEach(it::addWithTag) }
                        // check 2nd "header" at end of the file, to avoid accepting partially written file
                        return reader.checkHeader()
                    }
                }
            } catch(e: IOException) {}
        }
        return false
    }

    private fun writeFile(prefix: String, filters: Collection<UnifiedFilter>?) {
        if (filters == null) return // better throw error, should not happen
        val file = File(application.applicationContext.getFilterDir(), prefix)
        val writer = FilterWriter()
        file.outputStream().buffered().use {
            if (isModify(prefix))
                // use !! to get error if filter.modify is null
                writer.writeModifyFilters(it, filters.toList())
            else
                writer.write(it, filters.toList())
            it.close()
        }
    }

    // from yuzu: jp.hazuki.yuzubrowser.adblock/AdBlockController.kt
    private fun createDummy(uri: Uri): WebResourceResponse {
        val mimeType = getMimeType(uri.toString())
        return if (mimeType.startsWith("image/")) {
            WebResourceResponse("image/png", null, ByteArrayInputStream(dummyImage))
        } else {
            dummyResponse
        }
    }

    // from yuzu: jp.hazuki.yuzubrowser.adblock/AdBlockController.kt
    // stings adjusted for Fulguris
    private fun createMainFrameDummy(uri: Uri, pattern: String): WebResourceResponse {
        val builder = StringBuilder("<meta charset=utf-8>" +
                "<meta content=\"width=device-width,initial-scale=1,minimum-scale=1\"name=viewport>" +
                "<style>body{padding:5px 15px;background:#fafafa}body,p{text-align:center}p{margin:20px 0 0}" +
                "pre{margin:5px 0;padding:5px;background:#ddd}</style><title>")
            .append(application.resources.getText(R.string.request_blocked))
            .append("</title><p>")
            .append(application.resources.getText(R.string.page_blocked))
            .append("<pre>")
            .append(uri)
            .append("</pre><p>")
            .append(application.resources.getText(R.string.page_blocked_reason))
            .append("<pre>")
            .append(pattern)
            .append("</pre>")

        return getNoCacheResponse("text/html", builder)
    }

    /*
    // element hiding
    override fun loadScript(uri: Uri): String? {
        val cosmetic = elementBlocker ?: return null
        return cosmetic.loadScript(uri)
        return null
    }
     */

    // moved from jp.hazuki.yuzubrowser.adblock/AdBlock.kt to allow modified 3rd party detection
    private fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(url, pageUri))

    // initially based on jp.hazuki.yuzubrowser.adblock/AdBlock.kt
    //  modified to use cache for the slow part, decreases average time by 50-70%
    private fun is3rdParty(url: Uri, pageUri: Uri): Boolean {
        val hostName = url.host ?: return true
        val pageHost = pageUri.host ?: return true

        if (hostName == pageHost) return false

        return thirdPartyCache["$hostName/$pageHost"]!! // the create function can't return null!
    }

    // returns null if not blocked, else some WebResourceResponse
    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // always allow special URLs
        // then check user lists (user allow should even override malware list)
        // then mining/malware (ad block allow should not override malware list)
        // then ads

        if (request.url.toString().isSpecialUrl() || request.url.toString().isAppScheme())
            return null

        //logger.log(TAG,"request.isForMainFrame: " + request.isForMainFrame)
        //logger.log(TAG,"request.url: " + request.url)
        //logger.log(TAG,"pageUrl: " + pageUrl)

        // create contentRequest
        // pageUrl can be "" (when opening something in a new tab, or manually entering a URL)
        //  in this case everything gets blocked because of the pattern "|https://"
        //  this is blocked for some specific page domains
        //   and if pageUrl.host == null, domain check return true (in UnifiedFilter.kt)
        //   same for is3rdParty
        // if switching pages (via link or pressing back), pageUrl is still the old url, messing up 3rd party checks
        // -> fix both by setting pageUrl to requestUrl if request.isForMainFrame
        //  is there any way a request for main frame can be a 3rd party request? then a different fix would be required
        val contentRequest = request.getContentRequest(if (request.isForMainFrame || pageUrl.isBlank()) request.url else Uri.parse(pageUrl))

        // no need to supply pattern to getBlockResponse
        //  pattern only used if it's for main frame
        //  and if it's for main frame and blocked by user, it's always because user chose to block entire domain
        abpUserRules.getResponse(contentRequest)?.let { response ->
            return if (response) getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_user, contentRequest.pageUrl.host ?: ""))
             else null
        }

        // wait until blocklists are loaded
        //  web request stuff does not run on main thread, so thread.sleep should be ok
        while (!listsLoaded) {
            Thread.sleep(50)
        }

        // use !! to get an error if sth is wrong
        filterContainers[ABP_PREFIX_IMPORTANT_ALLOW]!![contentRequest]?.let { return null }

        // https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters#important
        //  -> "The $important modifier will be ignored if a document-level exception rule is applied to the document."
        filterContainers[ABP_PREFIX_IMPORTANT]!![contentRequest]?.let {
            return if (filterContainers[ABP_PREFIX_ALLOW]!![contentRequest]?.let { allowFilter ->
                    allowFilter.contentType == TYPE_DOCUMENT } == true) // document-level exception
                        null
            else getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_malware, it.pattern))
        }
        filterContainers[ABP_PREFIX_ALLOW]!![contentRequest]?.let { return null }
        filterContainers[ABP_PREFIX_DENY]!![contentRequest]?.let {
            //if (it.pattern.isNotBlank()) {
                return getBlockResponse(
                    request, application.resources.getString(R.string.page_blocked_list_ad, it.pattern))
            //}
        }

        // careful: we need to get ALL matching modify filters, not just one (like it's done for block and allow decisions)
        val modifyFilters = filterContainers[ABP_PREFIX_MODIFY]!!.getAll(contentRequest)
        if (modifyFilters.isNotEmpty()) {
            if (request.url.encodedQuery == null) {
                // if no parameters, remove all removeparam filters
                    // TODO: compare speed checking by class vs the old way via string
//                modifyFilters.removeAll { it.modify!!.prefix == MODIFY_PREFIX_REMOVEPARAM }
                modifyFilters.removeRemoveparam()
                if (modifyFilters.isEmpty()) return null
            }
            // there is a hit, but first check whether the exact filter has an exception
            val modifyExceptions = filterContainers[ABP_PREFIX_MODIFY_EXCEPTION]!!.getAll(contentRequest)
            if (modifyExceptions.isNotEmpty()) {
                /* how exceptions/negations work: (adguard removeparam documentation is useful: https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters)
                 *  without parameter (i.e. empty), all filters of that type (removeparam, csp,...) are invalid
                 *  with parameter, only same type and same parameter are considered invalid
                 */

                modifyExceptions.forEach { exception ->
                    if (exception.modify!!.parameter == null) // no parameter -> remove all modify of same type
                        modifyFilters.removeAll { it.modify!!.prefix == exception.modify!!.prefix }
                    else // else remove exact matches in modify
                        modifyFilters.removeAll { it.modify == exception.modify }
                }
            }

            // important: there can be multiple filters valid, and all should be applied if possible
            //  just do one after the other
            //  but since I can't change the WebResourceRequest it must all happen within getModifiedResponse()
            return getModifiedResponse(request, modifyFilters)
        }

        return null
    }

    private fun getBlockResponse(request: WebResourceRequest, pattern: String): WebResourceResponse {
        var response = if (request.isForMainFrame) {
            createMainFrameDummy(request.url, pattern)
        }
        else {
            createDummy(request.url)
        }

        //SL: We used when debugging
        // See: https://github.com/Slion/Fulguris/issues/225
        // TODO: Though we should really set a status code TBH, could be 200 or 404 depending of our needs I guess
        //response.setStatusCodeAndReasonPhrase(404, pattern)
        return response
    }

    // this needs to be fast, the adguard url tracking list has quite a few options acting on all urls!
    //  e.g. removing the fbclid parameter added by facebook
    private fun getModifiedResponse(request: WebResourceRequest, filters: MutableList<ContentFilter>): WebResourceResponse? {
        // we can't simply modify the request, so do what the request wants, but modify
        //  then deliver what we got as response
        //  like in https://stackoverflow.com/questions/7610790/add-custom-headers-to-webview-resource-requests-android

        // first apply redirect filters
        //  this always redirects to some internal resources, see uBo documentation (so other filters are obsolete)
        filters.forEach {
            if (it.modify!! is RedirectFilter)
                return redirectResponse(it.modify!!.parameter!!)
        }

        // apply removeparam
        val parameters = getModifiedParameters(request, filters)
        filters.removeRemoveparam()
        if (parameters == null && filters.isEmpty()) return null

        // apply removeheaders, request part
        val requestHeaders = request.requestHeaders
        val headerSize = requestHeaders.size
        filters.forEach { filter ->
            if (filter.modify!! is RemoveHeaderFilter && filter.modify!!.inverse) { // why can't i access 'request'? but doesn't matter...
                requestHeaders as MutableMap
                requestHeaders.keys.forEach {
                    if (it.lowercase() == filter.modify!!.parameter)
                        requestHeaders.remove(it)
                }
            }
        }
        filters.removeAll { it.modify!! is RemoveHeaderFilter && it.modify!!.inverse }
        if (filters.isEmpty() && headerSize == requestHeaders.size) return null

        val newRequest =  Request.Builder()
            .url(
                if (parameters == null)
                    request.url.toString()
                else
                    request.url.toString().substringBefore('?').substringBefore('#') + // url without parameters and fragment
                            parameterString(parameters) + // add modified parameters
                            (request.url.fragment ?: "") // add fragment
                )
            .method(request.method, null) // use same method, TODO: is body null really ok?
            .headers(requestHeaders.toHeaders())
            .build() // anything missing?

        // TODO: does this still apply? https://artemzin.com/blog/android-webview-io/
        //  I think not (because I see multiple threads being used), but need to check!
        //  if it still applies -> what do?
        val call = okHttpClient.newCall(newRequest)
        try {
            val response = call.execute()

            val headers = mutableMapOf<String, String>()
            response.headers.toHeaderList().forEach {
                // TODO: there should not be duplicate anythings, as this is uninterpreted header line... but better make sure?
                headers[it.name.utf8()] = it.value.utf8()
            }

            // now apply filters that run on the response
            filters.forEach eachFilter@{ filter ->
                when (filter.modify!!) {
                    is CspFilter -> {
                        // from uBo documentation https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#modifier-filters:
                        //   "This option will inject Content-Security-Policy header to the HTTP network response of the requested web page. It can be applied to main document and documents in frames."
                        // -> add header Content-Security-Policy with content = modify.parameter
                        headers.keys.forEach {
                            if (it.lowercase() == "content-security-policy") { // header names are case insensitive, but we want to modify as little as possible
                                headers[it] = headers[it] + "; " + filter.modify!!.parameter
                                return@eachFilter
                            }
                        }
                        headers["Content-Security-Policy"] = filter.modify!!.parameter!!
                    }
                    // removeheaders, response part
                    is RemoveHeaderFilter -> {
                        // no need to check whether it's for request, those are already removed
                        headers.keys.forEach {
                            if (it.lowercase() == filter.modify!!.parameter)
                                headers.remove(it)
                        }
                    }
                    else -> throw(IOException("should not happen!"))
                }
            }

            return response.body?.let {
                return WebResourceResponse(
                    response.header("content-type", "text/plain"),
                    response.header("content-encoding", "utf-8"),
                    response.code,
                    "", // TODO: should be ok... where to get reason from okhttp?
                    headers,
                    it.byteStream())
            }
        } catch (e: IOException) {
            return null // TODO: what do? empty response? null to let webview try again? but the it's unmodified...
        }
    }

    // resources from uBlockOrigin!
    //  TODO: implement it in a better maintainable version, maybe something generated with a script
    //   from https://github.com/gorhill/uBlock/blob/master/src/js/redirect-engine.js
    private fun redirectResponse(resource: String): WebResourceResponse =
        when (resource) {
            "1x1.gif", "1x1-transparent.gif" ->
                WebResourceResponse("image/gif", null, redirectFile("1x1.gif"))
            "2x2.png", "2x2-transparent.png" ->
                WebResourceResponse("image/png", null, redirectFile("2x2.png"))
            "3x2.png", "3x2-transparent.png" ->
                WebResourceResponse("image/png", null, redirectFile("3x2.png"))
            "32x32.png", "32x32-transparent.png" ->
                WebResourceResponse("image/png", null, redirectFile("32x32.png"))
            "addthis_widget.js", "addthis.com/addthis_widget.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("addthis_widget.js"))
            "amazon_ads.js", "amazon-adsystem.com/aax2/amzn_ads.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("amazon_ads.js"))
            "amazon_apstag.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("amazon_apstag.jsf"))
            "ampproject_v0.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("ampproject_v0.js"))
            "chartbeat.js", "static.chartbeat.com/chartbeat.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("chartbeat.js"))
            "click2load.html", "aliasURL", "url" ->
                WebResourceResponse("text/html", "utf-8", redirectFile("click2load.html"))
            "doubleclick_instream_ad_status.js", "doubleclick.net/instream/ad_status.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("doubleclick_instream_ad_status.js"))
            "empty" ->
                WebResourceResponse("text/plain", "utf-8", redirectFile("empty"))
            "fingerprint2.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("fingerprint2.js"))
            "google-nalytics_analytics.js", "google-analytics.com/analytics.js", "googletagmanager_gtm.js", "googletagmanager.com/gtm.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("google-analytics_analytics.js"))
            "google-analytics_cx_api.js", "google-analytics.com/cx/api.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("google-analytics_cx_api.js"))
            "google-analytics_ga.js", "google-analytics.com/ga.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("google-analytics_ga.js"))
            "google-analytics_inpage_linkid.js", "google-analytics.com/inpage_linkid.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("google-analytics_inpage_linkid.js"))
            "googlesyndication_adsbygoogle.js", "googlesyndication.com/adsbygoogle.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("googlesyndication_adsbygoogle.js"))
            "googletagservices_gpt.js", "googletagservices.com/gpt.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("googletagservices_gpt.js"))
            "hd-main.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("hd-main.js"))
            "ligatus_angular-tag.js", "ligatus.com/*/angular-tag.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("1x1.gif"))
            "mxpnl_mixpanel.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("mxpnl_mixpanel.js"))
            "monkeybroker.js", "d3pkae9owd2lcf.cloudfront.net/mb105.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("monkeybroker.js"))
            "noeval.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("noeval.js"))
            "noeval-silent.js", "silent-noeval.js'" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("noeval-silent.js"))
            "nobab2.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("nobab2.js"))
            "nofab.js", "fuckadblock.js-3.2.0" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("nofab.js"))
            "noop-0.1s.mp3", "noopmp3-0.1s", "abp-resource:blank-mp3" ->
                WebResourceResponse("audio/mpeg", null, redirectFile("noop-0.1s.mp3"))
            "noop-1s.mp4", "noopmp4-1s" ->
                WebResourceResponse("video/mp4", null, redirectFile("noop-1s.mp4"))
            "noop.html", "noopframe" ->
                WebResourceResponse("text/html", "utf-8", redirectFile("noop.html"))
            "noop.js", "noopjs", "abp-resource:blank-js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("noop.js"))
            "noop.txt", "nooptext" ->
                WebResourceResponse("text/plain", "utf-8", redirectFile("noop.txt"))
            "noop-vmap1.0.xml", "noopvmap-1." ->
                WebResourceResponse("application/xml", "utf-8", redirectFile("noop-vmap1.0.xml"))
            "outbrain-widget.js", "widgets.outbrain.com/outbrain.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("outbrain-widget.js"))
            "popads.js", "popads.net.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("popads.js"))
            "popads-dummy.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("popads-dummy.js"))
            "scorecardresearch_beacon.js", "scorecardresearch.com/beacon.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("scorecardresearch_beacon.js"))
            "window.open-defuser.js", "nowoif.js" ->
                WebResourceResponse("application/javascript", "utf-8", redirectFile("window.open-defuser.js"))
            else -> dummyResponse
        }

    private fun redirectFile(name: String) = application.resources.assets.open("blocker_resources/$name")


    companion object {
        private val prefixes = listOf(ABP_PREFIX_ALLOW, ABP_PREFIX_DENY, ABP_PREFIX_MODIFY, ABP_PREFIX_MODIFY_EXCEPTION, ABP_PREFIX_IMPORTANT, ABP_PREFIX_IMPORTANT_ALLOW)

        private const val BUFFER_SIZE = 1024 * 8
        private const val TAG = "AbpBlocker"

        // from jp.hazuki.yuzubrowser.core.utility.utils/IOUtils.java
        @Throws(IOException::class)
        fun readByte(inputStream: InputStream): ByteArray {
            val buffer = ByteArray(BUFFER_SIZE)
            val bout = ByteArrayOutputStream()
            var n: Int
            while (inputStream.read(buffer).also { n = it } >= 0) {
                bout.write(buffer, 0, n)
            }
            return bout.toByteArray()
        }

        // from jp.hazuki.yuzubrowser.core.utility.utils/FileUtils.kt
        private const val MIME_TYPE_UNKNOWN = "application/octet-stream"
        fun getMimeType(fileName: String): String {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = fileName.substring(lastDot + 1).toLowerCase()
                return getMimeTypeFromExtension(extension)
            }
            return "application/octet-stream"
        }

        // from jp.hazuki.yuzubrowser.core.utility.utils/FileUtils.kt
        fun getMimeTypeFromExtension(extension: String): String {
            return when (extension) {
                "js" -> "application/javascript"
                "mhtml", "mht" -> "multipart/related"
                "json" -> "application/json"
                else -> {
                    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    if (type.isNullOrEmpty()) {
                        MIME_TYPE_UNKNOWN
                    } else {
                        type
                    }
                }
            }
        }

        // from jp.hazuki.yuzubrowser.core.utility.extensions/HtmlExtensions.kt
        fun getNoCacheResponse(mimeType: String, sequence: CharSequence): WebResourceResponse {
            return getNoCacheResponse(
                mimeType, ByteArrayInputStream(
                    sequence.toString().toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            )
        }

        // from jp.hazuki.yuzubrowser.core.utility.extensions/HtmlExtensions.kt
        private fun getNoCacheResponse(mimeType: String, stream: InputStream): WebResourceResponse {
            val response = WebResourceResponse(mimeType, "UTF-8", stream)
            response.responseHeaders =
                HashMap<String, String>().apply { put("Cache-Control", "no-cache") }
            return response
        }

        private fun isModify(prefix: String) = prefix in listOf(ABP_PREFIX_MODIFY, ABP_PREFIX_MODIFY_EXCEPTION)

        // applies filters to parameters and returns remaining parameters
        // returns null of parameters are not modified
        fun getModifiedParameters(request: WebResourceRequest, filters: List<ContentFilter>): Map<String, String>? {
            val parameters = request.url.getQueryParameterMap()
            var changed = false
            if (parameters.isEmpty()) return null // TODO: should not happen, maybe remove this check
            filters.forEach { filter ->
                when (val modify = filter.modify!!) {
                    is RemoveparamRegexFilter -> {} // TODO: use the matcher!
                    is RemoveparamFilter -> {
                        if (modify.parameter == null)
                            return emptyMap() // means: remove all parameters
                        changed = changed or if (modify.inverse)
                            parameters.entries.retainAll { it.key == modify.parameter }
                        else
                            parameters.entries.removeAll { it.key == modify.parameter }
                    }
                }
            }
            return if (changed) parameters else null
        }

        fun MutableList<ContentFilter>.removeRemoveparam() =
            removeAll { it.modify!!::class.java.isAssignableFrom(RemoveparamFilter::class.java) }

        fun parameterString(parameters: Map<String, String>) = if (parameters.isEmpty()) ""
        else "?" + parameters.entries.joinToString("&") { it.key + "=" + it.value }

        // TODO: is encoded query and decode necessary?
        //  is it slower than using decoded query?
        // using LinkedHashMap to keep original order
        fun Uri.getQueryParameterMap(): LinkedHashMap<String, String> {
            // using some code from android.net.uri.getQueryParameters()
            val query = encodedQuery ?: return linkedMapOf()
            val parameters = linkedMapOf<String, String>()
            var start = 0
            do {
                val next = query.indexOf('&', start)
                val end = if (next == -1) query.length else next
                var separator = query.indexOf('=', start)
                if (separator > end || separator == -1) {
                    separator = end
                }
                parameters[Uri.decode(query.substring(start, separator))] = // parameter name
                        Uri.decode(query.substring(if (separator < end) separator + 1 else end, end)) // parameter value
                start = end + 1
            } while (start < query.length)
            return parameters
        }

        private class ThirdPartyLruCache(size: Int): LruCache<String, Boolean>(size) {
            override fun create(key: String): Boolean {
                return key.split('/').let { is3rdParty(it[0], it[1])}
            }

            private fun is3rdParty(hostName: String, pageHost: String): Boolean {
                val ipPattern = PatternsCompat.IP_ADDRESS
                if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches())
                    return true
                val db = PublicSuffix.get()
                return db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost)
            }
        }

    }
}
