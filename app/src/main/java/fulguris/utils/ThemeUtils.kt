/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.utils

import fulguris.R
import android.annotation.TargetApi
import android.content.Context
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat


object ThemeUtils {

    private val sTypedValue = TypedValue()

    /**
     * Gets the primary color of the current theme.
     *
     * @param context the context to get the theme from.
     * @return the primary color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getPrimaryColor(context: Context): Int {
        return getColor(context, R.attr.colorPrimary)
    }

    /**
     * Gets the primary dark color of the current theme.
     *
     * @param context the context to get the theme from.
     * @return the primary dark color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getPrimaryColorDark(context: Context): Int {
        return getColor(context, R.attr.colorPrimaryDark)
    }

    /**
     * Gets the surface color of the current theme.
     *
     * @param context the context to get the theme from.
     * @return the surface color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getSurfaceColor(context: Context): Int {
        return getColor(context, R.attr.colorSurface)
    }

    /**
     * Gets on surface color of the current theme.
     *
     * @param context the context to get the theme from.
     * @return the on surface color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getOnSurfaceColor(context: Context): Int {
        return getColor(context, R.attr.colorOnSurface)
    }


    /**
     * Get background color from current theme.
     * Though in fact this should be the same as surface color anyway.
     * I believe that attribute is mostly used for backward compatibility issues.
     *
     * @param context the context to get the theme from.
     * @return the background color as defined in the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getBackgroundColor(context: Context): Int {
        return getColor(context, android.R.attr.colorBackground)
    }


    /**
     * Gets the accent color of the current theme.
     *
     * @param context the context to get the theme from.
     * @return the accent color of the current theme.
     */
    @JvmStatic
    @ColorInt
    fun getAccentColor(context: Context): Int {
        return getColor(context, R.attr.colorAccent)
    }

    /**
     * Gets the color of the status bar as set in styles
     * for the current theme.
     *
     * @param context the context to get the theme from.
     * @return the status bar color of the current theme.
     */
    @JvmStatic
    @ColorInt
    @TargetApi(21)
    fun getStatusBarColor(context: Context): Int {
        return getColor(context, android.R.attr.statusBarColor)
    }

    /**
     * Gets the color attribute from the current theme.
     *
     * @param context  the context to get the theme from.
     * @param resource the color attribute resource.
     * @return the color for the given attribute.
     */
    @JvmStatic
    @ColorInt
    fun getColor(context: Context, @AttrRes resource: Int): Int {
        val a: TypedArray = context.obtainStyledAttributes(sTypedValue.data, intArrayOf(resource))
        val color = a.getColor(0, 0)
        a.recycle()
        return color
    }

    /**
     * Gets the icon color for the light theme.
     *
     * @param context the context to use.
     * @return the color of the icon.
     */
    @JvmStatic
    @ColorInt
    private fun getIconLightThemeColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.icon_light_theme)
    }

    /**
     * Gets the icon color for the dark theme.
     *
     * @param context the context to use.
     * @return the color of the icon.
     */
    @JvmStatic
    @ColorInt
    private fun getIconDarkThemeColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.icon_dark_theme)
    }

    /**
     * Gets the color icon for the light or
     * dark theme.
     *
     * @param context the context to use.
     * @param dark    true for the dark theme,
     * false for the light theme.
     * @return the color of the icon.
     */
    @JvmStatic
    @ColorInt
    fun getIconThemeColor(context: Context, dark: Boolean): Int {
        return if (dark) getIconDarkThemeColor(context) else getIconLightThemeColor(context)
    }

    @JvmStatic
    private fun getVectorDrawable(context: Context, drawableId: Int): Drawable {
        var drawable = ContextCompat.getDrawable(context, drawableId)
        fulguris.utils.Preconditions.checkNonNull(drawable)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable!!).mutate()
        }
        return drawable!!
    }

    // http://stackoverflow.com/a/38244327/1499541
    @JvmStatic
    fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable: Drawable = getVectorDrawable(context, drawableId)
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Gets the icon with an applied color filter
     * for the correct theme.
     *
     * @param context the context to use.
     * @param res     the drawable resource to use.
     * @param dark    true for icon suitable for use with a dark theme,
     * false for icon suitable for use with a light theme.
     * @return a themed icon.
     */
    @JvmStatic
    fun createThemedBitmap(context: Context, @DrawableRes res: Int, dark: Boolean): Bitmap {
        val color: Int = if (dark) getIconDarkThemeColor(context) else getIconLightThemeColor(context)
        val sourceBitmap: Bitmap = getBitmapFromVectorDrawable(context, res)
        val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height,
                Bitmap.Config.ARGB_8888)
        val p = Paint()
        val filter: ColorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        p.colorFilter = filter
        val canvas = Canvas(resultBitmap)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, p)
        sourceBitmap.recycle()
        return resultBitmap
    }

    /**
     * Gets the edit text text color for the current theme.
     *
     * @param context the context to use.
     * @return a text color.
     */
    @ColorInt
    @JvmStatic
    fun getTextColor(context: Context): Int {
        return getColor(context, android.R.attr.editTextColor)
    }


    /**
     *
     */
    @JvmStatic
    fun getSearchBarColor(requestedColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(requestedColor)
        return if (luminance > 0.9) {
            // Too bright, make it darker then
            fulguris.utils.DrawableUtils.mixColor(0.20f, requestedColor, Color.BLACK)
        } else {
            // Make search text field background lighter
            fulguris.utils.DrawableUtils.mixColor(0.20f, requestedColor, Color.WHITE)
        }
    }

    /**
     *
     */
    @JvmStatic
    fun getSearchBarFocusedColor(requestedColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(requestedColor)
        return if (luminance > 0.9) {
            // Too bright, make it darker then
            fulguris.utils.DrawableUtils.mixColor(0.35f, requestedColor, Color.BLACK)
        } else {
            // Make search text field background lighter
            fulguris.utils.DrawableUtils.mixColor(0.35f, requestedColor, Color.WHITE)
        }
    }

}


