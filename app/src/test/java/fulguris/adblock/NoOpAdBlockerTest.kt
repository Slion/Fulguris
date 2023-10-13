package fulguris.adblock

import android.net.Uri
import fulguris.adblock.NoOpAdBlocker
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

}
