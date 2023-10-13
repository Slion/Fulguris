package fulguris.adblock

import fulguris.adblock.AbpBlocker.Companion.getQueryParameterMap
import fulguris.adblock.AbpBlockerManager.Companion.blockerPrefixes
import fulguris.adblock.AbpBlockerManager.Companion.isModify
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import fulguris.adblock.AbpBlocker
import fulguris.adblock.BlockResourceResponse
import fulguris.adblock.BlockResponse
import fulguris.adblock.ModifyResponse
import fulguris.adblock.RES_1X1
import fulguris.adblock.RES_NOOP_MP4
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.getFilterType
import jp.hazuki.yuzubrowser.adblock.otherIfNone
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
    private val filterContainers = blockerPrefixes.associateWith { FilterContainer() }
    private val blocker = AbpBlocker(null, filterContainers)
    private val mimeTypeMap = loadMimeTypeMap()

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

    private fun loadFiltersIntoContainers(list: List<String>) {
        loadFiltersIntoContainers(list.joinToString("\n").byteInputStream())
    }

    private fun loadFiltersIntoContainers(abpStyleList: InputStream) {
        val set: UnifiedFilterSet
        abpStyleList.bufferedReader().use {
            set = AbpFilterDecoder().decode(it, null)
        }
        loadFiltersIntoContainers(set)
    }

    private fun loadFiltersIntoContainers(set: UnifiedFilterSet) {
        blockerPrefixes.forEach { prefix ->
            val filterStore = ByteArrayOutputStream()
            filterStore.writeFilterList(set.filters[prefix], isModify(prefix))
            val sameListWithTag = readFiltersFile(filterStore.toByteArray().inputStream(), isModify(prefix))

            // assert writing and reading the filters worked
            Assert.assertEquals(set.filters[prefix], sameListWithTag.unzip().second)

            // load filters into container
            sameListWithTag.forEach(filterContainers[prefix]!!::addWithTag)

            // assert filters are put into containers correctly
            Assert.assertEquals(set.filters[prefix].sortedBy { it.hashCode() }, filterContainers[prefix]!!.getFilterList().sortedBy { it.hashCode() })
        }
    }

    private fun Map<String, FilterContainer>.clear() = forEach { it.value.clear() }

    private fun OutputStream.writeFilterList(list: List<UnifiedFilter>, isModify: Boolean) {
        use {
            val writer = FilterWriter()
            if (isModify) writer.writeModifyFilters(it, list)
            else writer.write(it, list)
        }
    }

    private fun readFiltersFile(filtersFile: InputStream, isModify: Boolean): List<Pair<String, UnifiedFilter>> {
        val filters = mutableListOf<Pair<String, UnifiedFilter>>()
        filtersFile.use {
            val reader = FilterReader(it)
            if (reader.checkHeader()) {
                if (isModify) filters.addAll(reader.readAllModifyFilters())
                else filters.addAll(reader.readAll())
            }
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

    private fun request(
        url: String,
        pageUrl: String,
        mainFrame: Boolean = false,
        headers: Map<String, String> = mapOf()
    ): ContentRequest {
        return TestWebResourceRequest(
            url.toUri(),
            mainFrame,
            headers
        ).getContentRequest(pageUrl.toUri())
    }

    private fun checkFiltersWithBlocker(
        filterList: List<String>,
        blockedRequests: List<ContentRequest>?,
        allowedRequests: List<ContentRequest>?,
        modifiedRequests: List<ContentRequest>?
    ) {
        filterContainers.clear()
        loadFiltersIntoContainers(filterList.joinToString("\n").byteInputStream())

        blockedRequests?.forEach {
            println("should be blocked: " + it.url + " " + it.pageHost)
            Assert.assertTrue(blocker.shouldBlock(it) is BlockResponse)
        }
        allowedRequests?.forEach {
            println("should not be blocked: " + it.url + " " + it.pageHost)
            Assert.assertNull(blocker.shouldBlock(it))
        }
        modifiedRequests?.forEach {
            println("should be modified: " + it.url + " " + it.pageHost)
            Assert.assertTrue(blocker.shouldBlock(it) is BlockResourceResponse || blocker.shouldBlock(it) is ModifyResponse)
        }
    }

    private fun checkFiltersWithContainer(
        filterList: List<String>,
        blockedRequests: List<ContentRequest>,
        allowedRequests: List<ContentRequest>,
        list: String,
        showFilters: Boolean = false
    ) {
        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())
        val container = FilterContainer()

        when (list) {
            "block" -> {
                Assert.assertEquals(filterList.size, set.filters[ABP_PREFIX_DENY].size)
                container.also { set.filters[ABP_PREFIX_DENY].forEach(it::plusAssign) }
            }
            "allow" -> {
                Assert.assertEquals(filterList.size, set.filters[ABP_PREFIX_ALLOW].size)
                container.also { set.filters[ABP_PREFIX_ALLOW].forEach(it::plusAssign) }
            }
            "modify" -> {
                Assert.assertEquals(filterList.size, set.filters[ABP_PREFIX_MODIFY].size)
                container.also { set.filters[ABP_PREFIX_MODIFY].forEach(it::plusAssign) }
            }
        }

        if (showFilters)
            container.getFilterList().forEach {
                println("${it.pattern}, ${it.javaClass}")
            }

        blockedRequests.forEach {
            println("should be filtered: " + it.url + " " + it.pageHost)
            Assert.assertNotNull(container[it])
        }
        allowedRequests.forEach {
            println("should not be touched: " + it.url + " " + it.pageHost)
            Assert.assertNull(container[it])
        }
    }

    @Test
    fun decoderCheckHeader() {
        val file1 = """
            ! Homepage: https://github.com/AdguardTeam/AdGuardFilters
            ! License: https://github.com/AdguardTeam/AdguardFilters/blob/master/LICENSE
            !
            !-------------------------------------------------------------------!
            !------------------ General JS API ---------------------------------!
            !-------------------------------------------------------------------!
            ! JS API START
            #%#var AG_onLoad=function(func){if(document.readyState==="complete"||document.readyState==="interactive")func();else if(document.addEventListener)document.addEventListener("DOMContentLoaded",func);else if(document.attachEvent)document.attachEvent("DOMContentLoaded",func)};
        """.trimIndent()
        Assert.assertTrue(AbpFilterDecoder().checkHeader(file1.byteInputStream().bufferedReader(), Charsets.UTF_8))

        val file2 = """
            [Adblock Plus 2.0]
            ! Version: 202201181719
            ! Title: EasyList
            ! Last modified: 18 Jan 2022 17:19 UTC
            ! Expires: 4 days (update frequency)
            ! Homepage: https://easylist.to/
            ! Licence: https://easylist.to/pages/licence.html
            ! !
            ! Please report any unblocked adverts or problems
            ! in the forums (https://forums.lanik.us/)
            ! or via e-mail (easylist@protonmail.com).
            ! GitHub issues: https://github.com/easylist/easylist/issues
            ! GitHub pull requests: https://github.com/easylist/easylist/pulls
            ! 
            ! -----------------------General advert blocking filters-----------------------!
            ! *** easylist:easylist/easylist_general_block.txt ***
            &ad_block=
        """.trimIndent()
        Assert.assertTrue(AbpFilterDecoder().checkHeader(file2.byteInputStream().bufferedReader(), Charsets.UTF_8))

        val file3 = """
            # Title: StevenBlack/hosts
            #
            # This hosts file is a merged collection of hosts from reputable sources,
            # with a dash of crowd sourcing via GitHub
            #
            # Date: 18 January 2022 18:32:01 (UTC)
            # Number of unique domains: 97,681
            #
            # Fetch the latest version of this file: https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts
            # Project home page: https://github.com/StevenBlack/hosts
            # Project releases: https://github.com/StevenBlack/hosts/releases
            #
            # ===============================================================

            127.0.0.1 localhost
        """.trimIndent()
        Assert.assertFalse(AbpFilterDecoder().checkHeader(file3.byteInputStream().bufferedReader(), Charsets.UTF_8))
    }

    @Test
    fun loadFilters() {
        // loadFiltersIntoContainers tests whether write/read works
        filterContainers.clear()
        loadFiltersIntoContainers(set2) // modify filters

        filterContainers.clear()
        loadFiltersIntoContainers(set) // easylist
    }

    @Test
    fun filterContainer() {
        blockerPrefixes.forEach { prefix ->
            val container = FilterContainer().also { set.filters[prefix].forEach(it::plusAssign) }
            val filterStore = ByteArrayOutputStream()
            filterStore.writeFilterList(set.filters[prefix], isModify(prefix))
            val container2 = FilterContainer().also {
                readFiltersFile(filterStore.toByteArray().inputStream(), isModify(prefix)).forEach(it::addWithTag)
            }
            val filterList = container.getFilterList()
            val filterList2 = container2.getFilterList()

            // filter lists from containers should be equal
            Assert.assertTrue(filterList.containsAll(filterList2) && filterList2.containsAll(filterList))

            // if inserting into filter container works, set.blacklist and filterList contain the same filters (but possibly in different order)
            Assert.assertTrue(filterList.containsAll(set.filters[prefix]) && set.filters[prefix].containsAll(filterList))
        }
    }

    @Test
    fun unsuppertedTypes() {
        val filterList = mutableListOf<String>()
        filterList.add("||ad.adpage.com/ads^\$object") // exclusively unsupported -> discard
        filterList.add("||ad.adpage.com/ads^\$object,3p") // type is still exclusively unsupported -> discard
        filterContainers.clear()
        loadFiltersIntoContainers(filterList)
        var size = 0
        blockerPrefixes.forEach {
            size += filterContainers[it]!!.getFilterList().size
        }
        Assert.assertEquals(size, 0)

        filterList.add("||ad.adpage.com/ads^\$~object") // inverse unsopported -> keep
        filterList.add("||ad.adpage.com/ads^\$object,image") // unsuppported and other -> keep
        loadFiltersIntoContainers(filterList)
        size = 0
        blockerPrefixes.forEach {
            size += filterContainers[it]!!.getFilterList().size
        }
        Assert.assertEquals(size, 2)

    }

    @Test
    fun blockList() {
        // TODO: go through more ubo/abp/adguard things and include examples for more or less everything
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()
        filterList.add("||ad.adpage.com/ads/")
        blockedRequests.add(request("http://ad.adpage.com/ads/badad", "https://example.com"))
        filterList.add("/badthing/*")
        blockedRequests.add(request("http://something.com/badthing/ad", "https://page.com"))
        allowedRequests.add(request("http://something.com/badthing", "https://page.com"))
        allowedRequests.add(request("http://something.com/badthingies", "https://page.com"))
        filterList.add("||page5.*/something")
        blockedRequests.add(request("http://page5.co.uk/something/page", "http://page.com"))
        blockedRequests.add(request("http://page5.com/something?test=yes", "http://page.com"))
        filterList.add("/badfolder/worsething/")
        blockedRequests.add(request("http://page6.co.uk/badfolder/worsething/", "http://page.com"))
        blockedRequests.add(request("http://page6.com/badfolder/worsething/something?test=yes", "http://page.com"))
        allowedRequests.add(request("http://page6.com/badfolder/worsething_/something?test=yes", "http://page.com"))
        filterList.add("||page7.*^")
        blockedRequests.add(request("http://page7.com/something/", "http://page.com"))
        blockedRequests.add(request("http://page7.co.uk/something/", "http://page.com"))

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block", true)
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun regex() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("/banner\\d+/")
        blockedRequests.add(request("http://page2.com/banner1", "https://something.page2.com"))
        blockedRequests.add(request("http://page2.com/banner123", "https://something.page2.com"))
        allowedRequests.add(request("http://page2.com/banner", "https://badpage.com"))
        allowedRequests.add(request("http://page2.com/banner/", "https://badpage.com"))

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun thirdParty() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||page2.com^\$1p")
        blockedRequests.add(request("http://page2.com/something", "https://something.page2.com"))
        allowedRequests.add(request("http://page2.com/something", "https://badpage.com"))
        filterList.add("||page3.com^\$3p")
        allowedRequests.add(request("http://page3.com/something", "https://something.page3.com"))
        blockedRequests.add(request("http://page3.com/something", "https://badpage.com"))

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun strictThirdParty() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||page6.com^\$strict1p")
        blockedRequests.add(request("http://page6.com/something", "https://page6.com"))
        allowedRequests.add(request("http://page6.com/something", "https://other.com"))
        allowedRequests.add(request("http://page6.com/something", "https://something.page6.com"))
        filterList.add("||page7.com^\$strict3p")
        allowedRequests.add(request("http://page7.com/something", "https://page7.com"))
        blockedRequests.add(request("http://page7.com/something", "https://other.com"))
        blockedRequests.add(request("http://page7.com/something", "https://something.page7.com"))

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun domainsRules() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||page.com^\$domain=~okpage.com")
        blockedRequests.add(request("http://page.com/ads/badad", "https://example.com"))
        allowedRequests.add(request("http://page.com/ads/badad", "https://okpage.com"))
        allowedRequests.add(request("http://page.com/ads/badad", "https://test.okpage.com"))
        filterList.add("||page2.com^\$domain=badpage.com")
        allowedRequests.add(request("http://page2.com/something", "https://okpage.com"))
        blockedRequests.add(request("http://page2.com/something", "https://badpage.com"))
        filterList.add("://ads.\$domain=~goodotherpage.com|~good.page3.com")
        blockedRequests.add(request("http://ads.page3.com/something", "https://something.page3.com"))
        allowedRequests.add(request("http://ads.page3.com/something", "https://good.page3.com"))
        allowedRequests.add(request("http://ads.page3.com/something", "https://goodotherpage.com"))
        filterList.add("*\$domain=page4.*")
        blockedRequests.add(request("http://ads.page4.com/something", "https://something.page4.com"))
        blockedRequests.add(request("http://whatever.com/something", "https://page4.com"))
        blockedRequests.add(request("http://whatever.com/something", "https://page4.co.uk"))
        allowedRequests.add(request("http://whatever.com/something", "https://page4.test.com"))
        allowedRequests.add(request("http://whatever.com/something", "https://page4.co.cn"))

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun simpleDomain() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("example.com") // should be same as ||example.com^, like done in uBo
        blockedRequests.add(request("http://ads.example.com/something", "https://page4.com"))
        blockedRequests.add(request("http://example.com/something", "https://page4.com"))
        allowedRequests.add(request("http://ads.example4.com/example.com/not", "https://page4.com"))
        filterList.add("||example2.com^")
        blockedRequests.add(request("http://ads.example2.com/something", "https://page4.com"))
        blockedRequests.add(request("http://example2.com/something", "https://page4.com"))
        allowedRequests.add(request("http://ads.com/example2.com/not", "https://page4.com"))
        allowedRequests.add(request("http://ads.example2.com.org/something", "https://page4.com"))
        filterList.add("|http://example3.com/|")
        blockedRequests.add(request("http://example3.com/", "https://page4.com"))
        allowedRequests.add(request("http://example3.com/bla", "https://page4.com"))

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun allUrls() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("*\$third-party")
        filterList.add("\$third-party")
        allowedRequests.add(request("http://ads.page4.com/something", "https://something.page4.com"))
        blockedRequests.add(request("http://ads.page4.com/something", "https://goodotherpage.com"))

        val s = loadFilterSet(filterList.joinToString("\n").byteInputStream())
        Assert.assertEquals(
            s.filters[ABP_PREFIX_DENY].first(),
            s.filters[ABP_PREFIX_DENY].last()
        ) // '*' and '' should give the same filter

        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun allowList() {
        val filterList = mutableListOf<String>()
        val allowlistedRequests = mutableListOf<ContentRequest>()
        val otherRequests = mutableListOf<ContentRequest>()
        filterList.add("@@||page.com/ads\$~third-party")
        allowlistedRequests.add(request("http://page.com/ads", "http://page.com"))
        otherRequests.add(request("http://page.com/ads", "http://thing.com"))

        checkFiltersWithContainer(filterList, allowlistedRequests, otherRequests, "allow")
    }

    @Test
    fun matchCase() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()
        filterList.add("||page.com/ads")
        blockedRequests.add(request("http://page.com/ads", "https://page.com"))
        blockedRequests.add(request("http://page.com/ADS", "https://page.com"))
        filterList.add("||page2.com/Ads\$match-case")
        blockedRequests.add(request("http://page2.com/Ads", "https://page.com"))
        allowedRequests.add(request("http://page2.com/ads", "https://page.com"))
        filterList.add("page3/ads")
        blockedRequests.add(request("http://page3.com/page3/Ads", "https://page.com"))
        blockedRequests.add(request("http://page3.com/page3/ads", "https://page.com"))
        filterList.add("page4/Ads\$match-case")
        blockedRequests.add(request("http://page4.com/page4/Ads", "https://page.com"))
        allowedRequests.add(request("http://page4.com/page4/ads", "https://page.com"))
        filterList.add("page5*/Ads\$match-case")
        blockedRequests.add(request("http://page5.com/page5b/Ads", "https://page.com"))
        allowedRequests.add(request("http://page5.com/page5b/ads", "https://page.com"))
        filterList.add("page6*/Ads")
        blockedRequests.add(request("http://page5.com/page6b/Ads", "https://page.com"))
        blockedRequests.add(request("http://page5.com/page6b/ads", "https://page.com"))

        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun removeparam() {
        // only tests removeparam so far
        val filterList = mutableListOf<String>()
        val modifiedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()
        filterList.add("\$removeparam=badparam")
        modifiedRequests.add(request("http://page.com/page?badparam=yes", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?badparam=yes#content", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?badparam", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?badparam=", "http://page.com"))
        modifiedRequests.add(request("http://page.com/page?goodparam&badparam=yes&other=no#content", "http://page.com"))
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
        // exceptions should be exceptions from blocking, but not from modifying
        filterList.add("||page8.com\$removeparam")
        filterList.add("@@||page8.com^")
        modifiedRequests.add(request("http://page8.com/page?para_am2=yes", "http://page.com"))
        filterList.add("||page9.com\$removeparam")
        filterList.add("@@||page9.com\$removeparam=badparam4")
        modifiedRequests.add(request("http://page9.com/page?para_am2=yes", "http://page.com"))
        allowedRequests.add(request("http://page9.com/page?badparam4=yes", "http://page.com"))
        filterList.add("||page10.com\$removeparam=badparam5")
        filterList.add("@@||page10.com\$removeparam")
        allowedRequests.add(request("http://page10.com/page?badparam5=yes", "http://page.com"))
        allowedRequests.add(request("http://page10.com/page?par_am2=yes", "http://page.com")) // blocked by /^par_/, but allowed here

        // can't check modify filters allow with container, because non-null could be returned only later
        //checkFiltersWithContainer(filterList, modifiedRequests, allowedRequests, "modify")
        checkFiltersWithBlocker(filterList, null, allowedRequests, modifiedRequests)
        modifiedRequests.forEach { println((blocker.shouldBlock(it) as ModifyResponse).url) }
    }

    @Test
    fun addResponseHeaders() {
        val filterList = mutableListOf<String>()
        val modifiedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        // add stuff to request header, check whether it really is added
        filterList.add("||example.com^\$inline-font")
        modifiedRequests.add(request("http://example.com/something", "https://goodotherpage.com"))
        filterList.add("||example2.com^\$inline-font")
        filterList.add("@@||example2.com^\$inline-font")
        allowedRequests.add(request("http://ads.example2.com/something", "https://something.page4.com"))
        filterList.add("||example3.com^\$inline-font")
        filterList.add("@@||example3.com^\$csp=font-src *")
        allowedRequests.add(request("http://example3.com/something", "https://something.page4.com"))
        filterList.add("||example4.com^\$inline-font")
        filterList.add("@@||example4.com^\$csp")
        allowedRequests.add(request("http://example4.com/something", "https://something.page4.com"))

        filterContainers.clear()
        loadFiltersIntoContainers(filterList.joinToString("\n").byteInputStream())
        modifiedRequests.forEach {
            val response = blocker.shouldBlock(it)
            Assert.assertNotNull(response)
            Assert.assertTrue(response is ModifyResponse)
            response as ModifyResponse
            Assert.assertEquals(response.addResponseHeaders?.get("Content-Security-Policy"), "font-src *")
        }
        allowedRequests.forEach {
            println("should not be touched: " + it.url + " " + it.pageHost)
            Assert.assertNull(blocker.shouldBlock(it))
        }
    }

    @Test
    fun removeResponseHeaders() {
        val filterList = mutableListOf<String>()
        val modifiedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        // add stuff to request header, check whether it really is added
        filterList.add("||example.com^##^responseheader(refresh)")
        filterList.add("||example.com^\$removeheader=otherHeaderToRemove")
        modifiedRequests.add(request("http://example.com/something", "https://goodotherpage.com"))

        // blacklisted headers, rule should be ignored
        filterList.add("||example2.com^\$removeheader=p3p")
        filterList.add("||example2.com^##^responseheader(Content-Security-Policy)")
        allowedRequests.add(request("http://example2.com/something", "https://goodotherpage.com"))

        filterContainers.clear()
        loadFiltersIntoContainers(filterList.joinToString("\n").byteInputStream())
        modifiedRequests.forEach {
            val response = blocker.shouldBlock(it)
            Assert.assertNotNull(response)
            Assert.assertTrue(response is ModifyResponse)
            response as ModifyResponse
            Assert.assertTrue(response.removeResponseHeaders?.containsAll(listOf("refresh", "otherHeaderToRemove").map { it.lowercase() }) == true)
        }
        allowedRequests.forEach {
            Assert.assertNull(blocker.shouldBlock(it))
        }
    }

    @Test
    fun redirect() {
        // TODO: check again, now after the change
        val filterList = mutableListOf<String>()
        val modifiedRequests = mutableListOf<ContentRequest>()

        filterList.add("||example.com^\$image,redirect=1x1.gif") // RES_1X1
        filterList.add("||example2.com^\$media,mp4") // RES_NOOP_MP4
        modifiedRequests.add(request("http://example.com/something.png", "https://goodotherpage.com"))
        modifiedRequests.add(request("http://example2.com/something.mpg", "https://goodotherpage.com"))

        filterContainers.clear()
        loadFiltersIntoContainers(filterList.joinToString("\n").byteInputStream())
        modifiedRequests.forEach {
            val response = blocker.shouldBlock(it)
            Assert.assertNotNull(response)
            Assert.assertTrue(response is BlockResourceResponse)
            response as BlockResourceResponse
            Assert.assertTrue(response.filename in listOf(RES_1X1, RES_NOOP_MP4))
        }
    }

    @Test
    fun all() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        // not really a good test, modify it... and understand what "all" is actually supposed to do!
        //  the csp parts imply it's a modify filter, but the initial discussions imply it should be blocking
        filterList.add("||example.com^\$all")
        allowedRequests.add(request("http://ads.example2.com/something", "https://something.page4.com"))
        blockedRequests.add(request("http://example.com/something", "https://goodotherpage.com"))

        // can't check modify filters allow with container, because non-null could be returned only later
        //checkFiltersWithContainer(filterList, modifiedRequests, allowedRequests, "modify")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun getQueryParameterMap() {
        // add some filter and make sure (only) the correct parameters are removed
        val url = Uri.parse("http://ads.test.net/ads?a=1&b=4#bla")
        val parameters = mapOf("a" to "1", "b" to "4")
        Assert.assertEquals(parameters, url.getQueryParameterMap())
    }

    @Test
    fun sanitize() {
        val filterList = mutableListOf<String>()
        filterList.add("||example.com^")
        filterList.add("||example.com^\$badfilter")
        filterList.add("@@||example2.com^")
        filterList.add("@@||example2.com^\$badfilter")
        filterList.add("||example3.com^\$3p")
        filterList.add("||example3.com^\$3p,badfilter")
        filterList.add("||example4.com^\$3p")
        filterList.add("||example4.com^\$3p,badfilter")

        val set = loadFilterSet(filterList.joinToString("\n").byteInputStream())
        Assert.assertTrue(set.filters[ABP_PREFIX_DENY].isNotEmpty())
        Assert.assertTrue(set.filters[ABP_PREFIX_BADFILTER + ABP_PREFIX_DENY].isNotEmpty())
        Assert.assertTrue(set.filters[ABP_PREFIX_DENY].sanitize(set.filters[ABP_PREFIX_BADFILTER + ABP_PREFIX_DENY]).isEmpty())
    }

    // avoid copying code, map filter set to correct type
    private fun Collection<UnifiedFilter>.sanitize(badFilters: Collection<UnifiedFilter>): Set<UnifiedFilter> {
        val filters = filterNot {
             badFilters.contains(it)
        }
        return filters.toSet()
    }

    @Test
    fun important() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("||example.com^")
        blockedRequests.add(request("http://example.com/something", "https://goodotherpage.com"))
        blockedRequests.add(request("http://example.com/something.png", "https://goodotherpage.com"))
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)

        blockedRequests.clear()
        filterList.add("@@||example.com^\$image")
        // maybe problem: if there is no htm, a bunch of default content types are assumed
        //   but this is not really a problem if accept header is not empty or */*
        blockedRequests.add(request("http://example.com/something.htm", "https://goodotherpage.com"))
        allowedRequests.add(request("http://example.com/something.gif", "https://goodotherpage.com"))
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)

        allowedRequests.clear()
        filterList.add("||example.com^\$important")
        blockedRequests.add(request("http://example.com/something.gif", "https://goodotherpage.com"))
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun denyallow() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("*\$denyallow=page1.com|page2.com,domain=page3.com|page4.com")
        blockedRequests.add(request("http://page.com/something.gif", "https://page3.com"))
        allowedRequests.add(request("http://page1.com/something.gif", "https://page3.com"))

        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    @Test
    fun contentType() {
        val filterList = mutableListOf<String>()
        val blockedRequests = mutableListOf<ContentRequest>()
        val allowedRequests = mutableListOf<ContentRequest>()

        filterList.add("example.com\$~document")
        blockedRequests.add(request("http://example.com/something", "https://page4.com", false))
        blockedRequests.add(request("http://example.com/something", "https://example.com/something", true))
        blockedRequests.add(request("http://example.com/something", "http://example.com/something/bad", true))
        // test below didn't work because document also gets type_other (see AdBLock.kt),
        //  and the filter is applied on type_other (because it's not type_document)
        //  now it should be better...
        allowedRequests.add(request("http://example.com/something", "http://example.com/something", true, mapOf("Accept" to "text/html")))

        filterList.add("||example2.com^\$subdocument")
        blockedRequests.add(request("http://example2.com/something", "https://example2.com", false, mapOf("Accept" to "text/html")))
        allowedRequests.add(request("http://example2.com/something", "https://example2.com", true, mapOf("Accept" to "text/html")))
        allowedRequests.add(request("http://example2.com/something", "https://example2.com"))

        filterList.add("||example3.com^\$xhr")
        blockedRequests.add(request("http://example3.com/something", "https://example3.com", false, mapOf("X-Requested-With" to "XMLHttpRequest")))
        allowedRequests.add(request("http://example3.com/something", "https://example3.com", false, mapOf("X-Requested-With" to "whatever")))

        filterList.add("||example4.com^\$script,3p") // block if script and 3rd party
        blockedRequests.add(request("http://example4.com/something.js", "https://page.com", false, mapOf("Accept" to "application/javascript")))
        blockedRequests.add(request("http://example4.com/something", "https://page.com", false, mapOf("Accept" to "application/javascript")))
        allowedRequests.add(request("http://example4.com/something.js", "https://example4.com", false, mapOf("Accept" to "application/javascript")))
        allowedRequests.add(request("http://example4.com/something", "https://example4.com", false, mapOf("Accept" to "application/javascript")))
        // the request below is actually blocked because no type can be determined (no file ending, no Accept header)
        //  default assumption is other+image+media+font+script+css, see getContentType
        //  is this reasonable? not sure how often such situations occur
        //allowedRequests.add(request("http://example4.com/something", "https://page.com"))

        filterList.add("||example5.com^\$~script") // block if not script
        blockedRequests.add(request("http://example5.com/something", "https://page.com"))
        allowedRequests.add(request("http://example5.com/something.js", "https://page.com"))
        allowedRequests.add(request("http://example5.com/something", "https://page.com", false, mapOf("Accept" to "application/javascript")))

        filterList.add("||example6.com^\$~script,3p") // block if not script and 3rd party -> 3rd party only allowed if script
        blockedRequests.add(request("http://example6.com/something", "https://page.com"))
        allowedRequests.add(request("http://example6.com/something.js", "https://example6.com", false, mapOf("Accept" to "application/javascript")))
        allowedRequests.add(request("http://example6.com/something.js", "https://page.com", false, mapOf("Accept" to "application/javascript")))
        allowedRequests.add(request("http://example6.com/something", "https://example6.com"))

        filterList.add("||example7.com^\$~script,~image") // block if not (script or image)
        blockedRequests.add(request("http://example7.com/something", "https://page.com"))
        allowedRequests.add(request("http://example7.com/something.png", "https://page.com"))
        allowedRequests.add(request("http://example7.com/something.js", "https://page.com"))

        loadFilterSet(filterList.joinToString("\n").byteInputStream()).filters[ABP_PREFIX_DENY].forEach {
            if (it.pattern.contains("example.")) {
                println("${it.contentType}, ${ContentRequest.TYPE_DOCUMENT}, ${it.contentType and ContentRequest.TYPE_DOCUMENT}")
                println("${it.thirdParty}")
                println("${allowedRequests[0].type}, ${allowedRequests[0].isThirdParty}")
            }
        }
        checkFiltersWithContainer(filterList, blockedRequests, allowedRequests, "block")
        println("now check with blocker")
        checkFiltersWithBlocker(filterList, blockedRequests, allowedRequests, null)
    }

    private fun WebResourceRequest.getContentRequest(pageUri: Uri) =
        ContentRequest(url, pageUri.host, getContentType(pageUri), is3rdParty(url, pageUri), requestHeaders, "GET")

    // should be same code as in AdBlock.kt, but with mimeTypeMap[extension] instead of getMimeTypeFromExtension(extension)
    // because mimetypemap not working in tests
    private fun WebResourceRequest.getContentType(pageUri: Uri): Int {
        var type = 0
        val scheme = url.scheme
//    var isPage = false
        val accept by lazy { requestHeaders["Accept"] }

        if (isForMainFrame) {
            if (url == pageUri) {
//            isPage = true
                type = ContentRequest.TYPE_DOCUMENT
            }
        } else if (accept != null && accept!!.contains("text/html"))
            type = ContentRequest.TYPE_SUB_DOCUMENT

        if (scheme == "ws" || scheme == "wss") {
            type = type or ContentRequest.TYPE_WEB_SOCKET
        }

        if (requestHeaders["X-Requested-With"] == "XMLHttpRequest") {
            type = type or ContentRequest.TYPE_XHR
        }

        val path = url.path ?: url.toString()
        val lastDot = path.lastIndexOf('.')
        if (lastDot >= 0) {
            when (val extension = path.substring(lastDot + 1).lowercase().substringBefore('?')) {
                "js" -> return type or ContentRequest.TYPE_SCRIPT
                "css" -> return type or ContentRequest.TYPE_STYLE_SHEET
                "otf", "ttf", "ttc", "woff", "woff2" -> return type or ContentRequest.TYPE_FONT
                "php" -> Unit
                else -> {
                    val mimeType = mimeTypeMap[extension] ?: "application/octet-stream"
                    if (mimeType != "application/octet-stream") {
                        return otherIfNone(type or mimeType.getFilterType())
                    }
                }
            }
        }

        // why was this here?
        // breaks many $~document filters, because if there is no file extension (which is very common for main frame urls)
        //  then type_document ALWAYS has type_other and thus is blocked by $~document
//    if (isPage) {
//        return type or ContentRequest.TYPE_OTHER
//    }

        return if (accept != null && accept != "*/*") {
            val mimeType = accept!!.split(',')[0]
            otherIfNone(type or mimeType.getFilterType())
        } else {
            type or ContentRequest.TYPE_OTHER or ContentRequest.TYPE_MEDIA or ContentRequest.TYPE_IMAGE or
                    ContentRequest.TYPE_FONT or ContentRequest.TYPE_STYLE_SHEET or ContentRequest.TYPE_SCRIPT
        }
    }
}

