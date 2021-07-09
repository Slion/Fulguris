package acr.browser.lightning.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

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

    class TestWebResourceRequest(url: Uri,
                                 isForMainFrame: Boolean,
                                 requestHeaders: Map<String, String>): WebResourceRequest {
        override fun getUrl(): Uri {
            return url
        }

        override fun isForMainFrame(): Boolean {
            return isForMainFrame
        }

        override fun isRedirect(): Boolean {
            return false // not needed
        }

        override fun hasGesture(): Boolean {
            return false // not needed
        }

        override fun getMethod(): String {
            return "" // not needed
        }

        override fun getRequestHeaders(): MutableMap<String, String> {
            return requestHeaders
        }

    }
}
