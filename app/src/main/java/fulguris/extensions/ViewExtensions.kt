package fulguris.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import androidx.drawerlayout.widget.DrawerLayout
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import fulguris.R
import fulguris.utils.getFilteredColor
import timber.log.Timber
import java.lang.reflect.Method


/**
 * Tells if this view can scroll vertically.
 * This view may still contain children who can scroll.
 */
fun View.canScrollVertically() = this.let {
    it.canScrollVertically(-1) || it.canScrollVertically(1)
}

/**
 * Removes a view from its parent if it has one.
 * WARNING: This may not set this parent to null instantly if you are using animateLayoutChanges.
 */
fun View.removeFromParent() : ViewGroup? {
        val vg = (parent as? ViewGroup)
        vg?.removeView(this)
        return vg
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
 * Performs an action whenever this view is loosing focus.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onFocusGained(crossinline runnable: () -> Unit) = this?.let {
    it.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
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
 * Performs an action whenever view layout is changing.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onLayoutChange(crossinline runnable: () -> Unit) = this?.apply {
    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int)
        {
            runnable()
        }
    })
}

/**
 * Performs an action whenever view layout size is changing.
 *
 * @param runnable the runnable to run.
 */
    inline fun View?.onSizeChange(crossinline runnable: () -> Unit) = this?.apply {
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val rect = Rect(left, top, right, bottom)
            val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)
            if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
                runnable()
            }
        }
    }

/**
 * That's not actually working for WebView. You only get the top of the web page or blank if the page was scrolled down.
 * See: https://stackoverflow.com/questions/31295237/android-webview-takes-screenshot-only-from-top-of-the-page
 */
/*
fun View.createBitmap(): Bitmap {
    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    //layout(left, top, right, bottom)
    draw(c)
    return b
}
*/

/**
 * Capture a bitmap for this view. Also works with WeView.
 * Though those drawing cache APIs are deprecated they hopefully won't be removed so soon.
 * See: https://stackoverflow.com/a/63529956/3969362
 * [View.setFlags] which is called by [View.setDrawingCacheEnabled] discards calls which are not actually changing flags so we are cool there.
 */
@Suppress("DEPRECATION")
fun View.captureBitmap(): Bitmap {
    val wasDrawingCacheEnabled = isDrawingCacheEnabled
    isDrawingCacheEnabled = true // Enable cache in case it was not already, has not effect if already enabled
    val bitmap: Bitmap = Bitmap.createBitmap(getDrawingCache(false))
    isDrawingCacheEnabled = wasDrawingCacheEnabled // Restore cache state as it was, has not effect if already enabled
    return bitmap;
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
 * If needed it triggers a scroll animation to show the specified item in this [RecyclerView].
 * Unfortunately [LinearSmoothScroller] won't let us define the exact duration of the animation.
 *
 * [aPosition] The index of the item you want to scroll to.
 * [aDurationInMs] The rough duration of the scroll animation in milliseconds.
 * [aOvershot] Specify if you want your scroll animation to overshot in order to put the target item roughly in the middle of this view, as opposed than on the edge.
 * [aSnapMode] See [LinearSmoothScroller].
 *
 * Returns true if a scroll was triggered, false otherwise.
 * Improved from: https://stackoverflow.com/a/65489113/3969362
 */
fun RecyclerView.smoothScrollToPositionEx(aPosition: Int, aDurationInMs: Int = 1000) : Boolean {

    // First of all, check if we should be scrolling at all
    if (scrollState != RecyclerView.SCROLL_STATE_IDLE) {
        Timber.d("Already scrolling, skip it for now")
        return false
    }

    // Can't do it without adapter
    adapter?.let { adaptor ->

        val count = adaptor.itemCount
        var index = aPosition


        //adaptor.getItemViewType()

        val lm = layoutManager as LinearLayoutManager
        // Check if current item is currently visible
        val minIndex = lm.findFirstCompletelyVisibleItemPosition()
        val maxIndex = lm.findLastCompletelyVisibleItemPosition()

        // Check if our item is already visible
        if ( minIndex <= index && index <= maxIndex) {
            Timber.d("No need to scroll")
            return false
        }

        val scrollDown = (index<minIndex) // && !configPrefs.toolbarsBottom
        val scrollFrom = if (scrollDown) minIndex else maxIndex

        val scrollRange = if (scrollFrom>index) scrollFrom - index else index - scrollFrom
        Timber.d("Scroll range: $scrollRange")

        // Trigger our scroll animation
        val smoothScroller = object : LinearSmoothScroller(this.context) {

            // Center on our target item
            // See: https://stackoverflow.com/a/53756296/3969362
            override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }

            // We disabled our speed tweak as it is really tricky to get it right for various use cases
            // Various tabs list variant, number of tabs or screen DPI...
            // The default implementation appears to be a good compromise after all.
            /*
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                // Compute our speed as function of the distance we need to scroll
                var speed = if ((layoutManager as? LinearLayoutManager)?.orientation == LinearLayoutManager.VERTICAL) {
                    aDurationInMs.toFloat() / ((computeVerticalScrollRange().toFloat() / count) * scrollRange)
                } else {
                    aDurationInMs.toFloat() / ((computeHorizontalScrollRange().toFloat() / count) * scrollRange)
                }

                Timber.d("Scroll speed: $speed ms/pixel")

                // Speed is expressed in ms/pixel so in fact min speed is the fastest one and max speed is the slowest one
                val minSpeed = 0.001f    // Fastest
                val maxSpeed = 0.05f     // Slowest
                // Make sure we don't go too fast or too slow, going too fast can break the LinearSmoothScroller and cause endless animation jitter
                if (speed<minSpeed) speed = minSpeed
                if (speed>maxSpeed) speed = maxSpeed

                return speed
            }
            */

        }
        smoothScroller.targetPosition = index
        layoutManager?.startSmoothScroll(smoothScroller)

        return true
    }

    return false
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
    field.set(this,null)
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
        /**TODO: That code was duplicated in [FaviconModel.cacheFaviconForUrl] fix it, somehow */
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
            // Filtered luminance  works well enough for theregister.co.uk and github.com while not impacting bbc.co.uk
            // Luminance from dominant color was added to prevent toytowngermany.com from being filtered
            if (luminance < threshold && filteredLuminance < threshold
                // Needed to exclude white favicon variant provided by GitHub dark web theme
                && palette?.dominantSwatch != null) {
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

/**
 * Crazy workaround to get the virtual keyboard to show, Android FFS
 * See: https://stackoverflow.com/a/7784904/3969362
 */
fun View.simulateTap(x: Float = 0F, y: Float = 0F) {
    dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN , x, y, 0))
    dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP , x, y, 0))
}

