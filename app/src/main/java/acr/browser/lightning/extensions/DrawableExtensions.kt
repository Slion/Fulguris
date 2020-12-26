package acr.browser.lightning.extensions

import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
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