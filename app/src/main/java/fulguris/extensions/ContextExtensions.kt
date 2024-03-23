@file:Suppress("NOTHING_TO_INLINE")

package fulguris.extensions

// For comments links

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.google.android.material.resources.MaterialAttributes
import fulguris.R
import fulguris.settings.Config
import timber.log.Timber
import java.util.*


/**
 * Returns the dimension in pixels.
 *
 * @param dimenRes the dimension resource to fetch.
 */
inline fun Context.dimen(@DimenRes dimenRes: Int): Int = resources.getDimensionPixelSize(dimenRes)

/**
 * Returns the [ColorRes] as a [ColorInt]
 */
@ColorInt
inline fun Context.color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

/**
 * Shows a toast with the provided [StringRes].
 */
inline fun Context.toast(@StringRes stringRes: Int) = Toast.makeText(this, stringRes, Toast.LENGTH_SHORT).show()

/**
 *
 */
inline fun Context.toast(string: String) = Toast.makeText(this, string, Toast.LENGTH_SHORT).show()

/**
 * The [LayoutInflater] available on the [Context].
 */
inline val Context.inflater: LayoutInflater
    get() = LayoutInflater.from(this)

/**
 * Gets a drawable from the context.
 */
inline fun Context.drawable(@DrawableRes drawableRes: Int): Drawable = ContextCompat.getDrawable(this, drawableRes)!!

/**
 * The preferred locale of the user.
 */
val Context.preferredLocale: Locale
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        resources.configuration.locale
    }

@ColorInt
fun Context.attrColor( @AttrRes attrColor: Int): Int {
    val typedArray = theme.obtainStyledAttributes(intArrayOf(attrColor))
    val textColor = typedArray.getColor(0, 0)
    typedArray.recycle()
    return textColor
}


/**
 * See [PreferenceManager.getDefaultSharedPreferencesName]
 */
fun Context.portraitSharedPreferencesName(): String {
    return packageName + "_preferences_portrait"
}

/**
 * See [PreferenceManager.getDefaultSharedPreferencesName]
 */
fun Context.landscapeSharedPreferencesName(): String {
    return packageName + "_preferences_landscape"
}

/**
 * Allows us to have rich text and variable interpolation.
 */
fun Context.getText(@StringRes id: Int, vararg args: Any?): CharSequence? {
    return fulguris.extensions.ContextUtils.getText(this, id, *args)
}

/**
 *
 */
@SuppressLint("RestrictedApi")
fun Context.isLightTheme(): Boolean {
    //Timber.v("isLight: $isLight")
    return MaterialAttributes.resolveBoolean(this, R.attr.isLightTheme, true)
}

/**
 *
 */
fun Context.isDarkTheme(): Boolean {
    return !isLightTheme()
}

/**
 *
 */
fun Context.createDefaultFavicon(): Bitmap {
    Timber.v("createDefaultFavicon")
    return getDrawable(R.drawable.ic_web, android.R.attr.state_enabled).toBitmap()
}

/**
 * Load drawable [aId] from resources using theme and apply given [aStates].
 *
 * See drawable state: https://stackoverflow.com/questions/11943795
 */
fun Context.getDrawable(@DrawableRes aId: Int, vararg aStates: Int): Drawable {
    Timber.v("getDrawable")
    return ResourcesCompat.getDrawable(resources, aId, theme)!!.apply{ state = intArrayOf(*aStates) }
}

/**
 *
 */
val Context.isPortrait: Boolean get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

/**
 *
 */
val Context.isLandscape: Boolean get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * Provide the id of the current config
 */
val Context.configId: String get()  {

    val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation?.times(90)
    } else {
        (this as? Activity)?.windowManager?.defaultDisplay?.rotation?.times(90)
    }

    return "${Config.filePrefix}${if (isLandscape) "landscape" else "portrait"}-$rotation-sw${resources.configuration.smallestScreenWidthDp}"
}



