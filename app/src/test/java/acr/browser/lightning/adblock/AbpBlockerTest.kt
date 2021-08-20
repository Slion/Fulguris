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
// all moved to NoOpAdBlockerTest, because when put there the same code suddenly works...
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