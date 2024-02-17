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
import fulguris.extensions.isLandscape
import fulguris.extensions.isPortrait
import fulguris.settings.preferences.DomainPreferences
import fulguris.settings.preferences.UserPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import fulguris.di.configPrefs
import fulguris.extensions.configId
import fulguris.settings.Config
import fulguris.settings.preferences.ConfigurationCustomPreferences
import javax.inject.Inject

/**
 * Options settings screen.
 * Typically displayed in a bottom sheet from the browser activity.
 */
@AndroidEntryPoint
class OptionsSettingsFragment : AbstractSettingsFragment() {

    // Capture that as it could change through navigating our domain settings hierarchy
    val domain = app.domain

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_options

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState,rootKey)

        setupConfiguration()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setupConfiguration()
    }

    /**
     * Only show the configuration options for the current configuration
     */
    private fun setupConfiguration() {
        // TODO: Just setup a preference using our configPrefs instead of dealing with visibility of those three preferences?
        if (requireContext().configPrefs is ConfigurationCustomPreferences) {
            // Tell our configuration settings fragment to open the proper file
            app.config = Config(requireContext().configId)
            findPreference<Preference>(getString(R.string.pref_key_portrait))?.isVisible =  false
            findPreference<Preference>(getString(R.string.pref_key_landscape))?.isVisible =  false
            findPreference<Preference>(getString(R.string.pref_key_configuration_custom))?.apply {
                isVisible = true
                summary = app.config.name(requireContext())
            }
        } else {
            findPreference<Preference>(getString(R.string.pref_key_portrait))?.isVisible =  requireActivity().isPortrait
            findPreference<Preference>(getString(R.string.pref_key_landscape))?.isVisible =  requireActivity().isLandscape
            findPreference<Preference>(getString(R.string.pref_key_configuration_custom))?.isVisible =  false
        }
    }

    /**
     *
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Timber.d("Domain: ${app.domain}")
        // Don't show domain settings if it does not exists yet
        // Most important so that we don't create them when in incognito mode
        find<Preference>(R.string.pref_key_domain)?.apply{
            isVisible = DomainPreferences.exists(domain)
            setOnPreferenceClickListener {
                app.domain = domain
                false
            }
        }

        // Need when coming back from sub menu after rotation for instance
        setupConfiguration()
    }


    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.options
    }
}
