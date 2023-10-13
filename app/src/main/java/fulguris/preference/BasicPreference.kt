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


import fulguris.R
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import timber.log.Timber

/**
 * Basic preference adding the following features:
 * - Explicit breadcrumb
 * - Title and summary swap
 * - Multiple line summary
 *
 * See: https://stackoverflow.com/questions/6729484/android-preference-summary-how-to-set-3-lines-in-summary
 */
class BasicPreference :
    Preference {
    constructor(ctx: Context, attrs: AttributeSet?, defStyle: Int) : super(ctx, attrs, defStyle) {
        //Timber.d("constructor 3")
        construct(ctx,attrs)
    }
    constructor(ctx: Context, attrs: AttributeSet?) : super(ctx, attrs) {
        //Timber.d("constructor 2")
        construct(ctx,attrs)
    }
    constructor(ctx: Context) : super(ctx) {
        //Timber.d("constructor 1")
    }

    @SuppressLint("RestrictedApi")
    fun construct(ctx: Context, attrs: AttributeSet?) {
        //Timber.d("construct")
        val a = context.obtainStyledAttributes(attrs, R.styleable.BasicPreference)
        breadcrumb = TypedArrayUtils.getText(a, R.styleable.BasicPreference_breadcrumb,0) ?: ""
        a.recycle()
        if (breadcrumb.isEmpty()) {
            breadcrumb = title ?: summary ?: ""
        }
    }


    var breadcrumb: CharSequence = ""

    // Use this to swap texts of title and summary
    // Needed as preferences can only be sorted by titles but wanted them sorted by summary
    var swapTitleSummary = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        val title = holder.findViewById(android.R.id.title) as TextView
        // Enable multiple line support
        summary.isSingleLine = false
        summary.maxLines = 10 // Just need to be high enough I guess

        if (swapTitleSummary) {
            // Just do it
            val tt = title.text
            title.text = summary.text
            summary.text = tt
        }

    }
}