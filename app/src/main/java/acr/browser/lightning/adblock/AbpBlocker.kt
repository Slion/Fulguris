package acr.browser.lightning.adblock

import acr.browser.lightning.R
import acr.browser.lightning.adblock.allowlist.SessionAllowListModel
import acr.browser.lightning.database.adblock.UserRulesRepository
import acr.browser.lightning.utils.isSpecialUrl
import android.app.Application
import android.net.Uri
import android.os.AsyncTask
import android.os.SystemClock
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.EmptyInputStream
import jp.hazuki.yuzubrowser.adblock.core.*
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
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
    private val whitelistModel: SessionAllowListModel
    ) : AdBlocker {

    private lateinit var exclusionList: FilterContainer
    private lateinit var blockList: FilterContainer

    private lateinit var abpUserRules: AbpUserRules // TODO: how to get? probably injecting would be best

    // if i want mining/malware block, it should be separate lists so they are not affected by ad-blocklist exclusions
    // TODO: any reason NOT to join those lists?
    private var miningList = FilterContainer() // copy from yuzu?
    private var malwareList = FilterContainer() // copy from smartcookieweb/styx?

    // store whether lists are loaded (and delay any request if loading is not finished)
    private var listsLoaded = false

/*    // TODO: element hiding
    //  not sure if this actually works (did not in a test - I think?), maybe it's crucial to inject the js at the right point
    //  tried onPageFinished, might be too late (try to implement onDomFinished from yuzu?)
//    private var elementHideExclusionList: FilterContainer? = null
//    private var elementHideList: ElementContainer? = null
    // both lists are actually inside the elementBlocker
    private var elementBlocker: CosmeticFiltering? = null
    var elementHide = userPreferences.elementHide
*/
    // cache for 3rd party check, allows significantly faster checks
    private val thirdPartyCache = mutableMapOf<String, Boolean>()
    private val thirdPartyCacheSize = 100

    private val dummyImage: ByteArray by lazy { readByte(application.applicationContext.resources.assets.open("blank.png")) }
    private val dummyResponse by lazy { WebResourceResponse("text/plain", "UTF-8", EmptyInputStream()) }

    override fun isAd(url: String) = false // for now...

    init {
        // TODO: ideally we should call loadLists here (blocking) if the url in current tab (on opening browser) is not special
        //  because loadLists() here is sometimes significantly faster than inside the GlobalScope
        //  but generally no need to block UI, better just delay the first web requests
        //loadLists() // 400-450 ms on S4 mini plus / 550-650 ms on S4 mini -> fastest, but blocking

        GlobalScope.launch(Dispatchers.Default) { // IO for io-intensive stuff, but here we do some IO and are mostly limited by CPU... so Default should be better?
            // load lists here if not loaded above
            stufftest()
            //abpUserRules.loadUserLists() // TODO: just load it, and user lists are loaded on init?
            loadLists() // 320-430 ms on S4 mini plus / 1200-1700 ms on S4 mini -> good on plus
            //loadListsAsync() // 540-720 ms on S4 mini plus / 900-1200 ms on S4 mini -> best non-blocking on normal
            // why is async so much faster on a dual core phone? expectation is other way round

            // update all entities in AbpDao
            // may take a while depending on how many lists need update, and on internet connection
            if (abpListUpdater.updateAll(false)) // returns true if anything was updated
                loadLists() // update again if files have changed
        }
    }

    fun stufftest() {
        val filterList = mutableListOf<ContentFilter>()
        // global, blocking all requests to example.com
        filterList.add(ContainsFilter("", 0xffff, SingleDomainMap(true, "example.com"), -1))
        // global, blocking 3rd party requests to example.com
        filterList.add(ContainsFilter("", 0xffff, SingleDomainMap(true, "example.com"), 1))
        // global, blocking 1st party requests to example.com
        filterList.add(ContainsFilter("", 0xffff, SingleDomainMap(true, "example.com"), 0))
        // on example.net, blocking all requests to example.com
        filterList.add(StartEndFilter("example.net", 0xffff, false, SingleDomainMap(true, "example.com"), -1))
        // global, blocking all 3rd party requests
        filterList.add(StartEndFilter("", 0xffff, false, null, 1))

        // test requests (actually tags don't matter here)
        val requestList = mutableListOf<ContentRequest>()
        // we are on example.net and want stuff from example.com
        requestList.add(ContentRequest(Uri.parse("http://example.net"), Uri.parse("http://example.com"), ContentRequest.TYPE_DOCUMENT, true, listOf("example.net")))
        // we are on example.net and want stuff from example.com (sub)
        requestList.add(ContentRequest(Uri.parse("http://example.net"), Uri.parse("http://example.com/bla.html"), ContentRequest.TYPE_DOCUMENT, true, listOf("example.net")))
        // we are on example.net (sub) and want stuff from example.com
        requestList.add(ContentRequest(Uri.parse("http://example.net/meep.htm"), Uri.parse("http://example.com"), ContentRequest.TYPE_DOCUMENT, true, listOf("example.net")))
        // we are on example.net (maybe bad sub) and want stuff from example.com
        requestList.add(ContentRequest(Uri.parse("http://example.net/example.com/test"), Uri.parse("http://example.com"), ContentRequest.TYPE_DOCUMENT, true, listOf("example.net")))
        // we are on example.net and want stuff from example.com (maybe bad sub)
        requestList.add(ContentRequest(Uri.parse("http://example.net"), Uri.parse("http://example.com/example.net/bla.js"), ContentRequest.TYPE_DOCUMENT, true, listOf("example.net")))
        // we are on example.net and want stuff from test.example.com
        requestList.add(ContentRequest(Uri.parse("http://example.net"), Uri.parse("http://test.example.com"), ContentRequest.TYPE_DOCUMENT, true, listOf("example.net")))
        // we are on example.com and want stuff from example.com
        requestList.add(ContentRequest(Uri.parse("http://example.com"), Uri.parse("http://example.com"), ContentRequest.TYPE_DOCUMENT, false, listOf("example.com")))
        // we are on example.com and want stuff from test.example.com
        requestList.add(ContentRequest(Uri.parse("http://example.com"), Uri.parse("http://test.example.com"), ContentRequest.TYPE_DOCUMENT, false, listOf("example.com")))
        // we are on example.net and want stuff from example.net
        requestList.add(ContentRequest(Uri.parse("http://example.net"), Uri.parse("http://example.net"), ContentRequest.TYPE_DOCUMENT, false, listOf("example.net")))
        // we are on example.net and want stuff from test.example.net
        requestList.add(ContentRequest(Uri.parse("http://example.net"), Uri.parse("http://test.example.net"), ContentRequest.TYPE_DOCUMENT, false, listOf("example.net")))

        for (i in 0 until filterList.size) {
            for (j in 0 until requestList.size) {
                Log.i("yuzu", "list $i, request $j: ${filterList[i].isMatch(requestList[j])}")
            }
        }
    }

//----------------------- from yuzu adblocker (mostly AdBlockController) --------------------//

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

    override fun loadScript(uri: Uri): String? {
//        val cosmetic = elementBlocker ?: return null
//        return cosmetic.loadScript(uri)
        return null // TODO: remove if element hiding does not work
    }

    // copied here to allow modified 3rd party detection
    fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(url, pageUri))

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

    // TODO: probably remove
    fun loadListsAsync() {
        val time = SystemClock.elapsedRealtime()
        listsLoaded = false
        val abpLoader = AbpLoader(application.applicationContext.getFilterDir(), AbpDao(application.applicationContext).getAll())
        GlobalScope.launch {
            val el = async { FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::addWithTag) } }
            val bl = async { FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::addWithTag) } }
            Log.i("yuzu", "async stuff started after ${SystemClock.elapsedRealtime() - time} ms")
            exclusionList = el.await()
            blockList = bl.await()
            listsLoaded = true
            Log.i("yuzu", "loadListsAsync took ${SystemClock.elapsedRealtime() - time} ms")
        }
    }

    fun loadLists() {
        val time = SystemClock.elapsedRealtime()
        listsLoaded = false
        val abpLoader = AbpLoader(application.applicationContext.getFilterDir(), AbpDao(application.applicationContext).getAll())
//        exclusionList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::addWithTag) }
//        blockList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::addWithTag) }
        exclusionList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_ALLOW).forEach(it::plusAssign) }
        blockList = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DENY).forEach(it::plusAssign) }

        /*if (elementHide) {
            val disableCosmetic = FilterContainer().also { abpLoader.loadAll(ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach(it::plusAssign) }
            val elementFilter = ElementContainer().also { abpLoader.loadAllElementFilter().forEach(it::plusAssign) }
            elementBlocker = CosmeticFiltering(disableCosmetic, elementFilter)
        }*/

        listsLoaded = true
        Log.i("yuzu", "loadLists took ${SystemClock.elapsedRealtime() - time} ms")
    }

    // cache 3rd party check result
    private fun cache3rdPartyResult(is3rdParty: Boolean, cacheEntry: String): Boolean {
        thirdPartyCache[cacheEntry] = is3rdParty
        if (thirdPartyCache.size > thirdPartyCacheSize)
            thirdPartyCache.remove(thirdPartyCache.keys.first())
        return is3rdParty
    }

    // return null if not blocked, else some WebResourceResponse
    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        // always allow special urls
        // then check user lists (user allow should even override malware list)
        // then mining/malware (ad block allow should not override malware list)
        // then ads

        if (request.url.toString().isSpecialUrl())
            return null

        // create contentRequest
        // pageUrl can be "" (usually when opening something in a new tab)
        //  in this case everything gets blocked because of the pattern "|https://"
        //  this is blocked for some domains, and apparently no domain also means it's blocked
        //  so some workaround here (maybe do something else?)
        val contentRequest = request.getContentRequest(if (pageUrl == "") request.url else Uri.parse(pageUrl))

        // no need to supply pattern to block response
        //  pattern only used if it's for main frame
        //  and if it's for main frame and blocked by user, it's always because user chose to block entire domain
        abpUserRules.getResponse(contentRequest)?.let {
            return if (it) getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_user, contentRequest.pageUrl.host ?: ""))
             else null }

        // wait until blocklists loaded
        //  (web request stuff does not run on main thread, so thread.sleep is ok)
        // possible reduction of wait time before lists have loaded:
        //   load list with 'empty' tag first and check those
        //   for the rest the bloom filter could be used, so requests without matching tags are allowed immediately
        //   but: seems like a lot of work for maybe saving 0.5-2 seconds on browser start
        //    and 'maybe saving' because ca 20-50% of all requests will have matching tags, so there still will be a delay (but less noticeable)
        while (!listsLoaded) {
            Log.i("yuzu", "waiting for list loading")
            Thread.sleep(50)
        }

        var filter = miningList[contentRequest]
        if (filter != null)
            return getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_ad, filter.pattern))

        filter = malwareList[contentRequest]
        if (filter != null)
            return getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_ad, filter.pattern))

        if (exclusionList[contentRequest] != null)
            return null

        filter = blockList[contentRequest]
        if (filter != null)
            return getBlockResponse(request, application.resources.getString(R.string.page_blocked_list_ad, filter.pattern))

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
