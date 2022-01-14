/*
 * Copyright © 2020-2021 Stéphane Lenclud
 */

@file:JvmName("ActivityExtensions")

package acr.browser.lightning.extensions

import acr.browser.lightning.R
import acr.browser.lightning.utils.Utils
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar


// Define our snackbar popup duration
const val KDuration: Int = 4000; // Snackbar.LENGTH_LONG

/**
 * Displays a snackbar to the user with a [StringRes] message.
 *
 * NOTE: If there is an accessibility manager enabled on
 * the device, such as LastPass, then the snackbar animations
 * will not work.
 *
 * @param resource the string resource to display to the user.
 */
fun Activity.snackbar(@StringRes resource: Int, aGravity: Int = Gravity.BOTTOM) {
    makeSnackbar(getString(resource), KDuration, aGravity).show()
}

/**
 * Display a snackbar to the user with a [String] message.
 *
 * @param message the message to display to the user.
 * @see snackbar
 */
fun Activity.snackbar(message: String, aGravity: Int = Gravity.BOTTOM) {
    makeSnackbar(message, KDuration, aGravity).show()
}

/**
 *
 */
@SuppressLint("WrongConstant")
fun Activity.makeSnackbar(message: String, aDuration: Int, aGravity: Int): Snackbar {
    var view = findViewById<View>(R.id.web_view_frame)
    if (view == null) {
        // We won't use gravity and we provide compatibility with previous implementation
        view = findViewById<View>(android.R.id.content)
        return Snackbar.make(view, message, aDuration)
    } else {
        // Apply specified gravity before showing snackbar
        val snackbar = Snackbar.make(view, message, aDuration)
        //snackbar.setAnchorView(R.id.web_view_frame)
        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
        val params = snackbar.view.layoutParams as CoordinatorLayout.LayoutParams
        params.gravity = aGravity
        if (aGravity==Gravity.TOP) {
            // Move snackbar away from status bar
            // That one works well it seems
            //params.topMargin = Utils.dpToPx(90F)
        } else {
            // Make sure it is above rounded corner
            // Ain't working on F(x)tec Pro1, weird...
            //params.bottomMargin = Utils.dpToPx(90F)
        }
        snackbar.view.layoutParams = params

        return snackbar;
    }
}

/**
 *
 */
fun Window.setStatusBarIconsColor(dark: Boolean)
{
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (dark) {
            decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }
}
