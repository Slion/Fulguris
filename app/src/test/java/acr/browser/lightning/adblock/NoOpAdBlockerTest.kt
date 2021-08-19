package acr.browser.lightning.adblock

import android.net.Uri
import androidx.core.net.toUri
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
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
    val filters = mutableListOf<Pair<String, UnifiedFilter>>()
    val set2: UnifiedFilterSet

    init {
        val ASSET_BASE_PATH = "../assets/"
        val easylist = File(ASSET_BASE_PATH + "easylist-minified.txt")
        val modifyFilters = File(ASSET_BASE_PATH + "adguard-urltracking.txt")
        easylist.inputStream().bufferedReader().use {
            val decoder = AbpFilterDecoder()
            set = decoder.decode(it, null)
        }

        modifyFilters.inputStream().bufferedReader().use {
            val decoder = AbpFilterDecoder()
            set2 = decoder.decode(it, null)
        }

        val filterStore = ByteArrayOutputStream()
        filterStore.use {
            val writer = FilterWriter()
            writer.write(it, set.blackList)
        }

        filterStore.toByteArray().inputStream().use { stream ->
            val reader = FilterReader(stream)
            if (reader.checkHeader())
                filters.addAll(reader.readAll())
        }
    }

    @Test
    fun writeAndReadWorks() {
        Assert.assertEquals(set.blackList, filters.map { it.second })
    }

    @Test
    fun modifyFiltersReadWrite() {
        val filterStore = ByteArrayOutputStream()
        filterStore.use {
            val writer = FilterWriter()
            writer.writeModifyFilters(it, set2.modifyList)
        }

        val modifyFilters = mutableListOf<Pair<String, UnifiedFilter>>()
        filterStore.toByteArray().inputStream().use { stream ->
            val reader = FilterReader(stream)
            if (reader.checkHeader())
                modifyFilters.addAll(reader.readAllModifyFilters())
        }
        println(set2.modifyList[140].pattern)
        println(set2.modifyList[140].modify)

        Assert.assertEquals(set2.modifyList, modifyFilters.map { it.second })
    }

    @Test
    fun filterContainerWorks() {
        val container = FilterContainer().also { filters.forEach(it::addWithTag) }

        Assert.assertNotNull(container[TestWebResourceRequest(Uri.parse("http://g.doubleclick.net/ads"), false, mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 9; SM-I9195I) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.204 Mobile Safari/537.36",
                "Origin" to "https://orf.at", "Referer" to "https://orf.at/", "Accept" to "application/json, text/javascript, */*; q=0.01")).getContentRequest("https://orf.at".toUri())])

        Assert.assertNull(container[TestWebResourceRequest(Uri.parse("https://orf.at/"), false, mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 9; SM-I9195I) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.204 Mobile Safari/537.36",
                "Origin" to "https://orf.at", "Referer" to "https://orf.at/", "Accept" to "application/json, text/javascript, */*; q=0.01")).getContentRequest("https://orf.at".toUri())])

    }

}
