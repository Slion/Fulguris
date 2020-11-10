package acr.browser.lightning.utils

import android.graphics.Bitmap

// TODO: use [ColorUtils.calculateLuminance] instead
fun computeLuminance(r: Int, g: Int, b: Int) : Float {
    return (0.2126f * r + 0.7152f * g + 0.0722f * b);
}

fun foregroundColorFromBackgroundColor(color: Int) :Int {
    // The following needed newer API level so we implement it here instead
    //val c: Color = Color.valueOf(color);

    //
    val a = (color shr 24 and 0xff)
    val r = (color shr 16 and 0xff)
    val g = (color shr 8 and 0xff)
    val b = (color and 0xff)

    //val c: Color = Color.argb(a,r,g,b);

    val luminance = computeLuminance(r, g, b);

    // Mix with original color?
    //return (luminance<140?0xFFFFFFFF:0xFF000000)

    var res = 0xFF000000;
    if (luminance<140) {
        res = 0xFFFFFFFF
    }

    return res.toInt();
}

/**
 * Scale given bitmap to one pixel to get a rough average color
 */
fun getFilteredColor(bitmap: Bitmap?): Int {
    val newBitmap = Bitmap.createScaledBitmap(bitmap!!, 1, 1, false)
    val color = newBitmap.getPixel(0, 0)
    newBitmap.recycle()
    return color
}

/**
 *
 */
fun htmlColor(aColor: Int) : String {
    // Not sure why that ain't working the same with alpha, probably bits offset issues
    return java.lang.String.format("#%06X", 0xFFFFFF and aColor)
}