package acr.browser.lightning.browser.cleanup

import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.database.history.HistoryDatabase
import acr.browser.lightning.di.DatabaseScheduler
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.WebUtils
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
    override fun cleanUp(webView: WebView?, context: BrowserActivity) {
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
