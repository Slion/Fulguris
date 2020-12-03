@file:JvmName("ActivityExtensions")

package acr.browser.lightning.extensions

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar

/**
 * Displays a snackbar to the user with a [StringRes] message.
 *
 * NOTE: If there is an accessibility manager enabled on
 * the device, such as LastPass, then the snackbar animations
 * will not work.
 *
 * @param resource the string resource to display to the user.
 */
fun Activity.snackbar(@StringRes resource: Int) {
    val view = findViewById<View>(android.R.id.content)
    Snackbar.make(view, resource, Snackbar.LENGTH_LONG).show()
}

/**
 * Display a snackbar to the user with a [String] message.
 *
 * @param message the message to display to the user.
 * @see snackbar
 */
fun Activity.snackbar(message: String) {
    val view = findViewById<View>(android.R.id.content)
    Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
}


fun Activity.setStatusBarIconsColor(dark: Boolean)
{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (dark) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }
}