/**
 * Set gravity on the given layout parameters to bottom and apply it to this view
 * TODO: Find a way to use a generic to have a single implementation
 */
fun View.setGravityBottom(aParams: LinearLayout.LayoutParams) {
    aParams.gravity = aParams.gravity and Gravity.TOP.inv()
    aParams.gravity = aParams.gravity or Gravity.BOTTOM
    layoutParams = aParams
}

/**
 * Set gravity on the given layout parameters to top and apply it to this view
 */
fun View.setGravityTop(aParams: LinearLayout.LayoutParams) {
    aParams.gravity = aParams.gravity and Gravity.BOTTOM.inv()
    aParams.gravity = aParams.gravity or Gravity.TOP
    layoutParams = aParams
}

/**
 * Set gravity on the given layout parameters to bottom and apply it to this view
 */
fun View.setGravityBottom(aParams: CoordinatorLayout.LayoutParams) {
    aParams.gravity = aParams.gravity and Gravity.TOP.inv()
    aParams.gravity = aParams.gravity or Gravity.BOTTOM
    layoutParams = aParams
}

/**
 * Set gravity on the given layout parameters to top and apply it to this view
 */
fun View.setGravityTop(aParams: CoordinatorLayout.LayoutParams) {
    aParams.gravity = aParams.gravity and Gravity.BOTTOM.inv()
    aParams.gravity = aParams.gravity or Gravity.TOP
    layoutParams = aParams
}


/**
 * Return the first view matching the given type
 */
fun <T : View> View.findViewByType(type: Class<T>, skipThis: Boolean = true): T? {
    if (!skipThis && type.isInstance(this)) {
        return this as T
    }
    if (this is ViewGroup) {
        val viewGroup = this
        for (i in 0 until viewGroup.childCount) {
            val res = viewGroup.getChildAt(i).findViewByType(type, false)
            if (res!=null) {
                return res
            }

        }
    }

    return null;
}


/**
 * Not tested.
 * Taken from: https://github.com/material-components/material-components-android
 */
fun <T : View> View.findViewsByType(type: Class<T>): List<T> {
    val views: MutableList<T> = ArrayList()
    this.findViewsByType( type, views)
    return views
}

/**
 * Not tested.
 * Taken from: https://github.com/material-components/material-components-android
 */
private fun <T : View> View.findViewsByType( type: Class<T>, views: MutableList<T>) {
    if (type.isInstance(this)) {
        views.add(type.cast(this))
    }
    if (this is ViewGroup) {
        val viewGroup = this
        for (i in 0 until viewGroup.childCount) {
            getChildAt(i).findViewsByType(type, views)
        }
    }
}


/**
 *
 */
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

/**
 * Tells if the virtual keyboard is shown.
 * Solution taken from https://stackoverflow.com/a/52171843/3969362
 * Android is silly like this.
 */
@SuppressLint("DiscouragedPrivateApi")
fun InputMethodManager.isVirtualKeyboardVisible() : Boolean {
    return try {
        // Use reflection to access the hidden API we need.
        val method: Method = InputMethodManager::class.java.getDeclaredMethod("getInputMethodWindowVisibleHeight")
        // Assuming if the virtual keyboard height is above zero it is currently being shown.
        ((method.invoke(this) as Int) > 0);
    } catch (ex: Exception) {
        // Something went wrong, let's pretend the virtual keyboard is not showing then.
        // This is defensive and should never happen.
        false
    }
}


/**
 * It seems AlertDialog was never designed to handle screen rotation properly.
 * This can be used to dismiss them when our configuration is changed.
 * This must be a ViewGroup otherwise this function as no effect.
 *
 * [aRunnable] To be run upon configuration change
 */
fun View.onConfigurationChange(aRunnable: () -> Unit) {
    // We add an invisible anonymous View to this View
    // It will execute our runnable upon configuration change
    (this as? ViewGroup)?.apply { addView(object: View(context) {
        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            aRunnable()
        }
    }.apply {
        isVisible = false
        // Could be useful to help understand what's going on when inspecting our views
        id = R.id.onConfigurationChange
    }) }
}

