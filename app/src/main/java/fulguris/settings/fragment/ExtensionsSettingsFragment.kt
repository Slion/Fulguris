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

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceGroup
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.settings.preferences.UserPreferences
import fulguris.userscript.UserScriptManager
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen for user scripts management.
 * Allows users to view, enable/disable, and delete installed user scripts.
 */
@AndroidEntryPoint
class ExtensionsSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var userScriptManager: UserScriptManager

    private lateinit var scriptsCategory: PreferenceGroup

    override fun titleResourceId(): Int = R.string.pref_title_extensions

    override fun providePreferencesXmlResource(): Int = R.xml.preference_extensions

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Enable/disable userscripts globally
        switchPreference(
            preference = getString(R.string.pref_key_extensions_enabled),
            isChecked = userPreferences.extensionsEnabled,
            onCheckChange = { enabled ->
                userPreferences.extensionsEnabled = enabled
            }
        )

        scriptsCategory = findPreference<PreferenceGroup>(getString(R.string.pref_key_extensions_list))!!

        // Populate installed scripts
        populateUserScripts()
    }

    /**
     * Populate list of installed scripts.
     */
    private fun populateUserScripts() {
        val scripts = userScriptManager.getInstalledScripts()

        if (scripts.isEmpty()) {
            // Show help text as category summary when no scripts are installed
            scriptsCategory.summary = getString(R.string.pref_category_summary_no_extensions)
        } else {
            // Clear summary when scripts are present
            scriptsCategory.summary = null

            // Add each script as a preference
            scripts.forEachIndexed { index, script ->
                val scriptPref = DetailSwitchPreference(
                    requireContext(),
                    onSwitchChanged = { enabled ->
                        userScriptManager.setScriptEnabled(script.id, enabled)
                    },
                    onPreferenceClicked = null // Let framework handle navigation
                ).apply {
                    key = "script_${script.id}"
                    title = script.name
                    summary = buildScriptSummary(script)
                    isSingleLineTitle = false
                    isChecked = script.enabled
                    icon = script.getDefaultIcon(requireContext())
                    order = index + 2
                    layoutResource = R.layout.detail_switch_preference  // Use custom layout with icon inline with title
                    // Set fragment for navigation
                    fragment = "fulguris.settings.fragment.ExtensionSettingsFragment"
                    // Add script ID to extras
                    extras.putString("script_id", script.id)
                }
                scriptsCategory.addPreference(scriptPref)

                // Load icon asynchronously if available
                if (script.iconUrl.isNotEmpty()) {
                    lifecycleScope.launch {
                        script.loadIcon(requireContext())?.let { drawable ->
                            scriptPref.icon = drawable
                        }
                    }
                }
            }
        }
    }


    private fun buildScriptSummary(script: fulguris.userscript.UserScript): String {
        val parts = mutableListOf<String>()

        if (script.description.isNotEmpty()) {
            parts.add(script.description)
        }

//        if (script.matchUrls.isNotEmpty()) {
//            parts.add("Matches: ${script.matchUrls.size} pattern(s)")
//        }

        if (script.version.isNotEmpty()) {
            parts.add("v${script.version}")
        }

        return if (parts.isEmpty()) {
            getString(R.string.settings_userscript_no_info)
        } else {
            parts.joinToString(" - ")
        }
    }


    override fun onResume() {
        super.onResume()
        // Refresh list in case scripts were installed from web
        scriptsCategory.removeAll()
        populateUserScripts()

        // Flash preference if requested via intent extra
        flashPreferenceIfRequested()
    }
}

