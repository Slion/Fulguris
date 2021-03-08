package acr.browser.lightning.extensions

import acr.browser.lightning.utils.getFilteredColor
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.databinding.BindingAdapter
import androidx.drawerlayout.widget.DrawerLayout
import androidx.palette.graphics.Palette
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
fun View?.removeFromParent() : ViewGroup? = this?.let {
    val parent = it.parent
    (parent as? ViewGroup)?.let { vg ->
        vg.removeView(it)
        return vg
    }
    // Assuming you don't need to explicitly return null in Kotlin
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


/**
 * Analyse the given bitmap and apply a filter if it is too dark for the given theme before loading it in this ImageView
 * Basically turns icons which are too dark for dark theme to white.
 */
fun ImageView.setImageForTheme(bitmap: Bitmap, isDarkTheme: Boolean) {
    // Remove any existing filter
    clearColorFilter()

    if (isDarkTheme) {
        Palette.from(bitmap).generate { palette ->
            // OR with opaque black to remove transparency glitches
            val filteredColor = Color.BLACK or getFilteredColor(bitmap) // OR with opaque black to remove transparency glitches
            val filteredLuminance = ColorUtils.calculateLuminance(filteredColor)
            //val color = Color.BLACK or (it.getVibrantColor(it.getLightVibrantColor(it.getDominantColor(Color.BLACK))))
            val color = Color.BLACK or (palette?.getDominantColor(Color.BLACK) ?: Color.BLACK)
            val luminance = ColorUtils.calculateLuminance(color)
            // Lowered threshold from 0.025 to 0.02 for it to work with bbc.com/future
            // At 0.015 it does not kick in for GitHub
            val threshold = 0.02
            // Use white filter on darkest favicons
            // Filtered luminance  works well enough for theregister.co.uk and github.com while not impacting bbc.c.uk
            // Luminance from dominant color was added to prevent toytowngermany.com from being filtered
            if (luminance < threshold && filteredLuminance < threshold) {
                // Mostly black icon
                //setColorFilter(Color.WHITE)
                // Invert its colors
                // See: https://stackoverflow.com/a/17871384/3969362
                val matrix = floatArrayOf(-1.0f, 0f, 0f, 0f, 255f, 0f, -1.0f, 0f, 0f, 255f, 0f, 0f, -1.0f, 0f, 255f, 0f, 0f, 0f, 1.0f, 0f)
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        }
    }

    setImageBitmap(bitmap)
}

/**
 * To be able to have tooltips working before API level 26
 * See: https://stackoverflow.com/a/61873888/3969362
 */
@BindingAdapter("app:tooltipText")
fun View.bindTooltipText(tooltipText: String) {
    TooltipCompat.setTooltipText(this, tooltipText)
}


fun RectF.scale(factor: Float) {
    val oldWidth = width()
    val oldHeight = height()
    val newWidth = width() * factor
    val newHeight = height() * factor
    left+= (oldWidth - newWidth) / 2f
    right-= (oldWidth - newWidth) / 2f
    top += (oldHeight - newHeight) / 2f
    bottom -= (oldHeight - newHeight) / 2f
}

/**
 *  Dim screen behind a pop-up by the given [aDimAmout].
 *  See: https://stackoverflow.com/a/46711174/3969362
 */
fun PopupWindow.dimBehind(aDimAmout: Float = 0.3f) {
    val container = contentView.rootView
    val context: Context = contentView.context
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val p = container.layoutParams as WindowManager.LayoutParams
    p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
    p.dimAmount = aDimAmout
    wm.updateViewLayout(container, p)
}