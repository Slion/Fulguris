package fulguris.activity

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import fulguris.di.configPrefs
import timber.log.Timber

/**
 * Deal with screen insets, cutouts, notches and such
 */
fun WebBrowserActivity.applyWindowInsets(view: View, windowInsets: WindowInsetsCompat) {

    // That's still needed before API 35, it was at least needed for Android 13 on Samsung Galaxy A22
    // See: https://github.com/Slion/Fulguris/issues/686
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val cutoutMode = if (configPrefs.useCutoutArea) LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS else LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        if (window.attributes.layoutInDisplayCutoutMode != cutoutMode) {
            // We did not seem to be able to apply that without restarting the activity,
            window.attributes.layoutInDisplayCutoutMode = cutoutMode
            // This makes sure the newly set cutout mode is applied, without restarting activity I guess
            window.attributes = window.attributes
            // TODO adjust attributes for all our dialog windows
        }
    }

    // On HONOR Magic V2 with gesture navigation in portrait outer screen you get:
    // System insets: Insets{left=0, top=99, right=0, bottom=0}
    val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    Timber.d("System insets: $systemBars")
    //val imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
    val imeHeight = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom

    // On HONOR Magic V2 with gesture navigation in portrait outer screen you get:
    // Gesture insets: Insets{left=45, top=99, right=45, bottom=120}
    val gestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
    Timber.d("Gesture insets: $gestureInsets")

    // On HONOR Magic V2 with gesture navigation in portrait outer screen you get:
    // Mandatory gesture insets: Insets{left=0, top=99, right=0, bottom=120}
    val mandatoryGestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
    Timber.d("Mandatory gesture insets: $mandatoryGestureInsets")

    // On HONOR Magic V2 with gesture navigation in portrait outer screen you get:
    // Cutout insets: Insets{left=0, top=99, right=0, bottom=0}
    val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
    Timber.d("Cutout insets: $cutout")

    // Workout our insets according to configuration
    // From API 35 layoutInDisplayCutoutMode is useless, instead of relying on the root window we need to adjust our layout ourselves
    val insets = windowInsets.getInsets(if (!configPrefs.useCutoutArea) {
        // Exclude display cutout
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    } else {
        // Only exclude system bars
        WindowInsetsCompat.Type.systemBars()
    })

    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        // Don't apply vertical margins here as it would break our drawers status bar color
        // Apply horizontal margin to our root view so that we fill the cutout in on Honor Magic V2
        leftMargin = insets.left //+ gestureInsets.left
        rightMargin = insets.right //+ gestureInsets.right
        // Make sure our UI does not get stuck below the IME virtual keyboard
        // TODO: Do animation synchronization, see: https://developer.android.com/develop/ui/views/layout/sw-keyboard#synchronize-animation
        bottomMargin = imeHeight
    }

    iBinding.uiLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        // Apply vertical margins for status and navigation bar to our UI layout
        // Thus the drawers are still showing below the status bar
        topMargin = insets.top
        bottomMargin = insets.bottom + (gestureInsets.bottom * configPrefs.systemGestureClearance / 100).toInt()


        //leftMargin = gestureInsets.left
        //rightMargin = gestureInsets.right
    }

    iBinding.leftDrawerContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        // Apply vertical margins for status and navigation bar to our drawer content
        // Thus drawer content does not overlap with system UI
        topMargin = insets.top
        bottomMargin = insets.bottom
    }

    iBinding.rightDrawerContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        // Apply vertical margins for status and navigation bar to our drawer content
        // Thus drawer content does not overlap with system UI
        topMargin = insets.top
        bottomMargin = insets.bottom
    }
}
