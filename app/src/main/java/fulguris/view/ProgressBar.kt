/*
* Copyright 2016 Anthony Restaino
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package fulguris.view

import fulguris.R
import fulguris.utils.BezierEaseInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import java.util.*

class ProgressBar : View {
    private var mProgress = 0
    private var mBidirectionalAnimate = true
    private var mDrawWidth = 0
    var mProgressColor = 0
    private val mAlphaInterpolator: Interpolator = LinearInterpolator()
    private val mProgressInterpolator: Interpolator = BezierEaseInterpolator()
    private val mAnimationQueue: Queue<Animation> = ArrayDeque()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    /**
     * Initialize the AnimatedProgressBar
     *
     * @param context is the context passed by the constructor
     * @param attrs   is the attribute set passed by the constructor
     */
    private fun init(context: Context, attrs: AttributeSet?) {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.AnimatedProgressBar, 0, 0)
        try {
            // Retrieve the style of the progress bar that the user hopefully set
            val DEFAULT_PROGRESS_COLOR = Color.RED
            mProgressColor = array.getColor(R.styleable.AnimatedProgressBar_progressColor, DEFAULT_PROGRESS_COLOR)
            mBidirectionalAnimate = array.getBoolean(R.styleable.AnimatedProgressBar_bidirectionalAnimate, false)
        } finally {
            array.recycle()
        }
    }

    /**
     * Returns the current progress value between 0 and 100
     *
     * @return progress of the view
     */// animate the width change// calculate amount the width has to change// we don't need to go any farther if the progress is unchanged
    // save the progress
// if the we only animate the view in one direction
    // then reset the view width if it is less than the
    // previous progress
// progress cannot be less than 0
    // Set the drawing bounds for the ProgressBar
// progress cannot be greater than 100
    /**
     * sets the progress as an integer value between 0 and 100.
     * Values above or below that interval will be adjusted to their
     * nearest value within the interval, i.e. setting a value of 150 will have
     * the effect of setting the progress to 100. You cannot trick us.
     *
     * @param progress an integer between 0 and 100
     */
    var progress: Int
        get() = mProgress
        set(progress) {
            var progress = progress
            if (progress > 100) {       // progress cannot be greater than 100
                progress = 100
            } else if (progress < 0) {  // progress cannot be less than 0
                progress = 0
            }
            if (alpha < 1.0f) {
                fadeIn()
            }
            val mWidth = measuredWidth
            // Set the drawing bounds for the ProgressBar
            mRect.left = 0
            mRect.top = 0
            mRect.bottom = bottom - top
            if (progress < mProgress && !mBidirectionalAnimate) {   // if the we only animate the view in one direction
                // then reset the view width if it is less than the
                // previous progress
                mDrawWidth = 0
            } else if (progress == mProgress) {     // we don't need to go any farther if the progress is unchanged
                if (progress == 100) {
                    fadeOut()
                }
            }
            mProgress = progress // save the progress
            val deltaWidth = mWidth * mProgress / 100 - mDrawWidth // calculate amount the width has to change
            if (deltaWidth != 0) {
                animateView(mDrawWidth, deltaWidth, mWidth) // animate the width change
            }
        }

    private val mPaint = Paint()
    private val mRect = Rect()
    override fun onDraw(canvas: Canvas) {
        mPaint.color = mProgressColor
        mPaint.strokeWidth = 10f
        mRect.right = mRect.left + mDrawWidth
        canvas.drawRect(mRect, mPaint)
    }

    /**
     * private method used to create and run the animation used to change the progress
     *
     * @param initialWidth is the width at which the progress starts at
     * @param deltaWidth   is the amount by which the width of the progress view will change
     * @param maxWidth     is the maximum width (total width of the view)
     */
    private fun animateView(initialWidth: Int, deltaWidth: Int, maxWidth: Int) {
        val fill: Animation = ProgressAnimation(initialWidth, deltaWidth, maxWidth)
        fill.duration = PROGRESS_DURATION
        fill.interpolator = mProgressInterpolator
        if (!mAnimationQueue.isEmpty()) {
            mAnimationQueue.add(fill)
        } else {
            startAnimation(fill)
        }
    }

    /**
     * fades in the progress bar
     */
    private fun fadeIn() {
        animate().alpha(1f)
                .setDuration(ALPHA_DURATION)
                .setInterpolator(mAlphaInterpolator)
                .start()
    }

    /**
     * fades out the progress bar
     */
    private fun fadeOut() {
        animate().alpha(0f)
                .setDuration(ALPHA_DURATION)
                .setInterpolator(mAlphaInterpolator)
                .start()
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        var state: Parcelable? = state
        if (state is Bundle) {
            val bundle = state
            mProgress = bundle.getInt("progressState")
            state = bundle.getParcelable("instanceState")
        }
        super.onRestoreInstanceState(state)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("instanceState", super.onSaveInstanceState())
        bundle.putInt("progressState", mProgress)
        return bundle
    }

    private inner class ProgressAnimation internal constructor(private val mInitialWidth: Int, private val mDeltaWidth: Int, private val mMaxWidth: Int) : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val width = mInitialWidth + (mDeltaWidth * interpolatedTime).toInt()
            if (width <= mMaxWidth) {
                mDrawWidth = width
                invalidate()
            }
            if (Math.abs(1.0f - interpolatedTime) < 0.00001) {
                if (mProgress >= 100) {
                    fadeOut()
                }
                if (!mAnimationQueue.isEmpty()) {
                    startAnimation(mAnimationQueue.poll())
                }
            }
        }

        override fun willChangeBounds(): Boolean {
            return false
        }

        override fun willChangeTransformationMatrix(): Boolean {
            return false
        }

    }

    companion object {
        private const val PROGRESS_DURATION: Long = 500
        private const val ALPHA_DURATION: Long = 200
    }
}