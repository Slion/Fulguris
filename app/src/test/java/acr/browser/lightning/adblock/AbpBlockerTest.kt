package acr.browser.lightning.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.core.util.PatternsCompat
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.abp.AbpFilterDecoder
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilterSet
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.getContentType
import okhttp3.internal.publicsuffix.PublicSuffix
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*

class AbpBlockerTest {
    var set: UnifiedFilterSet
    val filters = mutableListOf<Pair<String, UnifiedFilter>>()

    init {
        val ASSET_BASE_PATH = "../app/src/main/assets/"
        val easylist = File(ASSET_BASE_PATH + "easylist.txt")
        easylist.inputStream().bufferedReader().use {
            val decoder = AbpFilterDecoder()
            set = decoder.decode(it, null)
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
        assertEquals(set.blackList, filters.map {it.second})
    }

    @Test
    fun filterContainerWorks() {
        val container = FilterContainer().also { filters.forEach(it::addWithTag) }

        assertNotNull(container[TestWebResourceRequest(Uri.parse("http://g.doubleclick.net/ads"),false, mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 9; SM-I9195I) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.204 Mobile Safari/537.36",
            "Origin" to "https://orf.at", "Referer" to "https://orf.at/", "Accept" to "application/json, text/javascript, */*; q=0.01")).getContentRequest("https://orf.at".toUri())])

        assertNull(container[TestWebResourceRequest(Uri.parse("https://orf.at/"),false, mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 9; SM-I9195I) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.204 Mobile Safari/537.36",
            "Origin" to "https://orf.at", "Referer" to "https://orf.at/", "Accept" to "application/json, text/javascript, */*; q=0.01")).getContentRequest("https://orf.at".toUri())])

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
    ContentRequest(url, pageUri, getContentType(pageUri), is3rdParty(url, pageUri))

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