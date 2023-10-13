package fulguris.extensions
import android.content.res.Resources

/**
 * Convert float from dp to px
 */
val Float.px: Float get() = (this * Resources.getSystem().displayMetrics.density)

/**
 * Covert float from px to dp
 */
val Float.dp: Float get() = (this / Resources.getSystem().displayMetrics.density)