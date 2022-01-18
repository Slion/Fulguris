package acr.browser.lightning.adblock

import acr.browser.lightning.adblock.AbpBlocker.Companion.getModifiedParameters
import acr.browser.lightning.adblock.AbpBlocker.Companion.getQueryParameterMap
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.abp.AbpFilterDecoder
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import okhttp3.internal.publicsuffix.PublicSuffix
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class AbpBlockerTest {

    private var set: UnifiedFilterSet
    private val set2: UnifiedFilterSet

    init {
        val baseAssets = "../assets/"
        val easylist = File(baseAssets + "easylist-minified.txt")
        val modifyFilters = File(baseAssets + "adguard-urltracking.txt")
        set = loadFilterSet(easylist.inputStream())
        set2 = loadFilterSet(modifyFilters.inputStream())
    }

    private fun loadFilterSet(abpStyleList: InputStream): UnifiedFilterSet {
        abpStyleList.bufferedReader().use {
            return AbpFilterDecoder().decode(it, null)
        }
    }

    private fun OutputStream.writeFilterList(list: List<UnifiedFilter>) {
        use {
            val writer = FilterWriter()
            writer.write(it, list)
        }
    }

    private fun readFiltersFile(filtersFile: InputStream): List<Pair<String, UnifiedFilter>> {
        val filters = mutableListOf<Pair<String, UnifiedFilter>>()
        filtersFile.use {
            val reader = FilterReader(it)
            if (reader.checkHeader())
                filters.addAll(reader.readAll())
        }
        return filters
    }

    private fun FilterContainer.getFilterList(): List<UnifiedFilter> {
        val filterList = mutableListOf<UnifiedFilter>()
        filters().values.forEach {
            var f: ContentFilter? = it
            while (f?.next != null) {
                filterList.add(f as UnifiedFilter)
                f = f.next
            }
            filterList.add(f as UnifiedFilter)
        }
        return filterList
    }

    private fun request(url: String, pageUrl: String, mainFrame: Boolean = false, headers: Map<String, String> = mapOf()): Pair<WebResourceRequest,ContentRequest> {
        return TestWebResourceRequest(url.toUri(), mainFrame, headers).let { it to it.getContentRequest(pageUrl.toUri()) }
    }

    private fun checkFilters(filterList: List<String>, blockedRequests: List<ContentRequest>, allowedRequests: List<ContentRequest>, list: String) {
        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())
        val container = FilterContainer()

        when (list) {
            "block" -> {
                Assert.assertEquals(filterList.size, set.blackList.size)
                container.also { set.blackList.forEach(it::plusAssign) }
            }
            "allow" -> {
                Assert.assertEquals(filterList.size, set.whiteList.size)
                container.also { set.whiteList.forEach(it::plusAssign) }
            }
            "modify" -> {
                Assert.assertEquals(filterList.size, set.modifyList.size)
                container.also { set.modifyList.forEach(it::plusAssign) }
            }
        }

        blockedRequests.forEach {
            println("should be filtered: " + it.url + " "+ it.pageUrl)
            Assert.assertNotNull(container[it])
        }
        allowedRequests.forEach {
            println("should not be touched: " + it.url + " "+ it.pageUrl)
            Assert.assertNull(container[it])
        }
    }

    @Test
    fun writeRead() {
        val startList = set.blackList
        val filterStore = ByteArrayOutputStream()
        filterStore.writeFilterList(startList)
        val endList = readFiltersFile(filterStore.toByteArray().inputStream())

        // endList also contains tag, we want to compare only the filter
        Assert.assertEquals(startList, endList.map { it.second })
    }

    @Test
    fun modifyFiltersReadWrite() {
        val startList = set2.modifyList
        val filterStore = ByteArrayOutputStream()
        filterStore.use {
            val writer = FilterWriter()
            writer.writeModifyFilters(it, startList)
        }

        val endList = mutableListOf<Pair<String, UnifiedFilter>>()
        filterStore.toByteArray().inputStream().use { stream ->
            val reader = FilterReader(stream)
            if (reader.checkHeader())
                endList.addAll(reader.readAllModifyFilters())
        }

        Assert.assertEquals(startList, endList.map { it.second })
    }

    @Test
    fun filterContainer() {
        val container = FilterContainer().also { set.blackList.forEach(it::plusAssign) }
        val filterStore = ByteArrayOutputStream()
        filterStore.writeFilterList(set.blackList)
        val container2 = FilterContainer().also { readFiltersFile(filterStore.toByteArray().inputStream()).forEach(it::addWithTag) }
        val filterList = container.getFilterList()
        val filterList2 = container2.getFilterList()

        // filter lists from containers should be equal
        Assert.assertTrue(filterList.containsAll(filterList2) && filterList2.containsAll(filterList))

        // if inserting into filter container works, set.blacklist and filterList contain the same filters (but possibly in different order)
        Assert.assertTrue(filterList.containsAll(set.blackList) && set.blackList.containsAll(filterList))
    }

    @Test
    fun blockList() {
        // TODO: go through ubo/abp things and include examples for everything
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()
        filterList.add("||ad.adpage.com/ads/")
        blockedRequests.add(request("http://ad.adpage.com/ads/badad", "https://example.com").second)
        filterList.add("/badthing/*")
        blockedRequests.add(request("http://something.com/badthing/ad", "https://page.com").second)
        allowedRequests.add(request("http://something.com/badthing", "https://page.com").second)
        allowedRequests.add(request("http://something.com/badthingies", "https://page.com").second)
        filterList.add("||page5.*/something")
        blockedRequests.add(request("http://page5.co.uk/something/page", "http://page.com").second)
        blockedRequests.add(request("http://page5.com/something?test=yes", "http://page.com").second)
        filterList.add("/badfolder/worsething/")
        blockedRequests.add(request("http://page6.co.uk/badfolder/worsething/", "http://page.com").second)
        blockedRequests.add(request("http://page6.com/badfolder/worsething/something?test=yes", "http://page.com").second)
        allowedRequests.add(request("http://page6.com/badfolder/worsething_/something?test=yes", "http://page.com").second)

        checkFilters(filterList, blockedRequests, allowedRequests, "block")
    }

    @Test
    fun regex() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("/banner\\d+/")
        blockedRequests.add(request("http://page2.com/banner1", "https://something.page2.com").second)
        blockedRequests.add(request("http://page2.com/banner123", "https://something.page2.com").second)
        allowedRequests.add(request("http://page2.com/banner", "https://badpage.com").second)
        allowedRequests.add(request("http://page2.com/banner/", "https://badpage.com").second)

        checkFilters(filterList, blockedRequests, allowedRequests, "block")
    }

    @Test
    fun thirdParty() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||page2.com^\$1p")
        blockedRequests.add(request("http://page2.com/something", "https://something.page2.com").second)
        allowedRequests.add(request("http://page2.com/something", "https://badpage.com").second)
        filterList.add("||page3.com^\$3p")
        allowedRequests.add(request("http://page3.com/something", "https://something.page3.com").second)
        blockedRequests.add(request("http://page3.com/something", "https://badpage.com").second)

        checkFilters(filterList, blockedRequests, allowedRequests, "block")
    }

    @Test
    fun strictThirdParty() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||page6.com^\$strict1p")
        blockedRequests.add(request("http://page6.com/something", "https://page6.com").second)
        allowedRequests.add(request("http://page6.com/something", "https://other.com").second)
        allowedRequests.add(request("http://page6.com/something", "https://something.page6.com").second)
        filterList.add("||page7.com^\$strict3p")
        allowedRequests.add(request("http://page7.com/something", "https://page7.com").second)
        blockedRequests.add(request("http://page7.com/something", "https://other.com").second)
        blockedRequests.add(request("http://page7.com/something", "https://something.page7.com").second)

        checkFilters(filterList, blockedRequests, allowedRequests, "block")
    }

    @Test
    fun domains() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||ad.adpage2.com^\$domain=~okpage.com")
        blockedRequests.add(request("http://ad.adpage2.com/ads/badad", "https://example.com").second)
        allowedRequests.add(request("http://ad.adpage2.com/ads/badad", "https://okpage.com").second)
        allowedRequests.add(request("http://ad.adpage2.com/ads/badad", "https://test.okpage.com").second)
        filterList.add("||page.com^\$domain=badpage.com")
        allowedRequests.add(request("http://page.com/something", "https://okpage.com").second)
        blockedRequests.add(request("http://page.com/something", "https://badpage.com").second)
        filterList.add("://ads.\$domain=~goodotherpage.com|~good.page4.com")
        blockedRequests.add(request("http://ads.page4.com/something", "https://something.page4.com").second)
        allowedRequests.add(request("http://ads.page4.com/something", "https://good.page4.com").second)
        allowedRequests.add(request("http://ads.page4.com/something", "https://goodotherpage.com").second)

        checkFilters(filterList, blockedRequests, allowedRequests, "block")
    }

    @Test
    fun allUrls() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("*\$third-party")
        filterList.add("\$third-party")
        allowedRequests.add(request("http://ads.page4.com/something", "https://something.page4.com").second)
        blockedRequests.add(request("http://ads.page4.com/something", "https://goodotherpage.com").second)

        val s = loadFilterSet(filterList.joinToString("\n").byteInputStream())
        Assert.assertEquals(s.blackList.first(), s.blackList.last()) // '*' and '' should give the same filter

        checkFilters(filterList, blockedRequests, allowedRequests, "block")
    }

    @Test
    fun all() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        // not really a good test, modify it... and understand what "all" is actually supposed to do!
        //  the csp parts imply it's a modify filter, but the initial discussions imply it should be blocking
        filterList.add("||example.com^\$all")
        allowedRequests.add(request("http://ads.example2.com/something", "https://something.page4.com").second)
        blockedRequests.add(request("http://example.com/something", "https://goodotherpage.com").second)

        checkFilters(filterList, blockedRequests, allowedRequests, "modify")
    }

    @Test
    fun allowList() {
        val filterList = mutableListOf<String>()
        val allowlistedRequests = mutableListOf<ContentRequest>()
        val otherRequests = mutableListOf<ContentRequest>()
        filterList.add("@@||page.com/ads\$~third-party")
        allowlistedRequests.add(request("http://page.com/ads", "http://page.com").second)
        otherRequests.add(request("http://page.com/ads", "http://thing.com").second)

        checkFilters(filterList, allowlistedRequests, otherRequests, "allow")
    }

    @Test
    fun modifyList() {
        // only tests removeparam so far
        val filterList = mutableListOf<String>()
        val modifiedRequests = mutableListOf<Pair<WebResourceRequest,ContentRequest>>()
        val allowedRequests = mutableListOf<Pair<WebResourceRequest,ContentRequest>>()
        filterList.add("\$removeparam=badparam")
        modifiedRequests.add(request("http://page.com/page?badparam=yes", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?badparam", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?badparam=", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?badparam=yes&other=no", "http://page.com"))
        allowedRequests.add(request("http://page.com/whatever", "http://thing.com"))
        allowedRequests.add(request("http://page.com/ads?param=yes", "http://thing.com"))
        filterList.add("||www.page.\$removeparam=badparam2")
        modifiedRequests.add(request("http://www.page.com/page?badparam2=yes", "http://page.com"))
        modifiedRequests.add(request("http://www.page.org/page?badparam2=yes", "http://page.com"))
        modifiedRequests.add(request("http://www.page.co.uk/page?badparam2=yes", "http://page.com"))
        filterList.add("||page56.com\$removeparam")
        modifiedRequests.add(request("http://page56.com/page?param2=yes", "http://page.com"))
        allowedRequests.add(request("http://page56.com/page_param2=yes", "http://page.com"))
        filterList.add("\$removeparam=/^par_/")
        allowedRequests.add(request("http://page2.com/page?param2=yes", "http://page.com"))
        allowedRequests.add(request("http://page2.com/page?1par_am2=yes", "http://page.com"))
        modifiedRequests.add(request("http://page2.com/page?par_am2=yes", "http://page.com"))
        modifiedRequests.add(request("http://page2.com/page?par_", "http://page.com"))

        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())

        // filters should be put in correct list
        Assert.assertEquals(set.modifyList.size, filterList.size)
        Assert.assertTrue(set.blackList.isEmpty())
        Assert.assertTrue(set.whiteList.isEmpty())
        Assert.assertTrue(set.modifyExceptionList.isEmpty())

        val container = FilterContainer().also { set.modifyList.forEach(it::plusAssign) }

        modifiedRequests.forEach {
            println("should be filtered: " + it.second.url + " "+ it.second.pageUrl)
            Assert.assertNotNull(getModifiedParameters(it.first, container.getAll(it.second)))
        }
        allowedRequests.forEach {
            println("should not be touched: " + it.second.url + " "+ it.second.pageUrl)
            Assert.assertNull(getModifiedParameters(it.first, container.getAll(it.second)))
        }
    }

    @Test
    fun modifyExceptionList() {
    }

    @Test
    fun getQueryParameterMap() {
        // add some filter and make sure (only) the correct parameters are removed
        val url = Uri.parse("http://ads.test.net/ads?a=1&b=4#bla")
        val parameters = mapOf( "a" to "1", "b" to "4")
        Assert.assertEquals(parameters, url.getQueryParameterMap())
    }
}

class TestWebResourceRequest(val url2: Uri,
                             val isForMainFrame2: Boolean,
                             val requestHeaders2: Map<String, String>
                             ): WebResourceRequest {
    override fun getUrl() = url2
    override fun isForMainFrame() = isForMainFrame2
    override fun getRequestHeaders() = requestHeaders2
    override fun isRedirect() = false // not needed
    override fun hasGesture() = false // not needed
    override fun getMethod() = "" // not needed
}

fun WebResourceRequest.getContentRequest(pageUri: Uri) =
//    ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(url, pageUri))
    ContentRequest(url, pageUri, 0xffff, is3rdParty(url, pageUri)) // TODO: avoids mimeTypeMap not mocked problems, but type is wrong

fun is3rdParty(url: Uri, pageUri: Uri): Boolean {
    val hostName = url.host ?: return true
    val pageHost = pageUri.host ?: return true

    if (hostName == pageHost) return false

    val ipPattern = PatternsCompat.IP_ADDRESS
    if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches())
        return true

    val db = PublicSuffix.get()

    return db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost)
}
