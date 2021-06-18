package acr.browser.lightning.adblock

import acr.browser.lightning.R
import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.EmptyInputStream
import jp.hazuki.yuzubrowser.adblock.core.*
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementContainer
import jp.hazuki.yuzubrowser.adblock.filter.unified.getFilterDir
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
    //private val userPreferences: UserPreferences // TODO: relevant only for element hiding
    ) : AdBlocker {
    // necessary to do it like this; userPreferences not injected when using AbpListUpdater(application.applicationContext).updateAll(false)
    //@Inject internal lateinit var abpListUpdater: AbpListUpdater
    // no... not initialized -> bah

    // filter lists
//    private var exclusionList: FilterContainer? = null
    private var exclusionList = FilterContainer()
//    private var blockList: FilterContainer? = null
    private var blockList = FilterContainer()
    private var userWhitelist: FilterContainer? = null
    private var userBlockList: FilterContainer? = null

    // if i want mining/malware block, it should be separate lists so they don't get disabled when not blocking ads
    private var miningList: FilterContainer? = null // copy from yuzu?
    private var malwareList: FilterContainer? = null // copy from smartcookieweb/styx?

/*    // TODO: element hiding
    //  not sure if this actually works (did not in a test - I think?), maybe it's crucial to inject the js at the right point
    //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
//    private var elementHideExclusionList: FilterContainer? = null
//    private var elementHideList: ElementContainer? = null
    // both lists are actually inside the elementBlocker
    private var elementBlocker: CosmeticFiltering? = null
    var useElementHide = true // TODO: load from preferences (if it really works)
*/
    // cache for 3rd party check, allows significantly faster checks
    private val thirdPartyCache = mutableMapOf<String, Boolean>()
    private val thirdPartyCacheSize = 100

    private val dummyImage: ByteArray = readByte(application.applicationContext.resources.assets.open("blank.png"))
    private val dummyResponse = WebResourceResponse("text/plain", "UTF-8", EmptyInputStream())

    override fun isAd(url: String) = false // for now...

    // TODO: decide when to load lists
    //  just loadLists() takes ca 1 second on S4 mini for easylist and delays browser start
    //   but then blocklists are loaded before the first request
    //  loading after updates are done may delay loading of adblocker for some 5-30 seconds, depending on phone speed and internet connection
    //  loading before fetching updates still leaves the first 3-5 seconds without blocker...
    //  nothing is really good here -> best way would be ton find a way to accelerate blocklist loading
    //   2nd best: have a setting
    //  hmm... there was this blocker.await in yuzu, maybe that's connected? but this made blocking 50% slower...
    init {
        loadLists() // takes ca 1 second on S4 mini for easylist

            // TODO: use of globalscope was discouraged somewhere... check and adjust if necessary
            GlobalScope.launch(Dispatchers.IO) {
                //loadLists() // takes about 3-5 seconds on S4 mini for easylist
                // updates all entities in AbpDao, may take a while depending on how many lists need update, and internet connection
                if (abpListUpdater.updateAll(false)) // returns true if any was updated
                    loadLists() // update again if files have changed
            }
    }

//----------------------- from yuzu adblocker (mostly AdBlockController) --------------------//
    fun loadLists() {
        val abpLoader = AbpLoader(application.applicationContext.getFilterDir(), AbpDao(application.applicationContext).getAll())
        blockList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::plusAssign) }
//        blockList.also { abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::plusAssign) } // TODO: if i do it like this, there is even more reason to remove duplicate entries!
        exclusionList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::plusAssign) }
//        exclusionList.also { abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::plusAssign) }

