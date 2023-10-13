package fulguris.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dagger.Reusable
import javax.inject.Inject

/**
 * A no-op ad blocker implementation. Always returns false for [isAd].
 */
@Reusable
class NoOpAdBlocker @Inject constructor() :
    AdBlocker {

    //override fun isAd(url: String) = false

    // unused element hiding currently disabled
    //override fun loadScript(uri: Uri): String? = null

    override fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse? = null
}
