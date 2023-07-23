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

package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BrowserApp
import acr.browser.lightning.R
import acr.browser.lightning.extensions.portraitSharedPreferencesName
import acr.browser.lightning.settings.preferences.ConfigurationPreferences
import acr.browser.lightning.settings.preferences.DomainPreferences
import acr.browser.lightning.settings.preferences.PortraitPreferences
import acr.browser.lightning.settings.preferences.UserPreferences
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Options settings screen.
 * Typically displayed in a bottom sheet.
 */
@AndroidEntryPoint
class OptionsSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_options

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState,rootKey)

        //Timber.d("Domain: ${BrowserApp.instance.domain}")
        // Don't show domain settings if it does not exists yet
        // Most important so that we don't create them when in incognito mode
        findPreference<Preference>(getString(R.string.pref_key_domain))?.isVisible = DomainPreferences.exists(BrowserApp.instance.domain)
    }

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.options
    }
}
