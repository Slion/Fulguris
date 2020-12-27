@file:JvmName("ActivityExtensions")

package acr.browser.lightning.extensions

import android.annotation.SuppressLint
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
    makeSnackbar(getString(resource)).show()
}

/**
 * Display a snackbar to the user with a [String] message.
 *
 * @param message the message to display to the user.
 * @see snackbar
 */
fun Activity.snackbar(message: String) {
    makeSnackbar(message).show()
}

// Define our snackbar popup duration
const val KDuration: Int = 4000; // Snackbar.LENGTH_LONG

/**
 *
 */
@SuppressLint("WrongConstant")
fun Activity.makeSnackbar(message: String): Snackbar {
    val view = findViewById<View>(android.R.id.content)
    return Snackbar.make(view, message, KDuration)
}

/**
 *
 */
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
