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

import fulguris.app
import acr.browser.lightning.R
import acr.browser.lightning.settings.preferences.DomainPreferences
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint



@AndroidEntryPoint
class DomainSettingsFragment : AbstractSettingsFragment() {

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
        return app.domain
    }

    override fun providePreferencesXmlResource() = if (app.domain=="") R.xml.preference_domain_default else R.xml.preference_domain

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // That's the earliest place we can change our preference file as earlier in onCreate the manager has not been created yet
        preferenceManager.sharedPreferencesName = DomainPreferences.name(app.domain)
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Setup link and domain display
        findPreference<Preference>(getString(R.string.pref_key_visit_domain))?.apply {
            summary = app.domain
            val uri = "http://" + app.domain
            intent?.data = Uri.parse(uri)
        }
    }
}
