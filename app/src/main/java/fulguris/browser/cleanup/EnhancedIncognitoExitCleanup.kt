package fulguris.browser.cleanup

import fulguris.activity.WebBrowserActivity
import fulguris.utils.WebUtils
import android.webkit.WebView
import timber.log.Timber
import javax.inject.Inject

/**
 * Exit cleanup that should be run when the incognito process is exited on API >= 28. This cleanup
 * clears cookies and all web data, which can be done without affecting
 */
class EnhancedIncognitoExitCleanup @Inject constructor() :
    ExitCleanup {
    override fun cleanUp(webView: WebView?, context: WebBrowserActivity) {
        WebUtils.clearCache(webView, context)
        Timber.i("Cache Cleared")
        WebUtils.clearCookies()
        Timber.i("Cookies Cleared")
        WebUtils.clearWebStorage()
        Timber.i("WebStorage Cleared")
    }
}
