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

package fulguris.view

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.ViewPager

/**
 * A pager which can adjust its height according to every page content.
 * Taken from: https://stackoverflow.com/a/50975140/3969362
 */
class DynamicHeightViewPager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {

    override fun onMeasure(aWidthMeasureSpec: Int, aHeightMeasureSpec: Int) {
        var heightMeasureSpec = aHeightMeasureSpec

        var height = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(aWidthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val h = child.measuredHeight
            if (h > height) height = h
        }

        if (height != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height + paddingBottom + paddingTop, MeasureSpec.EXACTLY)
        }

        if (MeasureSpec.getSize(heightMeasureSpec)<MeasureSpec.getSize(aHeightMeasureSpec)) {
            // Only use our new specs if smaller to avoid break scrolling in dialogs
            super.onMeasure(aWidthMeasureSpec, heightMeasureSpec)
        } else {
            super.onMeasure(aWidthMeasureSpec, aHeightMeasureSpec)
        }
    }}