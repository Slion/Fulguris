package fulguris.extensions

import android.graphics.*
import androidx.core.graphics.createBitmap


/**
 * Creates and returns a new favicon which is the same as the provided favicon but with horizontal
 * and vertical padding of 4dp
 *
 * @return the padded bitmap.
 */
fun Bitmap.pad(): Bitmap {
    // SL: Disabled that funny padding it would cause favicon from frozen tab to look even smaller somehow
    return this;
}

/*
fun Bitmap.pad(): Bitmap = let {
    val padding = Utils.dpToPx(4f)
    val width = it.width + padding
    val height = it.height + padding

    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).apply {
            drawARGB(0x00, 0x00, 0x00, 0x00) // this represents white color
            drawBitmap(it, (padding / 2).toFloat(), (padding / 2).toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}*/

private val desaturatedPaint = Paint().apply {
    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
        setSaturation(0.5f)
    })
}


/**
 * Desaturates a [Bitmap] to 50% grayscale. Note that a new bitmap will be created.
 */
fun Bitmap.desaturate(): Bitmap = createBitmap(width, height).also {
    Canvas(it).drawBitmap(this, 0f, 0f, desaturatedPaint)
}

/**
 * Return a new bitmap containing this bitmap with inverted colors.
 * See: https://gist.github.com/moneytoo/87e3772c821cb1e86415
 */
fun Bitmap.invert(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val matrixGrayscale = ColorMatrix()
    matrixGrayscale.setSaturation(0f)
    val matrixInvert = ColorMatrix()
    matrixInvert.set(
        floatArrayOf(
            -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
            0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
            0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        )
    )
    matrixInvert.preConcat(matrixGrayscale)
    val filter = ColorMatrixColorFilter(matrixInvert)
    paint.colorFilter = filter
    canvas.drawBitmap(this, 0f, 0f, paint)
    return bitmap
}

