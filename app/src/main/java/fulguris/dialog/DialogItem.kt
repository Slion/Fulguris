package fulguris.dialog

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.StringRes


/**
 * Our dialog item features an [icon], a [title], a secondary [text], an [onClick] callback
 * and a [show] boolean condition to conveniently control its visibility.
 */
class DialogItem(
    val icon: Drawable? = null,
    @param:ColorInt
    val colorTint: Int? = null,
    @param:StringRes
    val title: Int,
    val text: String? = null,
    val show: Boolean = true,
    // TODO: return boolean that tells if the dialog needs to be dismissed
    private val onClick: () -> Unit
) {
    fun onClick() = onClick.invoke()
}