class TestWebResourceRequest(private val url2: Uri,
                             private val isForMainFrame2: Boolean,
                             private val requestHeaders2: Map<String, String>
                             ): WebResourceRequest {
    override fun getUrl() = url2
    override fun isForMainFrame() = isForMainFrame2
    override fun getRequestHeaders() = requestHeaders2
    override fun isRedirect() = false // not needed
    override fun hasGesture() = false // not needed
    override fun getMethod() = "GET" // not needed, but should be valid
}

fun is3rdParty(url: Uri, pageUri: Uri): Int {
    val hostName = url.host?.lowercase() ?: return THIRD_PARTY
    val pageHost = pageUri.host ?: return THIRD_PARTY

    if (hostName == pageHost) return STRICT_FIRST_PARTY

    val ipPattern = PatternsCompat.IP_ADDRESS
    if (ipPattern.matcher(hostName).matches() || ipPattern.matcher(pageHost).matches())
        return THIRD_PARTY

    val db = PublicSuffix.get()

    return if (db.getEffectiveTldPlusOne(hostName) != db.getEffectiveTldPlusOne(pageHost))
        THIRD_PARTY
    else
        FIRST_PARTY
}


// map taken from https://android.googlesource.com/platform/frameworks/base/+/61ae88e/core/java/android/webkit/MimeTypeMap.java
fun loadMimeTypeMap(): Map<String, String> {
    val sMimeTypeMap = mutableMapOf<String, String>()
    sMimeTypeMap.loadEntry("application/andrew-inset", "ez")
    sMimeTypeMap.loadEntry("application/dsptype", "tsp")
    sMimeTypeMap.loadEntry("application/futuresplash", "spl")
    sMimeTypeMap.loadEntry("application/hta", "hta")
    sMimeTypeMap.loadEntry("application/mac-binhex40", "hqx")
    sMimeTypeMap.loadEntry("application/mac-compactpro", "cpt")
    sMimeTypeMap.loadEntry("application/mathematica", "nb")
    sMimeTypeMap.loadEntry("application/msaccess", "mdb")
    sMimeTypeMap.loadEntry("application/oda", "oda")
    sMimeTypeMap.loadEntry("application/ogg", "ogg")
    sMimeTypeMap.loadEntry("application/pdf", "pdf")
    sMimeTypeMap.loadEntry("application/pgp-keys", "key")
    sMimeTypeMap.loadEntry("application/pgp-signature", "pgp")
    sMimeTypeMap.loadEntry("application/pics-rules", "prf")
    sMimeTypeMap.loadEntry("application/rar", "rar")
    sMimeTypeMap.loadEntry("application/rdf+xml", "rdf")
    sMimeTypeMap.loadEntry("application/rss+xml", "rss")
    sMimeTypeMap.loadEntry("application/zip", "zip")
    sMimeTypeMap.loadEntry("application/vnd.android.package-archive",
                    "apk")
    sMimeTypeMap.loadEntry("application/vnd.cinderella", "cdy")
    sMimeTypeMap.loadEntry("application/vnd.ms-pki.stl", "stl")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.database", "odb")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.formula", "odf")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.graphics", "odg")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.graphics-template",
                    "otg")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.image", "odi")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.spreadsheet", "ods")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.spreadsheet-template",
                    "ots")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.text", "odt")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.text-master", "odm")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.text-template", "ott")
    sMimeTypeMap.loadEntry(
                    "application/vnd.oasis.opendocument.text-web", "oth")
    sMimeTypeMap.loadEntry("application/msword", "doc")
    sMimeTypeMap.loadEntry("application/msword", "dot")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "docx")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                    "dotx")
    sMimeTypeMap.loadEntry("application/vnd.ms-excel", "xls")
    sMimeTypeMap.loadEntry("application/vnd.ms-excel", "xlt")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "xlsx")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                    "xltx")
    sMimeTypeMap.loadEntry("application/vnd.ms-powerpoint", "ppt")
    sMimeTypeMap.loadEntry("application/vnd.ms-powerpoint", "pot")
    sMimeTypeMap.loadEntry("application/vnd.ms-powerpoint", "pps")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "pptx")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.presentationml.template",
                    "potx")
    sMimeTypeMap.loadEntry(
                    "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                    "ppsx")
    sMimeTypeMap.loadEntry("application/vnd.rim.cod", "cod")
    sMimeTypeMap.loadEntry("application/vnd.smaf", "mmf")
    sMimeTypeMap.loadEntry("application/vnd.stardivision.calc", "sdc")
    sMimeTypeMap.loadEntry("application/vnd.stardivision.draw", "sda")
    sMimeTypeMap.loadEntry(
                    "application/vnd.stardivision.impress", "sdd")
    sMimeTypeMap.loadEntry(
                    "application/vnd.stardivision.impress", "sdp")
    sMimeTypeMap.loadEntry("application/vnd.stardivision.math", "smf")
    sMimeTypeMap.loadEntry("application/vnd.stardivision.writer",
                    "sdw")
    sMimeTypeMap.loadEntry("application/vnd.stardivision.writer",
                    "vor")
    sMimeTypeMap.loadEntry(
                    "application/vnd.stardivision.writer-global", "sgl")
    sMimeTypeMap.loadEntry("application/vnd.sun.xml.calc", "sxc")
    sMimeTypeMap.loadEntry(
                    "application/vnd.sun.xml.calc.template", "stc")
    sMimeTypeMap.loadEntry("application/vnd.sun.xml.draw", "sxd")
    sMimeTypeMap.loadEntry(
                    "application/vnd.sun.xml.draw.template", "std")
    sMimeTypeMap.loadEntry("application/vnd.sun.xml.impress", "sxi")
    sMimeTypeMap.loadEntry(
                    "application/vnd.sun.xml.impress.template", "sti")
    sMimeTypeMap.loadEntry("application/vnd.sun.xml.math", "sxm")
    sMimeTypeMap.loadEntry("application/vnd.sun.xml.writer", "sxw")
    sMimeTypeMap.loadEntry(
                    "application/vnd.sun.xml.writer.global", "sxg")
    sMimeTypeMap.loadEntry(
                    "application/vnd.sun.xml.writer.template", "stw")
    sMimeTypeMap.loadEntry("application/vnd.visio", "vsd")
    sMimeTypeMap.loadEntry("application/x-abiword", "abw")
    sMimeTypeMap.loadEntry("application/x-apple-diskimage", "dmg")
    sMimeTypeMap.loadEntry("application/x-bcpio", "bcpio")
    sMimeTypeMap.loadEntry("application/x-bittorrent", "torrent")
    sMimeTypeMap.loadEntry("application/x-cdf", "cdf")
    sMimeTypeMap.loadEntry("application/x-cdlink", "vcd")
    sMimeTypeMap.loadEntry("application/x-chess-pgn", "pgn")
    sMimeTypeMap.loadEntry("application/x-cpio", "cpio")
    sMimeTypeMap.loadEntry("application/x-debian-package", "deb")
    sMimeTypeMap.loadEntry("application/x-debian-package", "udeb")
    sMimeTypeMap.loadEntry("application/x-director", "dcr")
    sMimeTypeMap.loadEntry("application/x-director", "dir")
    sMimeTypeMap.loadEntry("application/x-director", "dxr")
    sMimeTypeMap.loadEntry("application/x-dms", "dms")
    sMimeTypeMap.loadEntry("application/x-doom", "wad")
    sMimeTypeMap.loadEntry("application/x-dvi", "dvi")
    sMimeTypeMap.loadEntry("application/x-flac", "flac")
    sMimeTypeMap.loadEntry("application/x-font", "pfa")
    sMimeTypeMap.loadEntry("application/x-font", "pfb")
    sMimeTypeMap.loadEntry("application/x-font", "gsf")
    sMimeTypeMap.loadEntry("application/x-font", "pcf")
    sMimeTypeMap.loadEntry("application/x-font", "pcf.Z")
    sMimeTypeMap.loadEntry("application/x-freemind", "mm")
    sMimeTypeMap.loadEntry("application/x-futuresplash", "spl")
    sMimeTypeMap.loadEntry("application/x-gnumeric", "gnumeric")
    sMimeTypeMap.loadEntry("application/x-go-sgf", "sgf")
    sMimeTypeMap.loadEntry("application/x-graphing-calculator", "gcf")
    sMimeTypeMap.loadEntry("application/x-gtar", "gtar")
    sMimeTypeMap.loadEntry("application/x-gtar", "tgz")
    sMimeTypeMap.loadEntry("application/x-gtar", "taz")
    sMimeTypeMap.loadEntry("application/x-hdf", "hdf")
    sMimeTypeMap.loadEntry("application/x-ica", "ica")
    sMimeTypeMap.loadEntry("application/x-internet-signup", "ins")
    sMimeTypeMap.loadEntry("application/x-internet-signup", "isp")
    sMimeTypeMap.loadEntry("application/x-iphone", "iii")
    sMimeTypeMap.loadEntry("application/x-iso9660-image", "iso")
    sMimeTypeMap.loadEntry("application/x-jmol", "jmz")
    sMimeTypeMap.loadEntry("application/x-kchart", "chrt")
    sMimeTypeMap.loadEntry("application/x-killustrator", "kil")
    sMimeTypeMap.loadEntry("application/x-koan", "skp")
    sMimeTypeMap.loadEntry("application/x-koan", "skd")
    sMimeTypeMap.loadEntry("application/x-koan", "skt")
    sMimeTypeMap.loadEntry("application/x-koan", "skm")
    sMimeTypeMap.loadEntry("application/x-kpresenter", "kpr")
    sMimeTypeMap.loadEntry("application/x-kpresenter", "kpt")
    sMimeTypeMap.loadEntry("application/x-kspread", "ksp")
    sMimeTypeMap.loadEntry("application/x-kword", "kwd")
    sMimeTypeMap.loadEntry("application/x-kword", "kwt")
    sMimeTypeMap.loadEntry("application/x-latex", "latex")
    sMimeTypeMap.loadEntry("application/x-lha", "lha")
    sMimeTypeMap.loadEntry("application/x-lzh", "lzh")
    sMimeTypeMap.loadEntry("application/x-lzx", "lzx")
    sMimeTypeMap.loadEntry("application/x-maker", "frm")
    sMimeTypeMap.loadEntry("application/x-maker", "maker")
    sMimeTypeMap.loadEntry("application/x-maker", "frame")
    sMimeTypeMap.loadEntry("application/x-maker", "fb")
    sMimeTypeMap.loadEntry("application/x-maker", "book")
    sMimeTypeMap.loadEntry("application/x-maker", "fbdoc")
    sMimeTypeMap.loadEntry("application/x-mif", "mif")
    sMimeTypeMap.loadEntry("application/x-ms-wmd", "wmd")
    sMimeTypeMap.loadEntry("application/x-ms-wmz", "wmz")
    sMimeTypeMap.loadEntry("application/x-msi", "msi")
    sMimeTypeMap.loadEntry("application/x-ns-proxy-autoconfig", "pac")
    sMimeTypeMap.loadEntry("application/x-nwc", "nwc")
    sMimeTypeMap.loadEntry("application/x-object", "o")
    sMimeTypeMap.loadEntry("application/x-oz-application", "oza")
    sMimeTypeMap.loadEntry("application/x-pkcs12", "p12")
    sMimeTypeMap.loadEntry("application/x-pkcs7-certreqresp", "p7r")
    sMimeTypeMap.loadEntry("application/x-pkcs7-crl", "crl")
    sMimeTypeMap.loadEntry("application/x-quicktimeplayer", "qtl")
    sMimeTypeMap.loadEntry("application/x-shar", "shar")
    sMimeTypeMap.loadEntry("application/x-shockwave-flash", "swf")
    sMimeTypeMap.loadEntry("application/x-stuffit", "sit")
    sMimeTypeMap.loadEntry("application/x-sv4cpio", "sv4cpio")
    sMimeTypeMap.loadEntry("application/x-sv4crc", "sv4crc")
    sMimeTypeMap.loadEntry("application/x-tar", "tar")
    sMimeTypeMap.loadEntry("application/x-texinfo", "texinfo")
    sMimeTypeMap.loadEntry("application/x-texinfo", "texi")
    sMimeTypeMap.loadEntry("application/x-troff", "t")
    sMimeTypeMap.loadEntry("application/x-troff", "roff")
    sMimeTypeMap.loadEntry("application/x-troff-man", "man")
    sMimeTypeMap.loadEntry("application/x-ustar", "ustar")
    sMimeTypeMap.loadEntry("application/x-wais-source", "src")
    sMimeTypeMap.loadEntry("application/x-wingz", "wz")
    sMimeTypeMap.loadEntry("application/x-webarchive", "webarchive")
    sMimeTypeMap.loadEntry("application/x-x509-ca-cert", "crt")
    sMimeTypeMap.loadEntry("application/x-x509-user-cert", "crt")
    sMimeTypeMap.loadEntry("application/x-xcf", "xcf")
    sMimeTypeMap.loadEntry("application/x-xfig", "fig")
    sMimeTypeMap.loadEntry("application/xhtml+xml", "xhtml")
    sMimeTypeMap.loadEntry("audio/3gpp", "3gpp")
    sMimeTypeMap.loadEntry("audio/basic", "snd")
    sMimeTypeMap.loadEntry("audio/midi", "mid")
    sMimeTypeMap.loadEntry("audio/midi", "midi")
    sMimeTypeMap.loadEntry("audio/midi", "kar")
    sMimeTypeMap.loadEntry("audio/mpeg", "mpga")
    sMimeTypeMap.loadEntry("audio/mpeg", "mpega")
    sMimeTypeMap.loadEntry("audio/mpeg", "mp2")
    sMimeTypeMap.loadEntry("audio/mpeg", "mp3")
    sMimeTypeMap.loadEntry("audio/mpeg", "m4a")
    sMimeTypeMap.loadEntry("audio/mpegurl", "m3u")
    sMimeTypeMap.loadEntry("audio/prs.sid", "sid")
    sMimeTypeMap.loadEntry("audio/x-aiff", "aif")
    sMimeTypeMap.loadEntry("audio/x-aiff", "aiff")
    sMimeTypeMap.loadEntry("audio/x-aiff", "aifc")
    sMimeTypeMap.loadEntry("audio/x-gsm", "gsm")
    sMimeTypeMap.loadEntry("audio/x-mpegurl", "m3u")
    sMimeTypeMap.loadEntry("audio/x-ms-wma", "wma")
    sMimeTypeMap.loadEntry("audio/x-ms-wax", "wax")
    sMimeTypeMap.loadEntry("audio/x-pn-realaudio", "ra")
    sMimeTypeMap.loadEntry("audio/x-pn-realaudio", "rm")
    sMimeTypeMap.loadEntry("audio/x-pn-realaudio", "ram")
    sMimeTypeMap.loadEntry("audio/x-realaudio", "ra")
    sMimeTypeMap.loadEntry("audio/x-scpls", "pls")
    sMimeTypeMap.loadEntry("audio/x-sd2", "sd2")
    sMimeTypeMap.loadEntry("audio/x-wav", "wav")
    sMimeTypeMap.loadEntry("image/bmp", "bmp")
    sMimeTypeMap.loadEntry("image/gif", "gif")
    sMimeTypeMap.loadEntry("image/ico", "cur")
    sMimeTypeMap.loadEntry("image/ico", "ico")
    sMimeTypeMap.loadEntry("image/ief", "ief")
    sMimeTypeMap.loadEntry("image/jpeg", "jpeg")
    sMimeTypeMap.loadEntry("image/jpeg", "jpg")
    sMimeTypeMap.loadEntry("image/jpeg", "jpe")
    sMimeTypeMap.loadEntry("image/pcx", "pcx")
    sMimeTypeMap.loadEntry("image/png", "png")
    sMimeTypeMap.loadEntry("image/svg+xml", "svg")
    sMimeTypeMap.loadEntry("image/svg+xml", "svgz")
    sMimeTypeMap.loadEntry("image/tiff", "tiff")
    sMimeTypeMap.loadEntry("image/tiff", "tif")
    sMimeTypeMap.loadEntry("image/vnd.djvu", "djvu")
    sMimeTypeMap.loadEntry("image/vnd.djvu", "djv")
    sMimeTypeMap.loadEntry("image/vnd.wap.wbmp", "wbmp")
    sMimeTypeMap.loadEntry("image/x-cmu-raster", "ras")
    sMimeTypeMap.loadEntry("image/x-coreldraw", "cdr")
    sMimeTypeMap.loadEntry("image/x-coreldrawpattern", "pat")
    sMimeTypeMap.loadEntry("image/x-coreldrawtemplate", "cdt")
    sMimeTypeMap.loadEntry("image/x-corelphotopaint", "cpt")
    sMimeTypeMap.loadEntry("image/x-icon", "ico")
    sMimeTypeMap.loadEntry("image/x-jg", "art")
    sMimeTypeMap.loadEntry("image/x-jng", "jng")
    sMimeTypeMap.loadEntry("image/x-ms-bmp", "bmp")
    sMimeTypeMap.loadEntry("image/x-photoshop", "psd")
    sMimeTypeMap.loadEntry("image/x-portable-anymap", "pnm")
    sMimeTypeMap.loadEntry("image/x-portable-bitmap", "pbm")
    sMimeTypeMap.loadEntry("image/x-portable-graymap", "pgm")
    sMimeTypeMap.loadEntry("image/x-portable-pixmap", "ppm")
    sMimeTypeMap.loadEntry("image/x-rgb", "rgb")
    sMimeTypeMap.loadEntry("image/x-xbitmap", "xbm")
    sMimeTypeMap.loadEntry("image/x-xpixmap", "xpm")
    sMimeTypeMap.loadEntry("image/x-xwindowdump", "xwd")
    sMimeTypeMap.loadEntry("model/iges", "igs")
    sMimeTypeMap.loadEntry("model/iges", "iges")
    sMimeTypeMap.loadEntry("model/mesh", "msh")
    sMimeTypeMap.loadEntry("model/mesh", "mesh")
    sMimeTypeMap.loadEntry("model/mesh", "silo")
    sMimeTypeMap.loadEntry("text/calendar", "ics")
    sMimeTypeMap.loadEntry("text/calendar", "icz")
    sMimeTypeMap.loadEntry("text/comma-separated-values", "csv")
    sMimeTypeMap.loadEntry("text/css", "css")
    sMimeTypeMap.loadEntry("text/html", "htm")
    sMimeTypeMap.loadEntry("text/html", "html")
    sMimeTypeMap.loadEntry("text/h323", "323")
    sMimeTypeMap.loadEntry("text/iuls", "uls")
    sMimeTypeMap.loadEntry("text/mathml", "mml")
    // add it first so it will be the default for ExtensionFromMimeType
            sMimeTypeMap.loadEntry("text/plain", "txt")
    sMimeTypeMap.loadEntry("text/plain", "asc")
    sMimeTypeMap.loadEntry("text/plain", "text")
    sMimeTypeMap.loadEntry("text/plain", "diff")
    sMimeTypeMap.loadEntry("text/plain", "po")     // reserve "pot" for vnd.ms-powerpoint
            sMimeTypeMap.loadEntry("text/richtext", "rtx")
    sMimeTypeMap.loadEntry("text/rtf", "rtf")
    sMimeTypeMap.loadEntry("text/texmacs", "ts")
    sMimeTypeMap.loadEntry("text/text", "phps")
    sMimeTypeMap.loadEntry("text/tab-separated-values", "tsv")
    sMimeTypeMap.loadEntry("text/xml", "xml")
    sMimeTypeMap.loadEntry("text/x-bibtex", "bib")
    sMimeTypeMap.loadEntry("text/x-boo", "boo")
    sMimeTypeMap.loadEntry("text/x-c++hdr", "h++")
    sMimeTypeMap.loadEntry("text/x-c++hdr", "hpp")
    sMimeTypeMap.loadEntry("text/x-c++hdr", "hxx")
    sMimeTypeMap.loadEntry("text/x-c++hdr", "hh")
    sMimeTypeMap.loadEntry("text/x-c++src", "c++")
    sMimeTypeMap.loadEntry("text/x-c++src", "cpp")
    sMimeTypeMap.loadEntry("text/x-c++src", "cxx")
    sMimeTypeMap.loadEntry("text/x-chdr", "h")
    sMimeTypeMap.loadEntry("text/x-component", "htc")
    sMimeTypeMap.loadEntry("text/x-csh", "csh")
    sMimeTypeMap.loadEntry("text/x-csrc", "c")
    sMimeTypeMap.loadEntry("text/x-dsrc", "d")
    sMimeTypeMap.loadEntry("text/x-haskell", "hs")
    sMimeTypeMap.loadEntry("text/x-java", "java")
    sMimeTypeMap.loadEntry("text/x-literate-haskell", "lhs")
    sMimeTypeMap.loadEntry("text/x-moc", "moc")
    sMimeTypeMap.loadEntry("text/x-pascal", "p")
    sMimeTypeMap.loadEntry("text/x-pascal", "pas")
    sMimeTypeMap.loadEntry("text/x-pcs-gcd", "gcd")
    sMimeTypeMap.loadEntry("text/x-setext", "etx")
    sMimeTypeMap.loadEntry("text/x-tcl", "tcl")
    sMimeTypeMap.loadEntry("text/x-tex", "tex")
    sMimeTypeMap.loadEntry("text/x-tex", "ltx")
    sMimeTypeMap.loadEntry("text/x-tex", "sty")
    sMimeTypeMap.loadEntry("text/x-tex", "cls")
    sMimeTypeMap.loadEntry("text/x-vcalendar", "vcs")
    sMimeTypeMap.loadEntry("text/x-vcard", "vcf")
    sMimeTypeMap.loadEntry("video/3gpp", "3gpp")
    sMimeTypeMap.loadEntry("video/3gpp", "3gp")
    sMimeTypeMap.loadEntry("video/3gpp", "3g2")
    sMimeTypeMap.loadEntry("video/dl", "dl")
    sMimeTypeMap.loadEntry("video/dv", "dif")
    sMimeTypeMap.loadEntry("video/dv", "dv")
    sMimeTypeMap.loadEntry("video/fli", "fli")
    sMimeTypeMap.loadEntry("video/m4v", "m4v")
    sMimeTypeMap.loadEntry("video/mpeg", "mpeg")
    sMimeTypeMap.loadEntry("video/mpeg", "mpg")
    sMimeTypeMap.loadEntry("video/mpeg", "mpe")
    sMimeTypeMap.loadEntry("video/mp4", "mp4")
    sMimeTypeMap.loadEntry("video/mpeg", "VOB")
    sMimeTypeMap.loadEntry("video/quicktime", "qt")
    sMimeTypeMap.loadEntry("video/quicktime", "mov")
    sMimeTypeMap.loadEntry("video/vnd.mpegurl", "mxu")
    sMimeTypeMap.loadEntry("video/x-la-asf", "lsf")
    sMimeTypeMap.loadEntry("video/x-la-asf", "lsx")
    sMimeTypeMap.loadEntry("video/x-mng", "mng")
    sMimeTypeMap.loadEntry("video/x-ms-asf", "asf")
    sMimeTypeMap.loadEntry("video/x-ms-asf", "asx")
    sMimeTypeMap.loadEntry("video/x-ms-wm", "wm")
    sMimeTypeMap.loadEntry("video/x-ms-wmv", "wmv")
    sMimeTypeMap.loadEntry("video/x-ms-wmx", "wmx")
    sMimeTypeMap.loadEntry("video/x-ms-wvx", "wvx")
    sMimeTypeMap.loadEntry("video/x-msvideo", "avi")
    sMimeTypeMap.loadEntry("video/x-sgi-movie", "movie")
    sMimeTypeMap.loadEntry("x-conference/x-cooltalk", "ice")
    sMimeTypeMap.loadEntry("x-epoc/x-sisx-app", "sisx")
    return sMimeTypeMap
}

fun MutableMap<String,String>.loadEntry(type: String, ext: String) {
    put(ext,type)
}
