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
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.publicsuffix.PublicSuffix
import okhttp3.internal.toHeaderList
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
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
    private val filterContainers = prefixes.associateWith { FilterContainer() }

    // store whether lists are loaded (and delay any request until loading is done)
    private var listsLoaded = false

    private val okHttpClient by lazy { OkHttpClient() } // we only need it for some filters

    private val thirdPartyCache = ThirdPartyLruCache(100)

    private val blocker = AbpBlocker(abpUserRules, filterContainers)

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
        prefixes.forEach { File(filterDir, it).delete() }
    }

    // load lists
    //  and create files containing filters from all enabled entities (without duplicates)
    fun loadLists() {
        val filterDir = application.applicationContext.getFilterDir()

        // call loadFile for all prefixes and be done if all return true
        // asSequence() should not load all lists and then check, but fail faster if there is a problem
        if (prefixes.asSequence().map { loadFileToContainer(File(filterDir, it), it) }.all { it }) {
            listsLoaded = true
            return
        }
        // loading failed or joint lists don't exist: load the normal way and create joint lists

        val entities = AbpDao(application.applicationContext).getAll()
        val abpLoader = AbpLoader(filterDir, entities)

        // toSet() for removal of duplicate entries
        val filters = prefixes.map { it to abpLoader.loadAll(it).toSet() }.toMap()

        prefixes.forEach { prefix ->
            filterContainers[prefix]!!.clear() // clear container, or disabled filter lists will still be active
            filterContainers[prefix]!!.also { filters[prefix]!!.forEach(it::addWithTag) }
        }
        listsLoaded = true

        // create joint files
        // TODO: don't just write! after sanitize, reload filter containers!
        //  if load is slow and sanitize is fast: only load after sanitize
        prefixes.forEach { prefix ->
            writeFile(prefix, filters[prefix]!!.sanitize().map { it.second })
        }

        /*if (elementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }*/
    }

    private fun Set<Pair<String, UnifiedFilter>>.sanitize(): Collection<Pair<String, UnifiedFilter>> {
        return this.mapNotNull {
            when {
                // TODO: badfilter should act here! (first thing!)
                //  for now, load badfilter from filter sets (for important, block, allow, modify only)
                //   and remove matching filters in the set of appropriate type
                //  which wildcard domain matching as described on https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters#badfilter-modifier
                //   resp. https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#badfilter
                //   -> if badfilter matches filter only ignoring domains -> remove matching domains from the filter, also match wildcard

                // WebResourceRequest.getContentType(pageUri: Uri) never returns TYPE_POPUP
                //  so we can remove filters that act on popup-only
                it.second.contentType == ContentRequest.TYPE_POPUP -> null

                // remove other unnecessary filters?
                //  more unsupported types?

                // TODO: remove filters already included in others
                //   e.g. ||example.com^ and ||ads.example.com^, or ||example.com^ and ||example.com^$third-party
                //  ->
                //    combine filters that have same type and same pattern (we have the tag here as shortcut!) if difference is content type
                //      other.contentType = it.contentType and other.contentType
                //      null (to remove this filter)
                //    check StartEndFilters that could be subdomain (not necessarily same patter, but this would accelerate match by a lot)
                //      pattern does not contain '/', one endswith other

                else -> it
            }
        }
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
        // always allow special URLs
        // then check user lists (user allow should even override malware list)
        // then mining/malware (ad block allow should not override malware list)
        // then ads

        // TODO: also allow files, not only special urls?
        if (request.url.toString().isSpecialUrl() || request.url.toString().isAppScheme())
            return null

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
            is OkhttpResponse -> {
                try {
                    val webResponse = okHttpClient.newCall(response.request).execute()
                    if (response.addHeaders == null && response.removeHeaders == null)
                        return webResponse.toWebResourceResponse(null)
                    val headers = webResponse.headers.toMap()
                    response.addHeaders?.forEach eachHeader@{ addHeader ->
                        headers.keys.forEach {
                            // header names are case insensitive, but we want to modify as little as possible
                            if (it.lowercase() == addHeader.key.lowercase()) {
                                headers[it] = headers[it] + "; " + addHeader.value
                                return@eachHeader
                            }
                        }
                        headers[addHeader.key] = addHeader.value
                    }
                    response.removeHeaders?.forEach { removeHeader ->
                        headers.keys.forEach {
                            if (it.lowercase() == removeHeader.lowercase())
                                headers.remove(it)
                        }
                    }
                    return webResponse.toWebResourceResponse(headers)
                } catch (e: IOException) {
                    // TODO: what do?
                    //  empty WebResourceResponse? it's like blocking... maybe with some error response code?
                    //  null to let WebView try again? but then the filters are not applied
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
            else -> throw(IOException()) //application.resources.getString(R.string.page_blocked_list_ad, pattern) // TODO: exception for testing only
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

    private fun Response.toWebResourceResponse(modifiedHeaders: Map<String, String>?) =
        WebResourceResponse(
            header("content-type", "text/plain"),
            header("content-encoding", "utf-8"),
            code,
            message.let { if (it.isEmpty()) "OK" else it }, // reason must not be empty!
            modifiedHeaders ?: headers.toMap(),
            body?.byteStream() ?: EmptyInputStream()
        )

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
        val prefixes = listOf(
            ABP_PREFIX_ALLOW,
            ABP_PREFIX_DENY,
            ABP_PREFIX_MODIFY,
            ABP_PREFIX_MODIFY_EXCEPTION,
            ABP_PREFIX_IMPORTANT,
            ABP_PREFIX_IMPORTANT_ALLOW
        )

        fun isModify(prefix: String) = prefix in listOf(ABP_PREFIX_MODIFY, ABP_PREFIX_MODIFY_EXCEPTION)

        private const val TAG = "AbpBlocker"

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
