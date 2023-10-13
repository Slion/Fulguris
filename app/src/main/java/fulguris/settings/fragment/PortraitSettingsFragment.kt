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

import fulguris.R
import fulguris.extensions.portraitSharedPreferencesName
import fulguris.settings.preferences.ConfigurationPreferences
import fulguris.settings.preferences.PortraitPreferences
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Portrait settings configuration screen.
 * Notably use the correct shared preferences file rather than the default one.
 */
@AndroidEntryPoint
class PortraitSettingsFragment : ConfigurationSettingsFragment() {

    @Inject internal lateinit var portraitPreferences: PortraitPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_configuration
    override fun configurationPreferences() : ConfigurationPreferences = portraitPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        //injector.inject(this)
        // That's the earliest place we can change our preference file as earlier in onCreate the manager has not been created yet
        preferenceManager.sharedPreferencesName = requireContext().portraitSharedPreferencesName()
        preferenceManager.sharedPreferencesMode = MODE_PRIVATE
        super.onCreatePreferences(savedInstanceState,rootKey)

        // For access through options we show which configuration is currently loaded
        findPreference<Preference>(getString(R.string.pref_key_back))?.summary = getString(R.string.settings_title_portrait)
    }

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_portrait
    }
}
