package acr.browser.lightning

import acr.browser.lightning.browser.activity.BrowserActivity
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity @Inject constructor(): BrowserActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Suppress("DEPRECATION")
    public override fun updateCookiePreference(): Completable = Completable.fromAction {
        val cookieManager = CookieManager.getInstance()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(this@MainActivity)
        }
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

    /**
     * Needed to have animations while navigating our settings.
     * Also used to back up our stack.
     * See [PreferenceFragmentCompat.onPreferenceTreeClick].
     */
    @SuppressLint("PrivateResource")
    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, preference: Preference): Boolean {
        val fragmentManager: FragmentManager = caller.parentFragmentManager

        // No actual fragment specified, just a back action
        if (preference.fragment == "back") {
            if (fragmentManager.backStackEntryCount >=1) {
                // Go back to previous fragment if any
                fragmentManager.popBackStack()
            } else {
                // Close our bottom sheet if not previous fragment
                // Needed for the case where we jump directly to a domain settings without going through option
                // Notably happening when security error is set to no and snackbar action is shown
                // Actually should not be needed now that we hide the back button in that case.
                iBottomSheet.dismiss()
            }

            return true
        }

        // Launch specified fragment
        val args: Bundle = preference.extras
        val fragment = fragmentManager.fragmentFactory.instantiate(classLoader, preference.fragment!!)
        fragment.arguments = args
        fragmentManager.beginTransaction()
            // Use standard bottom sheet animations
            .setCustomAnimations(com.google.android.material.R.anim.design_bottom_sheet_slide_in,
                com.google.android.material.R.anim.design_bottom_sheet_slide_out,
                com.google.android.material.R.anim.design_bottom_sheet_slide_in,
                com.google.android.material.R.anim.design_bottom_sheet_slide_out)
            .replace((caller.requireView().parent as View).id, fragment)
            .addToBackStack(null)
            .commit()
        return true;
    }

}
