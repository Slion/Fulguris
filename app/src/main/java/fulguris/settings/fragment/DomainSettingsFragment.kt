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

import fulguris.app
import fulguris.R
import fulguris.extensions.find
import fulguris.settings.preferences.DomainPreferences
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint

/**
 * Used for both default domain settings and sub-domain settings.
 * We simply load a different XML file for default domain settings.
 */
@AndroidEntryPoint
class DomainSettingsFragment : AbstractSettingsFragment() {

    lateinit var prefs: DomainPreferences

    // Capture that as it could change as we open parent settings
    val domain = app.domain

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        //We use a dynamic string instead, see below
        return -1
    }

    /**
     * See [AbstractSettingsFragment.title]
     */
    override fun title(): String {
        return domain
    }

    /**
     * Select our layout depending if we are showing our default domain settings or not
     */
    override fun providePreferencesXmlResource() = if (domain=="") R.xml.preference_domain_default else R.xml.preference_domain

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        prefs = DomainPreferences(requireContext(),domain)
        // SharedPreferences will automatically create the file when settings are modified
        // That's the earliest place we can change our preference file as earlier in onCreate the manager has not been created yet
        preferenceManager.sharedPreferencesName = DomainPreferences.name(domain)
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Setup link and domain display
        find<Preference>(R.string.pref_key_visit_domain)?.apply {
            summary = domain
            val uri = "http://" + domain
            intent?.data = Uri.parse(uri)
        }

        // Parent link setup is done in onResume() to ensure it's always up-to-date

        // Geolocation permission setup
        setupGeolocationPreferences()

        // Reset default domain settings
        find<Preference>(R.string.pref_key_reset_default_domain)?.setOnPreferenceClickListener {
            DomainPreferences.deleteDefault()
            // Pop back stack and update breadcrumbs properly
            val responsiveParent = parentFragment as? ResponsiveSettingsFragment
            if (responsiveParent != null) {
                // Use breadcrumb-aware navigation for responsive settings
                responsiveParent.popBackStackWithBreadcrumbs()
                true
            } else {
                // Fallback for bottom sheet or other non-responsive contexts
                // Let WebBrowserActivity.onPreferenceStartFragment handle our fragment="back"
                // Should pop our stack and close the bottom sheet if needed
                false
            }
        }

        // Delete this domain settings
        find<Preference>(R.string.pref_key_delete)?.setOnPreferenceClickListener {
            DomainPreferences.delete(domain)
            // Pop back stack and update breadcrumbs properly
            val responsiveParent = parentFragment as? ResponsiveSettingsFragment
            if (responsiveParent != null) {
                // Use breadcrumb-aware navigation for responsive settings
                responsiveParent.popBackStackWithBreadcrumbs()
                true
            } else {
                // Fallback for bottom sheet or other non-responsive contexts
                // Let WebBrowserActivity.onPreferenceStartFragment handle our fragment="back"
                // Should pop our stack and close the bottom sheet if needed
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update parent preference - breadcrumb, summary, and click listener
        // Done here to ensure everything is always up-to-date when returning to this fragment
        find<x.Preference>(R.string.pref_key_parent)?.apply {
            if (prefs.isSubDomain) {
                breadcrumb = prefs.parent?.domain ?: ""
                prefs.parent?.getOverridesSummary(requireContext()) { summary = it }
            } else {
                // The parent of first level domains is the default domain settings
                summary = getString(R.string.pref_title_default_domain_settings)
                // Do not show 'Parent' as title we accessing default domain settings through settings activity
                breadcrumb = getString(R.string.pref_title_default_domain_settings)
            }

            setOnPreferenceClickListener {
                // Set domain setting page to load
                app.domain = prefs.parent?.domain ?: ""
                // Still perform default action
                false
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Clean up domain settings file if no overrides are actually enabled
        // This keeps the domain settings list clean - only showing domains with actual customizations
        prefs.deleteIfNoOverrides()
    }

    /**
     * Setup geolocation permission switch
     * - For default domain: Manages Android system location permissions
     * - For specific domains: Displays WebKit permission status and allows granting/revoking
     */
    private fun setupGeolocationPreferences() {
        val locationSwitch = find<x.SwitchPreference>(R.string.pref_key_location)

        if (prefs.isDefault) {
            // Default domain: Manage Android system permissions
            // The switch is handled normally by the preference framework
            // When disabled, geolocation requests are blocked at the domain level
            return
        }

        // Specific domains: Manage WebKit geolocation permissions
        locationSwitch?.apply {
            // Helper function to update summary based on permission state
            fun updateSummary(granted: Boolean?) {
                activity?.runOnUiThread {
                    summary = when (granted) {
                        true -> getString(R.string.pref_summary_location_granted)
                        false -> getString(R.string.pref_summary_location_denied)
                        null -> getString(R.string.pref_summary_location_ask)
                    }
                    isChecked = granted == true
                }
            }

            // Query current permission status using checkLocationPermission
            prefs.checkLocationPermission { granted ->
                updateSummary(granted)
            }

            // Handle switch changes
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean

                if (!enabled) {
                    // Revoke permission immediately
                    prefs.clearLocationPermission()
                    updateSummary(null) // Will be "Ask" state
                    true
                } else {
                    // Grant permission immediately
                    prefs.grantLocationPermission()
                    updateSummary(true)
                    true
                }
            }
        }
    }
}
