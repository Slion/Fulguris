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

package fulguris.settings.fragment

import android.content.Context
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import com.google.android.material.materialswitch.MaterialSwitch
import fulguris.R

/**
 * A SwitchPreference that allows separate click handlers for the switch and the preference itself.
 * This enables users to toggle the switch to enable/disable an item, while clicking the preference
 * shows more details.
 *
 * @param context The context
 * @param onSwitchChanged Callback when the switch state changes
 * @param onPreferenceClicked Optional callback when the preference (not the switch) is clicked.
 *        If null and fragment is set, the default fragment navigation will be used.
 */
open class DetailSwitchPreference(
    context: Context,
    private val onSwitchChanged: (Boolean) -> Unit,
    private val onPreferenceClicked: (() -> Unit)? = null
) : SwitchPreference(context) {

    init {
        isSingleLineTitle = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // Make the root item view non-clickable to prevent framework from handling clicks
        // We'll handle them manually in onClick()
        holder.itemView.isClickable = false
        holder.itemView.isFocusable = false

        val switch: MaterialSwitch? = holder.itemView.findViewById(R.id.detail_switch_widget)
        switch?.isChecked = isChecked
        switch?.setOnClickListener {
            val newValue = (it as MaterialSwitch).isChecked
            isChecked = newValue
            onSwitchChanged(newValue)
        }

        // Set up click on the whole item (except the switch)
        holder.itemView.setOnClickListener {
            onClick()
        }
    }

    override fun onClick() {
        // Custom handler - just call it and return
        if (onPreferenceClicked != null) {
            onPreferenceClicked.invoke()
            return
        }

        // For fragment navigation, don't toggle the switch
        // Just call the base Preference.onClick() which handles fragment navigation
        // We skip SwitchPreference.onClick() which would toggle the switch
        if (fragment != null) {
            // Call grandparent's onClick (Preference.onClick not SwitchPreference.onClick)
            // Since we can't call super.super in Kotlin, we manually do what Preference.onClick does:
            // It calls performClick which will handle onPreferenceTreeClick for fragment navigation
            val clickListener = onPreferenceClickListener
            if (clickListener == null || !clickListener.onPreferenceClick(this)) {
                val prefManager = preferenceManager
                val treeListener = prefManager?.onPreferenceTreeClickListener
                treeListener?.onPreferenceTreeClick(this)
            }
            return
        }

        // Default behavior - toggle the switch
        super.onClick()
    }

    // layout resource must be set before onBindViewHolder
    // title and isChecked can't be changed in onBindViewHolder, so also set it now
    override fun onAttached() {
        super.onAttached()
        widgetLayoutResource = R.layout.detail_switch_preference_widget
    }
}

