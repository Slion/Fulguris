package fulguris.activity

import fulguris.AccentTheme
import fulguris.AppTheme
import fulguris.Capabilities
import fulguris.isSupported
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import javax.inject.Inject

@AndroidEntryPoint
class IncognitoActivity @Inject constructor(): WebBrowserActivity() {

    override fun provideThemeOverride(): AppTheme =
        AppTheme.BLACK

    override fun provideAccentThemeOverride(): AccentTheme =
        AccentTheme.PINK

    /**
     *
     */
    public override fun updateCookiePreference(): Completable = Completable.fromAction {
        val cookieManager = CookieManager.getInstance()
        if (Capabilities.FULL_INCOGNITO.isSupported) {
            cookieManager.setAcceptCookie(userPreferences.cookiesEnabled)
        } else {
            cookieManager.setAcceptCookie(userPreferences.incognitoCookiesEnabled)
        }
    }

    /**
     *
     */
    override fun onNewIntent(intent: Intent) {
        handleNewIntent(intent)
        super.onNewIntent(intent)
    }

    /**
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState)
    }

    @Suppress("RedundantOverride")
    override fun onPause() = super.onPause() // saveOpenTabs();

    override fun updateHistory(title: String?, url: String) = Unit // addItemToHistory(title, url)

    override fun isIncognito() = true

    override fun closeActivity() = closeBrowser()

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
