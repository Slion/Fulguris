package acr.browser.lightning.extensions

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Tells if this view can scroll vertically.
 * This view may still contain children who can scroll.
 */
fun View.canScrollVertically() = this.let {
    it.canScrollVertically(-1) || it.canScrollVertically(1)
}

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
 * Performs an action once next time this view is pre drawn.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.doOnPreDraw(crossinline runnable: () -> Unit) = this?.let {
    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw() : Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            runnable()
            return true
        }
    })
}


/**
 * Performs an action whenever this view is loosing focus.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onFocusLost(crossinline runnable: () -> Unit) = this?.let {
    it.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        if (!hasFocus) {
            runnable()
        }
    }
}


/**
 * Performs an action once next time this view layout is changing.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onceOnLayoutChange(crossinline runnable: () -> Unit) = this?.apply {
    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int)
        {
            runnable(); removeOnLayoutChangeListener(this)
        }
    })
}

/**
 * Performs an action once next time a drawer is opened.
 *
 * @param runnable the runnable to run.
 */
inline fun DrawerLayout?.onceOnDrawerOpened(crossinline runnable: () -> Unit) = this?.apply {
    addDrawerListener(object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit
        override fun onDrawerOpened(drawerView: View) { runnable(); removeDrawerListener(this) }
        override fun onDrawerClosed(drawerView: View) = Unit
        override fun onDrawerStateChanged(newState: Int) = Unit
    })
}

/**
 * Performs an action once next time a drawer is closed.
 *
 * @param runnable the runnable to run.
 */
inline fun DrawerLayout?.onceOnDrawerClosed(crossinline runnable: () -> Unit) = this?.apply {
        addDrawerListener(object : DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit
        override fun onDrawerOpened(drawerView: View) = Unit
        override fun onDrawerClosed(drawerView: View) { runnable();removeDrawerListener(this) }
        override fun onDrawerStateChanged(newState: Int) = Unit
    })
}

/**
 * Performs an action once next time a recycler view goes idle.
 *
 * @param runnable the runnable to run.
 */
inline fun RecyclerView?.onceOnScrollStateIdle(crossinline runnable: () -> Unit) = this?.apply {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {if (newState==RecyclerView.SCROLL_STATE_IDLE) {runnable(); removeOnScrollListener(this)}}
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