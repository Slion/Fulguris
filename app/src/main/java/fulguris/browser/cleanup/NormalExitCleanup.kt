package fulguris.browser.cleanup

import fulguris.activity.WebBrowserActivity
import fulguris.database.history.HistoryDatabase
import fulguris.di.DatabaseScheduler
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.WebUtils
import android.webkit.WebView
import io.reactivex.Scheduler
import timber.log.Timber
import javax.inject.Inject

/**
 * Exit cleanup that should run whenever the main browser process is exiting.
 */
class NormalExitCleanup @Inject constructor(
    private val userPreferences: UserPreferences,
    private val historyDatabase: HistoryDatabase,
    @DatabaseScheduler private val databaseScheduler: Scheduler
) : ExitCleanup {
    override fun cleanUp(webView: WebView?, context: WebBrowserActivity) {
        if (userPreferences.clearCacheExit) {
            WebUtils.clearCache(webView, context)
            Timber.i("Cache Cleared")
        }
        if (userPreferences.clearHistoryExitEnabled) {
            WebUtils.clearHistory(context, historyDatabase, databaseScheduler)
            Timber.i("History Cleared")
        }
        if (userPreferences.clearCookiesExitEnabled) {
            WebUtils.clearCookies()
            Timber.i("Cookies Cleared")
        }
        if (userPreferences.clearWebStorageExitEnabled) {
            WebUtils.clearWebStorage()
            Timber.i("WebStorage Cleared")
        }
    }
}
