package acr.browser.lightning

import acr.browser.lightning.browser.activity.BrowserActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import javax.inject.Inject

@AndroidEntryPoint
class IncognitoActivity @Inject constructor(): BrowserActivity() {

    override fun provideThemeOverride(): AppTheme? = AppTheme.BLACK

    override fun provideAccentThemeOverride(): AccentTheme = AccentTheme.PINK

    @Suppress("DEPRECATION")
    public override fun updateCookiePreference(): Completable = Completable.fromAction {
        val cookieManager = CookieManager.getInstance()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(this@IncognitoActivity)
        }
        if (Capabilities.FULL_INCOGNITO.isSupported) {
            cookieManager.setAcceptCookie(userPreferences.cookiesEnabled)
        } else {
            cookieManager.setAcceptCookie(userPreferences.incognitoCookiesEnabled)
        }
    }

    @Suppress("RedundantOverride")
    override fun onNewIntent(intent: Intent) {
        handleNewIntent(intent)
        super.onNewIntent(intent)
    }

    @Suppress("RedundantOverride")
    override fun onPause() = super.onPause() // saveOpenTabs();

    override fun updateHistory(title: String?, url: String) = Unit // addItemToHistory(title, url)

    override fun isIncognito() = true

    override fun closeActivity() = closePanels(::closeBrowser)

    companion object {
        /**
         * Creates the intent with which to launch the activity. Adds the reorder to front flag.
         */
        fun createIntent(context: Context, uri: Uri? = null) = Intent(context, IncognitoActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            data = uri
        }
    }
}
