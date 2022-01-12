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
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.EmptyInputStream
import jp.hazuki.yuzubrowser.adblock.core.*
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.getFilterDir
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.getContentType
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import kotlinx.coroutines.*
import okhttp3.internal.publicsuffix.PublicSuffix
import java.io.*
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.HashMap

@Singleton
class AbpBlocker @Inject constructor(
    private val application: Application,
    abpListUpdater: AbpListUpdater,
    private val abpUserRules: AbpUserRules,
    userPreferences: UserPreferences,
    private val logger: Logger
    ) : AdBlocker {

    private lateinit var allowList: FilterContainer
    private lateinit var blockList: FilterContainer

    // contains filters that should not be overridden by allowList
    //  like mining list, malware list or maybe later the 'important' filter rules from AdGuard/uBo
    private var importantBlockList = FilterContainer()

    // store whether lists are loaded (and delay any request until loading is done)
    private var listsLoaded = false

/*    // element hiding
    //  doesn't work, but maybe it's crucial to inject the js at the right point
    //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
    private var elementBlocker: CosmeticFiltering? = null
    var elementHide = userPreferences.elementHide
*/

    // cache for 3rd party check, allows significantly faster checks
    private val thirdPartyCache = mutableMapOf<String, Boolean>()
    private val thirdPartyCacheSize = 100

    private val dummyImage: ByteArray by lazy { readByte(application.resources.assets.open("blank.png")) }
    private val dummyResponse by lazy { WebResourceResponse("text/plain", "UTF-8", EmptyInputStream()) }

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
        File(filterDir, ABP_PREFIX_ALLOW).delete()
        File(filterDir, ABP_PREFIX_DENY).delete()
    }

    // load lists
    //  and create files containing filters from all enabled entities (without duplicates)
    fun loadLists() {
        val filterDir = application.applicationContext.getFilterDir()

        val allowFile = File(filterDir, ABP_PREFIX_ALLOW)
        val blockFile = File(filterDir, ABP_PREFIX_DENY)

        // for some reason reading allows first is faster then reading blocks first... but why?
       if (loadFile(allowFile, false) && loadFile(blockFile, true)) {
            listsLoaded = true
            return
        }
        // loading failed or joint lists don't exist: load the normal way and create joint lists

        val entities = AbpDao(application.applicationContext).getAll()
        val abpLoader = AbpLoader(filterDir, entities)

        // toSet() for removal of duplicate entries
        val allowFilters = abpLoader.loadAll(ABP_PREFIX_ALLOW).toSet()
        val blockFilters = abpLoader.loadAll(ABP_PREFIX_DENY).toSet()

        blockList = FilterContainer().also { blockFilters.forEach(it::addWithTag) }
        allowList = FilterContainer().also { allowFilters.forEach(it::addWithTag) }
        listsLoaded = true

        // create joint file
        writeFile(blockFile, blockFilters.sanitize().map {it.second})
        writeFile(allowFile, allowFilters.sanitize().map {it.second})

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
            else -> it
        } }
    }

    private fun loadFile(file: File, blocks: Boolean): Boolean {
        if (file.exists()) {
            try {
                file.inputStream().buffered().use { ins ->
                    val reader = FilterReader(ins)
                    if (reader.checkHeader()) {
                        if (blocks)
                            blockList =
                                FilterContainer().also { reader.readAll().forEach(it::addWithTag) }
                        else
                            allowList =
                                FilterContainer().also { reader.readAll().forEach(it::addWithTag) }
                        // check 2nd "header" at end of the file, to avoid accepting partially written file
                        return reader.checkHeader()
                    }
                }
            } catch(e: IOException) {}
        }
        return false
    }

    private fun writeFile(file: File, filters: Collection<UnifiedFilter>) {
        val writer = FilterWriter()
        file.outputStream().buffered().use {
            writer.write(it, filters.toList())
            it.close()
        }
    }

    // from yuzu: jp.hazuki.yuzubrowser.adblock/AdBlockController.kt
    fun createDummy(uri: Uri): WebResourceResponse {
        val mimeType = getMimeType(uri.toString())
        return if (mimeType.startsWith("image/")) {
            WebResourceResponse("image/png", null, ByteArrayInputStream(dummyImage))
        } else {
            dummyResponse
        }
    }

    // from yuzu: jp.hazuki.yuzubrowser.adblock/AdBlockController.kt
    // stings adjusted for Fulguris
    fun createMainFrameDummy(uri: Uri, pattern: String): WebResourceResponse {
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
    fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(url, pageUri))

    // moved from jp.hazuki.yuzubrowser.adblock/AdBlock.kt
    // modified to use cache for the slow part, decreases average time by 50-70%
    fun is3rdParty(url: Uri, pageUri: Uri): Boolean {
        val hostName = url.host ?: return true
        val pageHost = pageUri.host ?: return true

        if (hostName == pageHost) return false

        val cacheEntry = hostName + pageHost
        val cached = thirdPartyCache[cacheEntry]
        if (cached != null)
            return cached

        val ipPattern = PatternsCompat.IP_ADDRESS
        if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches())
            return cache3rdPartyResult(true, cacheEntry)

        val db = PublicSuffix.get()

        return cache3rdPartyResult(db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost), cacheEntry)
    }

    // cache 3rd party check result, and remove oldest entry if cache too large
    // TODO: this can trigger concurrentModificationException
    //   fix should not defeat purpose of cache (introduce slowdown)
    //   simply use try and don't catch anything?
    //    if something is not added to cache it doesn't matter
    //    in worst case it takes another 1-2 ms to create the same result again
    private fun cache3rdPartyResult(is3rdParty: Boolean, cacheEntry: String): Boolean {
        runCatching {
            thirdPartyCache[cacheEntry] = is3rdParty
            if (thirdPartyCache.size > thirdPartyCacheSize)
                thirdPartyCache.remove(thirdPartyCache.keys.first())
        }
        return is3rdParty
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

        importantBlockList[contentRequest]?.let { return getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_malware, it.pattern)) }
        allowList[contentRequest]?.let { return null }
        blockList[contentRequest]?.let {
            //if (it.pattern.isNotBlank()) {
                return getBlockResponse(
                    request,
                    application.resources.getString(R.string.page_blocked_list_ad, it.pattern)
                )
            //}
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

    companion object {
        const val BUFFER_SIZE = 1024 * 8
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
        const val MIME_TYPE_UNKNOWN = "application/octet-stream"
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
        fun getNoCacheResponse(mimeType: String, stream: InputStream): WebResourceResponse {
            val response = WebResourceResponse(mimeType, "UTF-8", stream)
            response.responseHeaders =
                HashMap<String, String>().apply { put("Cache-Control", "no-cache") }
            return response
        }
    }
}
