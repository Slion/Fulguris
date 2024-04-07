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
import slions.pref.BasicPreference


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

        // Setup parent link
        find<BasicPreference>(R.string.pref_key_parent)?.apply {
            if (prefs.isSubDomain) {
                breadcrumb = prefs.parent?.domain ?: ""
                summary = breadcrumb
            } else {
                breadcrumb = getString(R.string.settings_summary_default_domain_settings)
                summary = breadcrumb
            }

            setOnPreferenceClickListener {
                // Set domain setting page to load
                app.domain = prefs.parent?.domain ?: ""
                // Still perform default action
                false
            }
        }


        // Delete this domain settings
        find<Preference>(R.string.pref_key_delete)?.setOnPreferenceClickListener {
            DomainPreferences.delete(domain)
            parentFragmentManager.popBackStack()
            true
        }
    }
}
