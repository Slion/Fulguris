package acr.browser.lightning.adblock

import acr.browser.lightning.adblock.AbpBlocker.Companion.getModifiedParameters
import acr.browser.lightning.adblock.AbpBlocker.Companion.getQueryParameterMap
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.abp.AbpFilterDecoder
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilterSet
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Unit tests for [NoOpAdBlocker].
 */
class NoOpAdBlockerTest {

    @Test
    fun `isAd no-ops`() {
        val noOpAdBlocker = NoOpAdBlocker()
        val request = TestWebResourceRequest(Uri.parse("https://ads.google.com"), false, mapOf())

        assertThat(noOpAdBlocker.shouldBlock(request, "https://google.com")).isNull()
    }

    // below are tests for adblocker, but when i put them in AbpBlockerTest there is some build error regarding "sponsorship"
    var set: UnifiedFilterSet
    val set2: UnifiedFilterSet

    init {
        val ASSET_BASE_PATH = "../assets/"
        val easylist = File(ASSET_BASE_PATH + "easylist-minified.txt")
        val modifyFilters = File(ASSET_BASE_PATH + "adguard-urltracking.txt")
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

    @Test
    fun writeAndReadWorks() {
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
        println(set2.modifyList[140].pattern)
        println(set2.modifyList[140].modify?.parameter)

        Assert.assertEquals(startList, endList.map { it.second })
    }

    @Test
    fun filterContainerWorks() {
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

        filterList.add("||page2.com^\$1p")
        blockedRequests.add(request("http://page2.com/something", "https://something.page2.com").second)
        allowedRequests.add(request("http://page2.com/something", "https://badpage.com").second)
        filterList.add("||page3.com^\$3p")
        allowedRequests.add(request("http://page3.com/something", "https://something.page3.com").second)
        blockedRequests.add(request("http://page3.com/something", "https://badpage.com").second)
        filterList.add("/badthing/*")
        blockedRequests.add(request("http://something.com/badthing/ad", "https://page.com").second)
        allowedRequests.add(request("http://something.com/badthing", "https://page.com").second)
        allowedRequests.add(request("http://something.com/badthingies", "https://page.com").second)
        filterList.add("||page5.*/something")
        blockedRequests.add(request("http://page5.co.uk/something/page", "http://page.com").second)
        blockedRequests.add(request("http://page5.com/something?test=yes", "http://page.com").second)

        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())

        // filters should be put in correct list
        Assert.assertEquals(set.blackList.size, filterList.size)
        Assert.assertTrue(set.whiteList.isEmpty())
        Assert.assertTrue(set.modifyList.isEmpty())
        Assert.assertTrue(set.modifyExceptionList.isEmpty())

        val container = FilterContainer().also { set.blackList.forEach(it::plusAssign) }

        blockedRequests.forEach {
            Assert.assertNotNull(container[it])
        }
        allowedRequests.forEach {
            Assert.assertNull(container[it])
        }
    }

    @Test
    fun allowList() {
        val filterList = mutableListOf<String>()
        val allowlistedRequests = mutableListOf<ContentRequest>()
        val otherRequests = mutableListOf<ContentRequest>()
        filterList.add("@@||page.com/ads\$~third-party")
        allowlistedRequests.add(request("http://page.com/ads", "http://page.com").second)
        otherRequests.add(request("http://page.com/ads", "http://thing.com").second)

        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())

        // filters should be put in correct list
        Assert.assertEquals(set.whiteList.size, filterList.size)
        Assert.assertTrue(set.blackList.isEmpty())
        Assert.assertTrue(set.modifyList.isEmpty())
        Assert.assertTrue(set.modifyExceptionList.isEmpty())

        val container = FilterContainer().also { set.whiteList.forEach(it::plusAssign) }

        allowlistedRequests.forEach {
            Assert.assertNotNull(container[it])
        }
        otherRequests.forEach {
            Assert.assertNull(container[it])
        }
    }

    // TODO: this is not yet included I think...
    @Test
    fun importantList() {
    }

    @Test
    fun modifyList() {
        // only tests removeparam so far
        // redirect not implemented
        // csp modifies response -> how to test?
        val filterList = mutableListOf<String>()
        val modifiedRequests = mutableListOf<Pair<WebResourceRequest,ContentRequest>>()
        val allowedRequests = mutableListOf<Pair<WebResourceRequest,ContentRequest>>()
        filterList.add("\$removeparam=badparam")
        modifiedRequests.add(request("http://page.com/page?badparam=yes", "http://page.com"))
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

        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())

        // filters should be put in correct list
        Assert.assertEquals(set.modifyList.size, filterList.size)
        Assert.assertTrue(set.blackList.isEmpty())
        Assert.assertTrue(set.whiteList.isEmpty())
        Assert.assertTrue(set.modifyExceptionList.isEmpty())

        val container = FilterContainer().also { set.modifyList.forEach(it::plusAssign) }

        modifiedRequests.forEach {
            Assert.assertNotNull(getModifiedParameters(it.first, container.getAll(it.second)))
        }
        allowedRequests.forEach {
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
