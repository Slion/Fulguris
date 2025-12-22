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

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.settings.preferences.UserPreferences
import fulguris.userscript.UserScriptManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL
import javax.inject.Inject
import androidx.core.graphics.scale
import androidx.core.graphics.drawable.toDrawable

/**
 * Settings screen for user scripts management.
 * Allows users to view, enable/disable, and delete installed user scripts.
 */
@AndroidEntryPoint
class ExtensionsSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var userScriptManager: UserScriptManager

    private lateinit var scriptsCategory: PreferenceGroup

    override fun titleResourceId(): Int = R.string.settings_title_extensions

    override fun providePreferencesXmlResource(): Int = R.xml.preference_extensions

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Enable/disable userscripts globally
        switchPreference(
            preference = getString(R.string.pref_key_extensions_enabled),
            isChecked = userPreferences.userScriptsEnabled,
            onCheckChange = { enabled ->
                userPreferences.userScriptsEnabled = enabled
            }
        )

        scriptsCategory = findPreference<PreferenceGroup>(getString(R.string.pref_key_extensions_list))!!

        // Populate installed scripts
        populateUserScripts()
    }

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
                    icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_script)
                    order = index + 2
                    // Set fragment for navigation
                    fragment = "fulguris.settings.fragment.ExtensionSettingsFragment"
                    // Add script ID to extras
                    extras.putString("script_id", script.id)
                }
                scriptsCategory.addPreference(scriptPref)

                // Load icon asynchronously if available
                if (script.iconUrl.isNotEmpty()) {
                    loadScriptIcon(script.iconUrl, scriptPref)
                }
            }
        }
    }


    private fun buildScriptSummary(script: fulguris.userscript.UserScript): String {
        val parts = mutableListOf<String>()

        if (script.description.isNotEmpty()) {
            parts.add(script.description)
        }

        if (script.matchUrls.isNotEmpty()) {
            parts.add("Matches: ${script.matchUrls.size} pattern(s)")
        }

        if (script.version.isNotEmpty()) {
            parts.add("v${script.version}")
        }

        return if (parts.isEmpty()) {
            getString(R.string.settings_userscript_no_info)
        } else {
            parts.joinToString(" • ")
        }
    }


    /**
     * Load and set a script icon from a URL asynchronously.
     */
    private fun loadScriptIcon(iconUrl: String, preference: Preference) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Download icon from URL
                val connection = URL(iconUrl).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()

                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        // Convert 24dp to pixels based on screen density
                        val iconSizePx = (24 * resources.displayMetrics.density).toInt()

                        // Scale bitmap to proper size
                        val scaledBitmap = bitmap.scale(iconSizePx, iconSizePx)

                        // Set the icon on the preference
                        val drawable = scaledBitmap.toDrawable(resources)
                        preference.icon = drawable
                        Timber.d("Loaded icon for ${preference.title}")
                    }
                } else {
                    Timber.w("Failed to decode icon bitmap from $iconUrl")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load icon from $iconUrl")
                // Keep default icon on failure
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh list in case scripts were installed from web
        scriptsCategory.removeAll()
        populateUserScripts()
    }
}