/*        if (useElementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }*/
    }

    fun createDummy(uri: Uri): WebResourceResponse {
        val mimeType = getMimeType(uri.toString())
        return if (mimeType.startsWith("image/")) {
            WebResourceResponse("image/png", null, ByteArrayInputStream(dummyImage))
        } else {
            dummyResponse
        }
    }

    fun createMainFrameDummy(uri: Uri, pattern: String): WebResourceResponse {
        val builder = StringBuilder("<meta charset=utf-8>" +
                "<meta content=\"width=device-width,initial-scale=1,minimum-scale=1\"name=viewport>" +
                "<style>body{padding:5px 15px;background:#fafafa}body,p{text-align:center}p{margin:20px 0 0}" +
                "pre{margin:5px 0;padding:5px;background:#ddd}</style><title>")
            .append(application.applicationContext.getText(R.string.request_blocked))
            .append("</title><p>")
            .append(application.applicationContext.getText(R.string.page_blocked))
            .append("<pre>")
            .append(uri)
            .append("</pre><p>")
            .append(application.applicationContext.getText(R.string.page_blocked_reason))
            .append("<pre>")
            .append(pattern)
            .append("</pre>")

        return getNoCacheResponse("text/html", builder)
    }

    override fun loadScript(uri: Uri): String? {
//        val cosmetic = elementBlocker ?: return null
//        return cosmetic.loadScript(uri)
        return null // TODO: remove if element hiding does not work
    }

    // copied here to allow modified 3rd party detection
    fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(this.url, pageUri))

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

 //----------------------- not from yuzu any more ------------------------//

    // cache 3rd party check result
    private fun cache3rdPartyResult(is3rdParty: Boolean, cacheEntry: String): Boolean {
        thirdPartyCache[cacheEntry] = is3rdParty
        if (thirdPartyCache.size > thirdPartyCacheSize)
            thirdPartyCache.remove(thirdPartyCache.keys.first())
        return is3rdParty
    }

    // return null if not blocked, else some WebResourceResponse
    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // pageUrl might be "" (usually when opening something in a new tab)
        //  in this case everything gets blocked because of the pattern "|https://"
        //  this is blocked for some domains, and apparently no domain also means it's blocked
        //  so some workaround here (maybe do something else?)
        val contentRequest = request.getContentRequest(if (pageUrl == "") request.url else Uri.parse(pageUrl))
        userBlockList?.get(contentRequest)?.pattern
        // check user lists
        // then mining/malware
        // then ads

        /* replace by rather ugly if-stuff, because result of check is used
        return when {
            userWhitelist?.get(contentRequest) != null -> null
            userBlockList?.get(contentRequest) != null -> getBlockResponse(request)
            miningList?.get(contentRequest) != null -> getBlockResponse(request)
            malwareList?.get(contentRequest) != null -> getBlockResponse(request)
            exclusionList?.get(contentRequest) != null -> null
            blockList?.get(contentRequest) != null -> getBlockResponse(request)
            else -> null
        }*/
        if (userWhitelist?.get(contentRequest) != null)
            return null

        var filter = userBlockList?.get(contentRequest)
        if (filter != null)
            return getBlockResponse(request, "User blocklist: ${filter.pattern}")

        filter = miningList?.get(contentRequest)
        if (filter != null)
            return getBlockResponse(request, "Mining blocklist: ${filter.pattern}")

        filter = malwareList?.get(contentRequest)
        if (filter != null)
            return getBlockResponse(request, "Malware blocklist: ${filter.pattern}")

        if (exclusionList?.get(contentRequest) != null)
            return null

        filter = blockList?.get(contentRequest)
        if (filter != null)
            return getBlockResponse(request, "Ad blocklist: ${filter.pattern}")

        return null
    }

    private fun getBlockResponse(request: WebResourceRequest, pattern: String): WebResourceResponse {
        return if (request.isForMainFrame)
            createMainFrameDummy(request.url, pattern)
        else
            createDummy(request.url)
    }

    // stuff from yuzu browser modules/core/.../utility
    companion object {
        const val BUFFER_SIZE = 1024 * 8

        @Throws(IOException::class)
        fun readByte(inpputStream: InputStream): ByteArray {
            val buffer = ByteArray(BUFFER_SIZE)
            val bout = ByteArrayOutputStream()
            var n: Int
            while (inpputStream.read(buffer).also { n = it } >= 0) {
                bout.write(buffer, 0, n)
            }
            return bout.toByteArray()
        }

        const val MIME_TYPE_UNKNOWN = "application/octet-stream"
        fun getMimeType(fileName: String): String {
            val lastDot = fileName.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = fileName.substring(lastDot + 1).toLowerCase()
                return getMimeTypeFromExtension(extension)
            }
            return "application/octet-stream"
        }

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

        fun getNoCacheResponse(mimeType: String, sequence: CharSequence): WebResourceResponse {
            return getNoCacheResponse(
                mimeType, ByteArrayInputStream(
                    sequence.toString().toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            )
        }

        fun getNoCacheResponse(mimeType: String, stream: InputStream): WebResourceResponse {
            val response = WebResourceResponse(mimeType, "UTF-8", stream)
            response.responseHeaders =
                HashMap<String, String>().apply { put("Cache-Control", "no-cache") }
            return response
        }
    }
}
