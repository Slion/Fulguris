package acr.browser.lightning.utils

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

    val luminance = computeLuminance(r,g,b);

    // Mix with original color?
    //return (luminance<140?0xFFFFFFFF:0xFF000000)

    var res = 0xFF000000;
    if (luminance<140) {
        res = 0xFFFFFFFF
    }

    return res.toInt();
}
