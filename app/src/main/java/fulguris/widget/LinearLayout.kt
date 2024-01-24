package fulguris.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PointerIcon
import android.widget.LinearLayout
import timber.log.Timber

/**
 * Allow interception of touch events.
 * Notably used to handle swipe on toolbar without breaking child views interactions.
 */
class LinearLayout: LinearLayout {

    private var interceptor: OnTouchListener?= null

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int): super(context, attrs, defStyle) {
    }

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
    }

    constructor(context: Context): super(context) {
    }

    fun setOnTouchInterceptor(aInterceptor: OnTouchListener?) {
        interceptor = aInterceptor
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {

        if (super.onInterceptTouchEvent(ev)) {
            return true
        }

        return interceptor?.onTouch(this,ev) ?: false
    }

    /**
     *
     */
    override fun onResolvePointerIcon(event: MotionEvent?, pointerIndex: Int): PointerIcon? {

        var icon: PointerIcon? = null //= PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HAND)

        try {
            icon = super.onResolvePointerIcon(event, pointerIndex)
        }
        catch (ex: Exception) {
            Timber.w(ex,"Pointer icon exception")
        }

        return icon
    }

    /**
     *
     */
    override fun dispatchGenericPointerEvent(event: MotionEvent?): Boolean {
        return super.dispatchGenericPointerEvent(event)
    }



    /**
     *
     */
/*
    override fun dispatchTooltipHoverEvent(event: MotionEvent) {

        try {
            super.dispatchTooltipHoverEvent(event)
        }
        catch (ex: Exception) {
            Timber.w(ex,"Tooltip Hover exception")
        }
    }
*/



}