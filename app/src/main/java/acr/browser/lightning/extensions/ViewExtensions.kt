package acr.browser.lightning.extensions

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Removes a view from its parent if it has one.
 */
fun View?.removeFromParent() = this?.let {
    val parent = it.parent
    (parent as? ViewGroup)?.removeView(it)
}

/**
 * Performs an action when the view is laid out.
 *
 * @param runnable the runnable to run when the view is laid out.
 */
inline fun View?.doOnLayout(crossinline runnable: () -> Unit) = this?.let {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            runnable()
        }
    })
}



/**
 * Reset Swipe Refresh Layout target.
 * This is needed if you are changing the child scrollable view during the lifetime of your layout.
 * So whenever we change tab we need to do that.
 */
fun SwipeRefreshLayout?.resetTarget() {
    // Get that mTarget private data member and set it as accessible
    val field = SwipeRefreshLayout::class.java.getDeclaredField("mTarget")
    field.isAccessible = true
    // Then reset it
    field.set(this,null);
    // Next time this is doing a layout ensureTarget() will be called and the target set properly again
}