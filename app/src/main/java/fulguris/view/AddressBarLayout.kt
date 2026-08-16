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
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Container for our address bar.
 *
 * In touch mode it stays focusable so it absorbs stray focus while scrolling through tabs in the
 * drawer, otherwise the address field's auto complete popup would come up out of the blue.
 *
 * In non touch mode (D-pad / keyboard / game pad navigation) it must NOT be focusable so that the
 * address field itself receives focus and can be reached with directional navigation.
 *
 * We can't express "focusable only in touch mode" purely in XML because setting
 * focusableInTouchMode also forces the focusable flag, so we toggle it here as the touch mode
 * changes.
 */
class AddressBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ViewTreeObserver.OnTouchModeChangeListener {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnTouchModeChangeListener(this)
        applyTouchMode(isInTouchMode)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnTouchModeChangeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        applyTouchMode(isInTouchMode)
    }

    private fun applyTouchMode(touchMode: Boolean) {
        if (touchMode) {
            // Absorb stray focus while scrolling so the auto complete popup stays hidden.
            isFocusableInTouchMode = true
        } else {
            // Let the address field itself receive directional focus.
            isFocusableInTouchMode = false
            isFocusable = false
        }
    }
}
