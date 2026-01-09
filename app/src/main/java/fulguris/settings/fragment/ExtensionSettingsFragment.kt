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
import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.extensions.launch
import fulguris.userscript.UserScriptManager
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen for an individual extension/userscript.
 * Allows users to view details, enable/disable, configure popup notifications, and delete the script.
 */
@AndroidEntryPoint
class ExtensionSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userScriptManager: UserScriptManager

    private lateinit var scriptId: String
    private lateinit var scriptPrefs: SharedPreferences

    override fun titleResourceId(): Int = 0 // Dynamic title based on script name

    override fun providePreferencesXmlResource(): Int = R.xml.preference_extension

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Get script ID from arguments
        scriptId = arguments?.getString(ARG_SCRIPT_ID) ?: run {
            activity?.finish()
            return
        }

        // Load the script
        val script = userScriptManager.getScript(scriptId) ?: run {
            activity?.finish()
            return
        }

        // Use separate preferences file for this script
        val prefsName = "userscript_$scriptId"
        scriptPrefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        preferenceManager.sharedPreferencesName = prefsName


        // Show popup switch
        switchPreference(
            preference = getString(R.string.pref_key_extension_show_popup),
            isChecked = scriptPrefs.getBoolean("show_popup", false),
            onCheckChange = { enabled ->
                scriptPrefs.edit().putBoolean("show_popup", enabled).apply()
            },
            // Not actually using it for now
            isVisible = false
        )

        // Populate script information
        populateScriptInfo(script)

        // View Source button
        clickablePreference(
            preference = getString(R.string.pref_key_extension_view_source),
            onClick = {
                openCodeEditor(scriptId)
                true
            }
        )

        // Delete button
        clickablePreference(
            preference = getString(R.string.pref_key_extension_delete),
            onClick = {
                confirmDeleteScript(script)
                true
            }
        )
    }

    private fun openCodeEditor(scriptId: String) {
        val intent = fulguris.activity.CodeEditorActivity.createIntent(requireContext(), scriptId)
        startActivity(intent)
    }

    private fun populateScriptInfo(script: fulguris.userscript.UserScript) {
        // Name with icon
        findPreference<Preference>(getString(R.string.pref_key_extension_name))?.apply {
            summary = script.name
            isVisible = script.name.isNotEmpty()
            isIconSpaceReserved = true

            // Set default icon
            icon = script.getDefaultIcon(requireContext())

            // Load script icon asynchronously if available
            if (script.iconUrl.isNotEmpty()) {
                lifecycleScope.launch {
                    script.loadIcon(requireContext())?.let { drawable ->
                        icon = drawable
                    }
                }
            }
        }

        // Description
        findPreference<Preference>(getString(R.string.pref_key_extension_description))?.apply {
            summary = script.description.ifEmpty {
                getString(R.string.settings_userscript_no_info)
            }
            isVisible = true
        }

        // Version
        findPreference<Preference>(getString(R.string.pref_key_extension_version))?.apply {
            summary = script.version.ifEmpty {
                getString(R.string.settings_userscript_no_info)
            }
            isVisible = true
        }

        // Author
        findPreference<Preference>(getString(R.string.pref_key_extension_author))?.apply {
            summary = script.author.ifEmpty {
                getString(R.string.settings_userscript_no_info)
            }
            isVisible = true
        }

        // Namespace
        findPreference<Preference>(getString(R.string.pref_key_extension_namespace))?.apply {
            summary = script.namespace.ifEmpty {
                getString(R.string.settings_userscript_no_info)
            }
            isVisible = true
        }

        // Match URLs
        findPreference<Preference>(getString(R.string.pref_key_extension_match_urls))?.apply {
            summary = if (script.matchUrls.isNotEmpty()) {
                script.matchUrls.joinToString("\n") { it }
            } else {
                getString(R.string.settings_userscript_no_info)
            }
            isVisible = true
        }

        // Exclude URLs
        findPreference<Preference>(getString(R.string.pref_key_extension_exclude_urls))?.apply {
            summary = if (script.excludeUrls.isNotEmpty()) {
                script.excludeUrls.joinToString("\n") { it }
            } else {
                getString(R.string.settings_userscript_no_info)
            }
            isVisible = script.excludeUrls.isNotEmpty()
        }
    }


    private fun confirmDeleteScript(script: fulguris.userscript.UserScript) {
        activity?.let { activity ->
            MaterialAlertDialogBuilder(activity).apply {
                setIcon(R.drawable.ic_delete_forever_outline)
                setTitle(R.string.dialog_title_delete_extension)
                setMessage(getString(R.string.dialog_message_delete_extension, script.name))
                setPositiveButton(R.string.action_delete) { _, _ ->
                    userScriptManager.deleteScript(scriptId)
                    // Clear script preferences
                    scriptPrefs.edit().clear().apply()
                    // Pop back stack and update breadcrumbs properly
                    val responsiveParent = parentFragment as? ResponsiveSettingsFragment
                    if (responsiveParent != null) {
                        // Use breadcrumb-aware navigation for responsive settings
                        responsiveParent.popBackStackWithBreadcrumbs()
                    } else {
                        // Fallback for bottom sheet or other non-responsive contexts
                        // Let WebBrowserActivity.onPreferenceStartFragment handle our fragment="back"
                        // Should pop our stack and close the bottom sheet if needed
                    }
                }
                setNegativeButton(R.string.action_cancel, null)
            }.launch()
        }
    }

    companion object {
        private const val ARG_SCRIPT_ID = "script_id"

        /**
         * Create a new instance with the script ID.
         */
        fun newInstance(scriptId: String): ExtensionSettingsFragment {
            return ExtensionSettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SCRIPT_ID, scriptId)
                }
            }
        }

        /**
         * Get the popup preference for a script.
         */
        fun shouldShowPopup(context: Context, scriptId: String): Boolean {
            val prefs = context.getSharedPreferences("userscript_$scriptId", Context.MODE_PRIVATE)
            return prefs.getBoolean("show_popup", false)
        }
    }
}

