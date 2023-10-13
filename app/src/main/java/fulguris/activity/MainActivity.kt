package fulguris.activity

import fulguris.R
import android.content.Intent
import android.view.KeyEvent
import android.webkit.CookieManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import javax.inject.Inject

/**
 * Not used in incognito mode
 */
@AndroidEntryPoint
class MainActivity @Inject constructor(): WebBrowserActivity() {


    public override fun updateCookiePreference(): Completable = Completable.fromAction {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(userPreferences.cookiesEnabled)
    }


    override fun onNewIntent(intent: Intent) =
        if (intent.action == INTENT_PANIC_TRIGGER) {
            panicClean()
        } else {
            handleNewIntent(intent)
            super.onNewIntent(intent)
        }

    override fun updateHistory(title: String?, url: String) = addItemToHistory(title, url)

    override fun isIncognito() = false

    // TODO: review how this is used and get rid of it
    override fun closeActivity() {
        performExitCleanUp()
        moveTaskToBack(true)
    }


    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_P ->
                    // Open a new private window
                    if (event.isShiftPressed) {
                        startActivity(IncognitoActivity.createIntent(this))
                        overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)
                        return true
                    }
            }
        }
        return super.dispatchKeyEvent(event)
    }

}
