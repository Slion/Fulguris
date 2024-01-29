package fulguris.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout

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

}