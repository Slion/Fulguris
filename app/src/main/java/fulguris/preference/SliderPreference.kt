/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.preference

import android.annotation.SuppressLint
import fulguris.R
import fulguris.extensions.px
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import timber.log.Timber
import java.lang.Float.max
import java.lang.Float.min
import java.util.*


/*
* Copyright 2018 The Android Open Source Project
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

/**
 * SL: Taken from Jetpack AndroidX and converted to Kotlin
 * See: [androidx.preference.SeekBarPreference]
 *
 * Preference based on android.preference.SeekBarPreference but uses support preference as a base
 * . It contains a title and a [SeekBar] and an optional SeekBar value [TextView].
 * The actual preference layout is customizable by setting `android:layout` on the
 * preference widget layout or `seekBarPreferenceStyle` attribute.
 *
 *
 * The [SeekBar] within the preference can be defined adjustable or not by setting `adjustable` attribute. If adjustable, the preference will be responsive to DPAD left/right keys.
 * Otherwise, it skips those keys.
 *
 *
 * The [SeekBar] value view can be shown or disabled by setting `showSeekBarValue`
 * attribute to true or false, respectively.
 *
 *
 * Other [SeekBar] specific attributes (e.g. `title, summary, defaultValue, min,
 * max`)
 * can be set directly on the preference widget layout.
 */
class SliderPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.sliderPreferenceStyle, defStyleRes: Int = 0) :
    Preference(context, attrs, defStyleAttr, defStyleRes) {

    var mSeekBarValue = 0F
    //var mMin = 0F
    //private var mMax = 100F
    //private var mSeekBarIncrement = 0F
    var mTrackingTouch = false
    var mSlider: Slider? = null
    //TODO: consider removing this once we support the latest Slider implementation as I think it does support an always visible mode
    // Since MDC 1.6.0 LABEL_VISIBLE should be supported
    private var mSeekBarValueTextView: TextView? = null
    /**
     * Gets whether the [SeekBar] should respond to the left/right keys.
     *
     * @return Whether the [SeekBar] should respond to the left/right keys
     */
    /**
     * Sets whether the [SeekBar] should respond to the left/right keys.
     *
     * @param adjustable Whether the [SeekBar] should respond to the left/right keys
     */
    // Whether the SeekBar should respond to the left/right keys
    var isAdjustable: Boolean = true

    // Whether to show the SeekBar value TextView next to the bar
    private var mShowSeekBarValue: Boolean
    /**
     * Gets whether the [SeekBarPreference] should continuously save the [SeekBar] value
     * while it is being dragged. Note that when the value is true,
     * [Preference.OnPreferenceChangeListener] will be called continuously as well.
     *
     * @return Whether the [SeekBarPreference] should continuously save the [SeekBar]
     * value while it is being dragged
     * @see .setUpdatesContinuously
     */
    /**
     * Sets whether the [SeekBarPreference] should continuously save the [SeekBar] value
     * while it is being dragged.
     *
     * @param updatesContinuously Whether the [SeekBarPreference] should continuously save
     * the [SeekBar] value while it is being dragged
     * @see .getUpdatesContinuously
     */
    // Whether the SeekBarPreference should continuously save the Seekbar value while it is being
    // dragged.
    var updatesContinuously: Boolean = true

    /**
     * Listener reacting to the [SeekBar] changing value by the user
     */
    private val mSeekBarChangeListener = object : Slider.OnSliderTouchListener, Slider.OnChangeListener {
        override fun onValueChange(aSlider: Slider, aValue: Float, fromUser: Boolean) {
            if (fromUser && (updatesContinuously || !mTrackingTouch)) {
                syncValueInternal(aSlider)
            } else {
                // We always want to update the text while the seekbar is being dragged
                updateLabelValue(aValue)
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onStartTrackingTouch(aSlider: Slider) {
            mTrackingTouch = true
        }

        @SuppressLint("RestrictedApi")
        override fun onStopTrackingTouch(aSlider: Slider) {
            mTrackingTouch = false
            //TODO: review logic there
            if (aSlider.value != mSeekBarValue) {
                syncValueInternal(aSlider)
            }
        }
    }

    /**
     * Listener reacting to the user pressing DPAD left/right keys if `adjustable` attribute is set to true; it transfers the key presses to the [SeekBar]
     * to be handled accordingly.
     */
    private val mSeekBarKeyListener: View.OnKeyListener = object : View.OnKeyListener {
        override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) {
                return false
            }
            if (!isAdjustable && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                // Right or left keys are pressed when in non-adjustable mode; Skip the keys.
                return false
            }

            // We don't want to propagate the click keys down to the SeekBar view since it will
            // create the ripple effect for the thumb.
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                return false
            }
            if (mSlider == null) {
                Timber.e("Slider view is null and hence cannot be adjusted.")
                return false
            }
            return mSlider!!.onKeyDown(keyCode, event)
        }
    }

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
        view.itemView.setOnKeyListener(mSeekBarKeyListener)
        mSlider = view.findViewById(R.id.slider) as Slider

        mSeekBarValueTextView = view.findViewById(R.id.seekbar_value) as TextView
        val tv = mSeekBarValueTextView!!

        if (mShowSeekBarValue) {
            tv.visibility = View.VISIBLE
            // Compute minimum width of our text view so that the slider does not resize as the label grows and shrinks
            val bounds = Rect()
            val longestText = formatter.getFormattedValue(valueTo)
            tv.paint.getTextBounds(longestText, 0, longestText.length, bounds)
            // Take into account text width, left and right padding and a small magic constant that we are not exactly sure why it was needed
            // If we are still having problems just increase that constant a bit
            tv.minWidth = bounds.width()  + tv.compoundPaddingLeft + tv.compoundPaddingRight + 2.px
        } else {
            tv.visibility = View.GONE
            mSeekBarValueTextView = null
        }
        if (mSlider == null) {
            Timber.e("Slider view is null in onBindViewHolder.")
            return
        }

        mSlider?.addOnChangeListener(mSeekBarChangeListener)

        mSlider?.let {
            it.valueFrom = valueFrom
            it.valueTo = valueTo
            it.stepSize = stepSize
            it.labelBehavior = labelBehavior
            it.setLabelFormatter(formatter)
            // Make sure our value is in range otherwise Slider will throw an exception
            // That's defensive code which might be useful if your preference range did change
            it.value = min(max(mSeekBarValue, valueFrom),valueTo)
        }


        // TODO: put that back in
        /*
        mSlider!!.setOnSeekBarChangeListener(mSeekBarChangeListener)
        mSlider!!.max = mMax - mMin
        // If the increment is not zero, use that. Otherwise, use the default mKeyProgressIncrement
        // in AbsSeekBar when it's zero. This default increment value is set by AbsSeekBar
        // after calling setMax. That's why it's important to call setKeyProgressIncrement after
        // calling setMax() since setMax() can change the increment value.
        if (mSeekBarIncrement != 0) {
            mSlider!!.keyProgressIncrement = mSeekBarIncrement
        } else {
            mSeekBarIncrement = mSlider!!.keyProgressIncrement
        }
        mSlider!!.progress = mSeekBarValue - mMin
        */
        updateLabelValue(mSeekBarValue)
        mSlider!!.isEnabled = isEnabled
    }

    /**
     * Defensive implementation to support type change
     */
    override fun onSetInitialValue(aDefaultValue: Any?) {

        var defaultValue = 0F
        aDefaultValue?.let {
            if (it is Int) {
                defaultValue = it.toFloat()
            } else if (it is Float) {
                defaultValue = it
            }
        }


        try {
            value = getPersistedFloat(defaultValue)
        } catch (ex: ClassCastException){
            // Well kick in when switching form int to float
            value = defaultValue
        }

    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {


        return a.getFloat(index, 0F)
    }
    /**
     * Gets the lower bound set on the [SeekBar].
     *
     * @return The lower bound set
     */
    /**
     * Sets the lower bound on the [SeekBar].
     *
     * @param min The lower bound to set
     */

    /*
    var min: Float
        get() = mMin
        set(min) {
            var min = min
            if (min > mMax) {
                min = mMax
            }
            if (min != mMin) {
                mMin = min
                notifyChanged()
            }
        }
     */
    /**
     * Returns the amount of increment change via each arrow key click. This value is derived from
     * user's specified increment value if it's not zero. Otherwise, the default value is picked
     * from the default mKeyProgressIncrement value in [android.widget.AbsSeekBar].
     *
     * @return The amount of increment on the [SeekBar] performed after each user's arrow
     * key press
     */
    /**
     * Sets the increment amount on the [SeekBar] for each arrow key press.
     *
     * @param seekBarIncrement The amount to increment or decrement when the user presses an
     * arrow key.
     */
    // TODO: check if we still need this
    /*
    var seekBarIncrement: Float
        get() = mSeekBarIncrement
        set(seekBarIncrement) {
            if (seekBarIncrement != mSeekBarIncrement) {
                mSeekBarIncrement = Math.min(mMax - mMin, Math.abs(seekBarIncrement))
                notifyChanged()
            }
        }
     */
    /**
     * Gets the upper bound set on the [SeekBar].
     *
     * @return The upper bound set
     */
    /**
     * Sets the upper bound on the [SeekBar].
     *
     * @param max The upper bound to set
     */
    /*
    var max: Float
        get() = mMax
        set(max) {
            var max = max
            if (max < mMin) {
                max = mMin
            }
            if (max != mMax) {
                mMax = max
                notifyChanged()
            }
        }
        */

    /**
     * Gets whether the current [SeekBar] value is displayed to the user.
     *
     * @return Whether the current [SeekBar] value is displayed to the user
     * @see .setShowSeekBarValue
     */
    /**
     * Sets whether the current [SeekBar] value is displayed to the user.
     *
     * @param showSeekBarValue Whether the current [SeekBar] value is displayed to the user
     * @see .getShowSeekBarValue
     */
    var showSeekBarValue: Boolean
        get() = mShowSeekBarValue
        set(showSeekBarValue) {
            mShowSeekBarValue = showSeekBarValue
            notifyChanged()
        }

    private fun setValueInternal(seekBarValue: Float, notifyChanged: Boolean) {

        if (seekBarValue != mSeekBarValue) {
            mSeekBarValue = seekBarValue
            updateLabelValue(mSeekBarValue)
            try {
                persistFloat(seekBarValue)
            } catch (ex: ClassCastException) {
                // Possibly converting this key from another type to float
                // Delete existing key
                sharedPreferences!!.edit().remove(key).commit()
                // Then try again
                persistFloat(seekBarValue)
            }

            if (notifyChanged) {
                notifyChanged()
            }
        }
    }
    /**
     * Gets the current progress of the [SeekBar].
     *
     * @return The current progress of the [SeekBar]
     */
    /**
     * Sets the current progress of the [SeekBar].
     *
     * @param seekBarValue The current progress of the [SeekBar]
     */
    var value: Float
        get() = mSeekBarValue
        set(seekBarValue) {
            setValueInternal(seekBarValue, true)
        }

    /**
     * Persist the [SeekBar]'s SeekBar value if callChangeListener returns true, otherwise
     * set the [SeekBar]'s value to the stored value.
     */
    fun  syncValueInternal(seekBar: Slider) {
        if (seekBar.value != mSeekBarValue) {
            if (callChangeListener(seekBar.value)) {
                setValueInternal(seekBar.value, false)
            } else {
                // SL: Looks like value change was cancelled
                seekBar.value = mSeekBarValue
                updateLabelValue(mSeekBarValue)
            }
        }
    }

    /**
     * Attempts to update the TextView label that displays the current value.
     *
     * @param value the value to display next to the [SeekBar]
     */
    fun  updateLabelValue(value: Float) {
        if (mSeekBarValueTextView != null) {
            mSeekBarValueTextView!!.text = formatter.getFormattedValue(value)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        if (isPersistent) {
            // No need to save instance state since it's persistent
            return superState!!
        }

        // Save the instance state
        val myState = SavedState(superState)
        myState.mSeekBarValue = mSeekBarValue
        myState.mMin = valueFrom
        myState.mMax = valueTo
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state?.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state)
            return
        }

        // Restore the instance state
        val myState = state as SavedState
        super.onRestoreInstanceState(myState.getSuperState())
        mSeekBarValue = myState.mSeekBarValue
        valueFrom = myState.mMin
        valueTo = myState.mMax
        notifyChanged()
    }

    /**
     * SavedState, a subclass of [BaseSavedState], will store the state of this preference.
     *
     *
     * It is important to always call through to super methods.
     */
    private class SavedState : BaseSavedState {
        var mSeekBarValue = 0F
        //SL: Not sure what's the use of persisting min and max
        var mMin = 0F
        var mMax = 0F

        internal constructor(source: Parcel) : super(source) {

            // Restore the click counter
            mSeekBarValue = source.readFloat()
            mMin = source.readFloat()
            mMax = source.readFloat()
        }

        internal constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)

            // Save the click counter
            dest.writeFloat(mSeekBarValue)
            dest.writeFloat(mMin)
            dest.writeFloat(mMax)
        }

        /*
        //SL: looks like that's not needed seems that our values are persisted anyway
        companion object {
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }*/
    }

    /**
     * See also [com.google.android.material.slider.BasicLabelFormatter]
     */
    class MostBasicLabelFormatter(aFormat: String="%.2f") : LabelFormatter {
        private val iFormat = aFormat
        override fun getFormattedValue(value: Float): String {
            return String.format(iFormat, value)            
        }
    }

    var valueFrom: Float
    var valueTo: Float
    var stepSize: Float

    /**
     * See [com.google.android.material.slider.LabelFormatter]
     *      LABEL_FLOATING = 0
     *      LABEL_WITHIN_BOUNDS = 1
     *      LABEL_GONE = 2
     */
    var labelBehavior: Int
    // This is use to format on label string
    var formatter: MostBasicLabelFormatter = MostBasicLabelFormatter()

    init {
        // Get Slider attributes from XML
        val sliderAttributes = context.obtainStyledAttributes(attrs, R.styleable.Slider, defStyleAttr, defStyleRes)
        sliderAttributes.let {
            valueFrom = it.getFloat(R.styleable.Slider_android_valueFrom, 0F)
            valueTo = it.getFloat(R.styleable.Slider_android_valueTo, 100F)
            stepSize = it.getFloat(R.styleable.Slider_android_stepSize, 0F)
            labelBehavior = it.getInt(R.styleable.Slider_labelBehavior, 0)
            value = it.getFloat(R.styleable.Slider_android_value, 0F)
            it.recycle()
        }

        // Get SeekBarPreference attributes from XML
        val seekbarAttributes = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference, defStyleAttr, defStyleRes)
        seekbarAttributes.let {
            //seekBarIncrement = a.getInt(R.styleable.SeekBarPreference_seekBarIncrement, 0)
            isAdjustable = it.getBoolean(R.styleable.SeekBarPreference_adjustable, true)
            mShowSeekBarValue = it.getBoolean(R.styleable.SeekBarPreference_showSeekBarValue, false)
            updatesContinuously = it.getBoolean(R.styleable.SeekBarPreference_updatesContinuously, false)
            it.recycle()
        }

        // Get SliderPreference attributes from XML
        val sliderPrefAttributes = context.obtainStyledAttributes(attrs, R.styleable.SliderPreference, defStyleAttr, defStyleRes)
        sliderPrefAttributes.let {
            val format = it.getString(R.styleable.SliderPreference_format)
            if (format!=null) {
                // Create a formatter using format provided in settings
                formatter = MostBasicLabelFormatter(format);
            }
            it.recycle()
        }

    }
}