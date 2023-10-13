package fulguris.extensions

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.core.graphics.drawable.toBitmapOrNull
import java.lang.reflect.Method

/**
 * Tint a drawable with the provided [color], using [BlendModeCompat.SRC_IN].
 */
fun Drawable.tint(@ColorInt color: Int) {
    colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
}

/**
 * Use non public method to extract drawable corresponding to given state.
 * See: https://stackoverflow.com/a/26714363/3969362
 */
fun StateListDrawable.drawableForStateOrNull(aState: Int) : Drawable? {
    return try {
        val getStateDrawableIndex: Method = StateListDrawable::class.java.getMethod("getStateDrawableIndex", IntArray::class.java)
        val getStateDrawable: Method = StateListDrawable::class.java.getMethod("getStateDrawable", Int::class.javaPrimitiveType)
        val index = getStateDrawableIndex.invoke(this, intArrayOf(aState)) as Int
        getStateDrawable.invoke(this, index) as Drawable
    } catch (ex :Exception) {
        null
    }
}

/**
 * Get drawable corresponding to given state or this.
 */
fun StateListDrawable.drawableForState(aState: Int) : Drawable {
    drawableForStateOrNull(aState)?.let{ return it }
    return this
}



/**
 * SL: Duplicated from androidx.core.graphics.drawable to add background color support
 *
 * Return a [Bitmap] representation of this [Drawable].
 *
 * If this instance is a [BitmapDrawable] and the [width], [height], and [config] match, the
 * underlying [Bitmap] instance will be returned directly. If any of those three properties differ
 * then a new [Bitmap] is created. For all other [Drawable] types, a new [Bitmap] is created.
 *
 * @param width Width of the desired bitmap. Defaults to [Drawable.getIntrinsicWidth].
 * @param height Height of the desired bitmap. Defaults to [Drawable.getIntrinsicHeight].
 * @param config Bitmap config of the desired bitmap. Null attempts to use the native config, if
 * any. Defaults to [Config.ARGB_8888] otherwise.
 * @throws IllegalArgumentException if the underlying drawable is a [BitmapDrawable] where
 * [BitmapDrawable.getBitmap] returns `null` or the drawable cannot otherwise be represented as a
 * bitmap
 * @see toBitmapOrNull
 */
fun Drawable.toBitmap(
    @Px width: Int = intrinsicWidth,
    @Px height: Int = intrinsicHeight,
    @ColorInt aBackground: Int = Color.TRANSPARENT,
    config: Config? = null
): Bitmap {
    if (this is BitmapDrawable) {
        if (bitmap == null) {
            // This is slightly better than returning an empty, zero-size bitmap.
            throw IllegalArgumentException("bitmap is null")
        }
        if (config == null || bitmap.config == config) {
            // Fast-path to return original. Bitmap.createScaledBitmap will do this check, but it
            // involves allocation and two jumps into native code so we perform the check ourselves.
            if (width == bitmap.width && height == bitmap.height) {
                return bitmap
            }
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
    }

    val (oldLeft, oldTop, oldRight, oldBottom) = bounds

    val bitmap = Bitmap.createBitmap(width, height, config ?: Config.ARGB_8888)
    bitmap.eraseColor(aBackground)
    setBounds(0, 0, width, height)
    draw(Canvas(bitmap))

    setBounds(oldLeft, oldTop, oldRight, oldBottom)
    return bitmap
}
