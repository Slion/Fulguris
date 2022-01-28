package acr.browser.lightning.adblock

import acr.browser.lightning.R
import acr.browser.lightning.adblock.AbpBlocker.Companion.addHeader
import acr.browser.lightning.adblock.AbpBlocker.Companion.removeHeader
import acr.browser.lightning.constant.FILE
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
import jp.hazuki.yuzubrowser.adblock.core.AbpLoader
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.getFilterDir
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.getContentType
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.publicsuffix.PublicSuffix
import okhttp3.internal.toHeaderList
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AbpBlockerManager @Inject constructor(
    private val application: Application,
    abpListUpdater: AbpListUpdater,
    abpUserRules: AbpUserRules,
    userPreferences: UserPreferences,
    private val logger: Logger
) : AdBlocker {

    // use a map of filterContainers instead of several separate containers
    private val filterContainers = blockerPrefixes.associateWith { FilterContainer() }

    // store whether lists are loaded (and delay any request until loading is done)
    private var listsLoaded = false

    private val okHttpClient by lazy { OkHttpClient() } // we only need it for some filters

    private val thirdPartyCache = ThirdPartyLruCache(100)

    private val blocker = AbpBlocker(abpUserRules, filterContainers)

    private val cacheDir by lazy { FILE + application.cacheDir.absolutePath }
    /*    // element hiding
        //  doesn't work, but maybe it's crucial to inject the js at the right point
        //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
        private var elementBlocker: CosmeticFiltering? = null
        var elementHide = userPreferences.elementHide
    */

    init {
        // hilt always loads blocker, even if not used
        //  thus we load the lists only if blocker is actually enabled
        if (userPreferences.adBlockEnabled)
            GlobalScope.launch(Dispatchers.Default) {
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
        blockerPrefixes.forEach { File(filterDir, it).delete() }
    }

    // load lists
    //  and create files containing filters from all enabled entities (without duplicates)
    fun loadLists() {
        val filterDir = application.applicationContext.getFilterDir()

        // call loadFile for all prefixes and be done if all return true
        // asSequence() should not load all lists and then check, but fail faster if there is a problem
        if (blockerPrefixes.asSequence().map { loadFileToContainer(File(filterDir, it), it) }.all { it }) {
            listsLoaded = true
            return
        }
        // loading failed or joint lists don't exist: load the normal way and create joint lists

        val entities = AbpDao(application.applicationContext).getAll()
        val abpLoader = AbpLoader(filterDir, entities)

        val filters = blockerPrefixes.associateWith { prefix ->
            abpLoader.loadAll(prefix).toSet().sanitize(abpLoader.loadAll(ABP_PREFIX_BADFILTER + prefix).toSet())
        }

        blockerPrefixes.forEach { prefix ->
            filterContainers[prefix]!!.clear() // clear container, or disabled filter lists will still be active
            filterContainers[prefix]!!.also { filters[prefix]!!.forEach(it::addWithTag) }
        }
        listsLoaded = true

        // create joint files
        // tags will be created again, this is unnecessary, but fast enough to not care about it very much
        blockerPrefixes.forEach { prefix ->
            writeFile(prefix, filters[prefix]!!.map { it.second })
        }

        /*if (elementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }*/
    }

    private fun loadFileToContainer(file: File, prefix: String): Boolean {
        if (file.exists()) {
            try {
                file.inputStream().buffered().use { ins ->
                    val reader = FilterReader(ins)
                    if (!reader.checkHeader())
                        return false
                    if (isModify(prefix))
                        reader.readAllModifyFilters().forEach(filterContainers[prefix]!!::addWithTag)
                    else
                        reader.readAll().forEach(filterContainers[prefix]!!::addWithTag)
                    // check 2nd "header" at end of the file, to avoid accepting partially written file
                    return reader.checkHeader()
                }
            } catch (e: IOException) { // nothing to do, returning false anyway
            }
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

    // returns null if not blocked, else some WebResourceResponse
    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // always allow special URLs, app scheme and cache dir (used for favicons)
        request.url.toString().let {
            if (it.isSpecialUrl() || it.isAppScheme() || it.startsWith(cacheDir))
                return null
        }

        // create contentRequest
        // pageUrl can be "" (when opening something in a new tab, or manually entering a URL)
        //  in this case everything gets blocked because of the pattern "|https://"
        //  this is blocked for some specific page domains
        //   and if pageUrl.host == null, domain check return true (in UnifiedFilter.kt)
        //   same for is3rdParty
        // if switching pages (via link or pressing back), pageUrl is still the old url, messing up 3rd party checks
        // -> fix both by setting pageUrl to requestUrl if request.isForMainFrame
        //  is there any way a request for main frame can be a 3rd party request? then a different fix would be required
        val contentRequest = request.getContentRequest(
            if (request.isForMainFrame || pageUrl.isBlank()) request.url else Uri.parse(pageUrl)
        )

        // wait until blocklists are loaded
        //  web request stuff does not run on main thread, so thread.sleep should be ok
        while (!listsLoaded) {
            Thread.sleep(50)
        }

        // blocker shouldBlock
        val response = blocker.shouldBlock(contentRequest) ?: return null

        when (response) {
            is BlockResponse -> {
                return if (request.isForMainFrame) {
                    createMainFrameDummy(request.url, response.blockList, response.pattern)
                } else
                    BlockResourceResponse(RES_EMPTY).toWebResourceResponse()
            }
            is BlockResourceResponse -> return response.toWebResourceResponse()
            is ModifyResponse -> {
                // okhttp accepts only ws, wss, http, https, can't build a request otherwise
                //  so don't block other schemes
                if (request.url.scheme !in okHttpAcceptedSchemes)
                    return null
                // for some reason, requests done via okhttp often cause problems with sites that
                //  require login, or with some full screen cookie dialogs
                // currently we reduce the problems by not executing modified requests for main frame
                //  this is not good, as filter options like $csp are main frame only
                //  and some pages are still broken
                if (request.isForMainFrame)
                    return null
                // webresourcerequest does not contain request body, but these request types must or can have a body
                if (request.method == "POST" || request.method == "PUT" || request.method == "PATCH" || request.method == "DELETE")
                    return null
                try {
                    val newRequest = Request.Builder()
                        .url(response.url)
                        .method(response.requestMethod, null) // use same method, no body to copy from WebResourceRequest
                        .headers(response.requestHeaders.toHeaders())
                        .build()
                    val webResponse = okHttpClient.newCall(newRequest).execute()
                    if (response.addResponseHeaders == null && response.removeResponseHeaders == null)
                        return webResponse.toWebResourceResponse(null)
                    val headers = webResponse.headers.toMap()
                    response.addResponseHeaders?.forEach { headers.addHeader(it) }
                    response.removeResponseHeaders?.forEach { headers.removeHeader(it) }
                    return webResponse.toWebResourceResponse(headers)
                } catch (e: IOException) {
                    // connection problems
                    logger.log(TAG, "error while doing okhttp request", e)
                    return null
                } catch (e: IllegalArgumentException) {
                    // request cannot be created, usually this is because of wrong scheme,
                    //  or not providing a body it the method requires one
                    // both cases are checked, so nothing should happen
                    logger.log(TAG, "error while creating okhttp request", e)
                    return null
                }
            }
        }
        // put strings and files into blocked response
        // get okhttp response and modify headers
        return null
    }

    // moved from jp.hazuki.yuzubrowser.adblock/AdBlock.kt to allow modified 3rd party detection
    private fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(url, pageUri), requestHeaders, method)

    // initially based on jp.hazuki.yuzubrowser.adblock/AdBlock.kt
    private fun is3rdParty(url: Uri, pageUri: Uri): Boolean {
        val hostName = url.host ?: return true
        val pageHost = pageUri.host ?: return true

        if (hostName == pageHost) return false

        return thirdPartyCache["$hostName/$pageHost"]!! // thirdPartyCache.Create can't return null!
    }

    // builder part from yuzu: jp.hazuki.yuzubrowser.adblock/AdBlockController.kt
    private fun createMainFrameDummy(uri: Uri, blockList: String, pattern: String): WebResourceResponse {
        val reasonString = when (blockList) {
            USER_BLOCKED -> application.resources.getString(R.string.page_blocked_list_user, pattern)
            ABP_PREFIX_IMPORTANT -> application.resources.getString(R.string.page_blocked_list_malware, pattern)
            ABP_PREFIX_DENY -> application.resources.getString(R.string.page_blocked_list_ad, pattern) // should only be ABP_PREFIX_DENY
            else -> {
                logger.log(TAG, "unexpected blocklist when creating main frame dummy: $blockList")
                application.resources.getString(R.string.page_blocked_list_ad, pattern)
            }
        }

        val builder = StringBuilder(
            "<meta charset=utf-8>" +
                    "<meta content=\"width=device-width,initial-scale=1,minimum-scale=1\"name=viewport>" +
                    "<style>body{padding:5px 15px;background:#fafafa}body,p{text-align:center}p{margin:20px 0 0}" +
                    "pre{margin:5px 0;padding:5px;background:#ddd}</style><title>"
        )
            .append(application.resources.getText(R.string.request_blocked))
            .append("</title><p>")
            .append(application.resources.getText(R.string.page_blocked))
            .append("<pre>")
            .append(uri)
            .append("</pre><p>")
            .append(application.resources.getText(R.string.page_blocked_reason))
            .append("<pre>")
            .append(reasonString)
            .append("</pre>")

        return getNoCacheResponse("text/html", builder)
    }

    private fun Response.toWebResourceResponse(modifiedHeaders: Map<String, String>?): WebResourceResponse {
        // content-type usually has format text/html: charset=utf-8
        // TODO: is this ok? worked in tests, but are there cases where it doesn't work?
        val contentType = header("content-type", "text/plain")?.split(';')
        return WebResourceResponse(
            contentType?.first(),
            contentType?.last()?.substringAfter('='),
            code,
            message.let { if (it.isEmpty()) "OK" else it }, // reason must not be empty!
            modifiedHeaders ?: headers.toMap(),
            body?.byteStream() ?: EmptyInputStream()
        )
    }

    private fun Headers.toMap(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        toHeaderList().forEach {
            map[it.name.utf8()] = it.value.utf8()
        }
        return map
    }

    // TODO: load from file every time? is there some caching in the background? cache stuff manually?
    private fun BlockResourceResponse.toWebResourceResponse(): WebResourceResponse {
        val mimeType = getMimeType(filename)
        return WebResourceResponse(
            mimeType,
            if (mimeType.startsWith("application") || mimeType.startsWith("text"))
                "utf-8"
            else null,
            application.assets.open("blocker_resources/$filename")
        )
    }

    /*
    // element hiding
    override fun loadScript(uri: Uri): String? {
        val cosmetic = elementBlocker ?: return null
        return cosmetic.loadScript(uri)
        return null
    }
     */

    companion object {
        val blockerPrefixes = listOf(
            ABP_PREFIX_ALLOW,
            ABP_PREFIX_DENY,
            ABP_PREFIX_MODIFY,
            ABP_PREFIX_MODIFY_EXCEPTION,
            ABP_PREFIX_IMPORTANT,
            ABP_PREFIX_IMPORTANT_ALLOW,
            ABP_PREFIX_REDIRECT,
            ABP_PREFIX_REDIRECT_EXCEPTION,
        )
        val badfilterPrefixes = blockerPrefixes.map { ABP_PREFIX_BADFILTER + it}

        fun isModify(prefix: String) = prefix in listOf(ABP_PREFIX_MODIFY, ABP_PREFIX_MODIFY_EXCEPTION, ABP_PREFIX_REDIRECT, ABP_PREFIX_REDIRECT_EXCEPTION)

        private const val TAG = "AbpBlocker"

        private val okHttpAcceptedSchemes = listOf("https", "http", "ws", "wss")

        // from jp.hazuki.yuzubrowser.core.utility.utils/FileUtils.kt
        private const val MIME_TYPE_UNKNOWN = "application/octet-stream"
        fun getMimeType(fileName: String): String {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = fileName.substring(lastDot + 1).lowercase()
                    // strip potentially leftover parameters and fragment
                    .substringBefore('?').substringBefore('#')
                return getMimeTypeFromExtension(extension)
            }
            return MIME_TYPE_UNKNOWN
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

        fun Collection<Pair<String, UnifiedFilter>>.sanitize(badFilters: Collection<Pair<String, UnifiedFilter>>): List<Pair<String, UnifiedFilter>> {
            val badFilterFilters = badFilters.map { it.second }
            val filters = mapNotNull {
                if (it.second.contentType == ContentRequest.TYPE_POPUP
                    || badFilterFilters.contains(it.second)
                )
                    null
                else it
            }
            // TODO: badfilter should also work with wildcard domain matching as described on https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters#badfilter-modifier
            //  resp. https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#badfilter
            //  -> if badfilter matches filter only ignoring domains -> remove matching domains from the filter, also match wildcard

            // TODO 2: remove filters already included in others
            //  e.g. ||example.com^ and ||ads.example.com^, or ||example.com^ and ||example.com^$third-party
            //  how to do:
            //    combine filters that have same type and same pattern if difference is content type
            //      simply other.contentType = it.contentType and other.contentType (and remove filter "it")
            //      but: how to check quickly? we have the tag, but checking the entire list for every entry is bad
            //       maybe this part of sanitize should work on the filterContainer?
            //    check StartEndFilters that could be subdomain
            //      remove longer if pattern does not contain '/', and one ends with other
            //      do some pre-matching using tags. will not find everything, but much faster
            //  also remove unnecessary filters, like ||example.com^ if there is @@||example.com^
            //   how to check properly? would need to do go through tags of allowlist, and remove blocklist entries that match
            //   but likely there aren't many hits, so this is low priority
            return filters
        }

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
