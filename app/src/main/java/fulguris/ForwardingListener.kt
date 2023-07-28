/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fulguris

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import androidx.annotation.RestrictTo
import androidx.appcompat.view.menu.ShowableListMenu
//import androidx.appcompat.widget.DropDownListView

/**
 * Abstract class that forwards touch events to a [ShowableListMenu].
 *
 * SL: We might be able to use that as a base to implement our drag-to-open
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
abstract class ForwardingListener(
    /** Source view from which events are forwarded.  */
    val mSrc: View
) :
    OnTouchListener,
    View.OnAttachStateChangeListener {
    /** Scaled touch slop, used for detecting movement outside bounds.  */
    private val mScaledTouchSlop: Float

    /** Timeout before disallowing intercept on the source's parent.  */
    private val mTapTimeout: Int

    /** Timeout before accepting a long-press to start forwarding.  */
    private val mLongPressTimeout: Int

    /** Runnable used to prevent conflicts with scrolling parents.  */
    private var mDisallowIntercept: Runnable? = null

    /** Runnable used to trigger forwarding on long-press.  */
    private var mTriggerLongPress: Runnable? = null

    /** Whether this listener is currently forwarding touch events.  */
    private var mForwarding = false

    /** The id of the first pointer down in the current event stream.  */
    private var mActivePointerId = 0

    /**
     * Temporary Matrix instance
     */
    private val mTmpLocation = IntArray(2)

    init {
        mSrc.isLongClickable = true
        mSrc.addOnAttachStateChangeListener(this)
        mScaledTouchSlop = ViewConfiguration.get(mSrc.context).scaledTouchSlop.toFloat()
        mTapTimeout = ViewConfiguration.getTapTimeout()

        // Use a medium-press timeout. Halfway between tap and long-press.
        mLongPressTimeout = (mTapTimeout + ViewConfiguration.getLongPressTimeout()) / 2
    }

    /**
     * Returns the popup to which this listener is forwarding events.
     *
     *
     * Override this to return the correct popup. If the popup is displayed
     * asynchronously, you may also need to override
     * [.onForwardingStopped] to prevent premature cancelation of
     * forwarding.
     *
     * @return the popup to which this listener is forwarding events
     */
    abstract val popup: ShowableListMenu?

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val wasForwarding = mForwarding
        val forwarding: Boolean
        if (wasForwarding) {
            forwarding = onTouchForwarded(event) || !onForwardingStopped()
        } else {
            forwarding = onTouchObserved(event) && onForwardingStarted()
            if (forwarding) {
                // Make sure we cancel any ongoing source event stream.
                val now = SystemClock.uptimeMillis()
                val e = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_CANCEL,
                    0.0f, 0.0f, 0
                )
                mSrc.onTouchEvent(e)
                e.recycle()
            }
        }
        mForwarding = forwarding
        return forwarding || wasForwarding
    }

    override fun onViewAttachedToWindow(v: View) {}
    override fun onViewDetachedFromWindow(v: View) {
        mForwarding = false
        mActivePointerId = MotionEvent.INVALID_POINTER_ID
        if (mDisallowIntercept != null) {
            mSrc.removeCallbacks(mDisallowIntercept)
        }
    }

    /**
     * Called when forwarding would like to start.
     *
     *
     * By default, this will show the popup returned by [.getPopup].
     * It may be overridden to perform another action, like clicking the
     * source view or preparing the popup before showing it.
     *
     * @return true to start forwarding, false otherwise
     */
    protected open fun onForwardingStarted(): Boolean {
        val popup = popup
        // TODO
        /*
        if (popup != null && !popup.isShowing) {
            popup.show()
        }
         */
        return true
    }

    /**
     * Called when forwarding would like to stop.
     *
     *
     * By default, this will dismiss the popup returned by
     * [.getPopup]. It may be overridden to perform some other
     * action.
     *
     * @return true to stop forwarding, false otherwise
     */
    protected fun onForwardingStopped(): Boolean {
        val popup = popup
        // TODO
        /*
        if (popup != null && popup.isShowing) {
            popup.dismiss()
        }

         */
        return true
    }

    /**
     * Observes motion events and determines when to start forwarding.
     *
     * @param srcEvent motion event in source view coordinates
     * @return true to start forwarding motion events, false otherwise
     */
    private fun onTouchObserved(srcEvent: MotionEvent): Boolean {
        val src = mSrc
        if (!src.isEnabled) {
            return false
        }
        val actionMasked = srcEvent.actionMasked
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mActivePointerId = srcEvent.getPointerId(0)
                if (mDisallowIntercept == null) {
                    mDisallowIntercept = DisallowIntercept()
                }
                src.postDelayed(mDisallowIntercept, mTapTimeout.toLong())
                if (mTriggerLongPress == null) {
                    mTriggerLongPress = TriggerLongPress()
                }
                src.postDelayed(mTriggerLongPress, mLongPressTimeout.toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                val activePointerIndex = srcEvent.findPointerIndex(mActivePointerId)
                if (activePointerIndex >= 0) {
                    val x = srcEvent.getX(activePointerIndex)
                    val y = srcEvent.getY(activePointerIndex)

                    // Has the pointer moved outside of the view?
                    if (!pointInView(src, x, y, mScaledTouchSlop)) {
                        clearCallbacks()

                        // Don't let the parent intercept our events.
                        src.parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> clearCallbacks()
        }
        return false
    }

    private fun clearCallbacks() {
        if (mTriggerLongPress != null) {
            mSrc.removeCallbacks(mTriggerLongPress)
        }
        if (mDisallowIntercept != null) {
            mSrc.removeCallbacks(mDisallowIntercept)
        }
    }

    fun onLongPress() {
        clearCallbacks()
        val src = mSrc
        if (!src.isEnabled || src.isLongClickable) {
            // Ignore long-press if the view is disabled or has its own
            // handler.
            return
        }
        if (!onForwardingStarted()) {
            return
        }

        // Don't let the parent intercept our events.
        src.parent.requestDisallowInterceptTouchEvent(true)

        // Make sure we cancel any ongoing source event stream.
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        src.onTouchEvent(e)
        e.recycle()
        mForwarding = true
    }

    /**
     * Handles forwarded motion events and determines when to stop
     * forwarding.
     *
     * @param srcEvent motion event in source view coordinates
     * @return true to continue forwarding motion events, false to cancel
     */
    private fun onTouchForwarded(srcEvent: MotionEvent): Boolean {
        val src = mSrc
        val popup = popup
        // TODO
        /*
        if (popup == null || !popup.isShowing) {
            return false
        }
         */
        // TODO
        /*
        val dst = popup.listView as DropDownListView
        if (dst == null || !dst.isShown) {
            return false
        }
        */


        // Convert event to destination-local coordinates.
        val dstEvent = MotionEvent.obtainNoHistory(srcEvent)
        toGlobalMotionEvent(src, dstEvent)
        // TODO
        //toLocalMotionEvent(dst, dstEvent)

        // Forward converted event to destination view, then recycle it.
        // TODO
        // See what that does here: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:appcompat/appcompat/src/main/java/androidx/appcompat/widget/DropDownListView.java;l=499?q=onForwardedEvent&ss=androidx%2Fplatform%2Fframeworks%2Fsupport
        // Though we could just stick to our plan and just send fake ACTION_HOVER_MOVE events
        //val handled = dst.onForwardedEvent(dstEvent, mActivePointerId)
        dstEvent.recycle()

        // Always cancel forwarding when the touch stream ends.
        val action = srcEvent.actionMasked
        val keepForwarding = (action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL)
        // TODO
        return /*handled &&*/ keepForwarding
    }

    /**
     * Emulates View.toLocalMotionEvent(). This implementation does not handle transformations
     * (scaleX, scaleY, etc).
     */
    private fun toLocalMotionEvent(view: View, event: MotionEvent): Boolean {
        val loc = mTmpLocation
        view.getLocationOnScreen(loc)
        event.offsetLocation(-loc[0].toFloat(), -loc[1].toFloat())
        return true
    }

    /**
     * Emulates View.toGlobalMotionEvent(). This implementation does not handle transformations
     * (scaleX, scaleY, etc).
     */
    private fun toGlobalMotionEvent(view: View, event: MotionEvent): Boolean {
        val loc = mTmpLocation
        view.getLocationOnScreen(loc)
        event.offsetLocation(loc[0].toFloat(), loc[1].toFloat())
        return true
    }

    private inner class DisallowIntercept internal constructor() :
        Runnable {
        override fun run() {
            val parent = mSrc.parent
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private inner class TriggerLongPress internal constructor() :
        Runnable {
        override fun run() {
            onLongPress()
        }
    }

    companion object {
        private fun pointInView(view: View, localX: Float, localY: Float, slop: Float): Boolean {
            return localX >= -slop && localY >= -slop && localX < view.right - view.left + slop && localY < view.bottom - view.top + slop
        }
    }
}