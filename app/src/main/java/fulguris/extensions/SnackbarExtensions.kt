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
 
package fulguris.extensions

import fulguris.R
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar

/**
 * Adds an extra action button to this snackbar.
 * [aLayoutId] must be a layout with a Button as root element.
 * [aLabel] defines new button label string.
 * [aListener] handles our new button click event.
 */
fun Snackbar.addAction(@LayoutRes aLayoutId: Int, @StringRes aLabel: Int, aListener: View.OnClickListener?) : Snackbar {
    addAction(aLayoutId,context.getString(aLabel),aListener)
    return this;
}

/**
 * Adds an extra action button to this snackbar.
 * [aLayoutId] must be a layout with a Button as root element.
 * [aLabel] defines new button label string.
 * [aListener] handles our new button click event.
 */
fun Snackbar.addAction(@LayoutRes aLayoutId: Int, aLabel: String, aListener: View.OnClickListener?) : Snackbar {
    // Add our button
    val button = LayoutInflater.from(view.context).inflate(aLayoutId, null) as Button
    // Using our special knowledge of the snackbar action button id we can hook our extra button next to it
    view.findViewById<Button>(R.id.snackbar_action).let {
        // Copy layout
        button.layoutParams = it.layoutParams
        // Copy colors
        (button as? Button)?.setTextColor(it.textColors)
        (it.parent as? ViewGroup)?.addView(button)
    }
    button.text = aLabel
    /** Ideally we should use [Snackbar.dispatchDismiss] instead of [Snackbar.dismiss] though that should do for now */
    //extraView.setOnClickListener {this.dispatchDismiss(BaseCallback.DISMISS_EVENT_ACTION); aListener?.onClick(it)}
    button.setOnClickListener {this.dismiss(); aListener?.onClick(it)}
    return this;
}

/**
 * Add an icon to this snackbar.
 * See: https://stackoverflow.com/a/31829381/3969362
 */
fun Snackbar.setIcon(drawable: Drawable): Snackbar {
    return this.apply {
        //setAction(" ") {}
        val textView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        textView.compoundDrawablePadding = context.resources.getDimensionPixelOffset(com.google.android.material.R.dimen.m3_navigation_item_icon_padding);
    }
}

/**
 *  Add an icon to this snackbar.
 */
fun Snackbar.setIcon(@DrawableRes aIcon: Int): Snackbar {
    return this.apply {
        AppCompatResources.getDrawable(context, aIcon)?.let {
            // Apply proper tint so that it works regardless of the theme
            it.setTint(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceInverse, Color.BLACK))
            setIcon(it)
        }
    }
}