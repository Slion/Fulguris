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

import acr.browser.lightning.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference

/**
 * Sponsorship settings for non Google Play Store variants.
 * We just redirect users to Google Play Store if they want to sponsor us.
 */
abstract class RedirectSponsorshipSettingsFragment : AbstractSettingsFragment() {

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_contribute
    }

    override fun providePreferencesXmlResource() = R.xml.preference_sponsorship

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        addCategoryContribute()
        addPreferenceLinkToGooglePlayStoreFiveStarsReview()
        addPreferenceShareLink()
        addPreferenceLinkToCrowdin()
        addPreferenceLinkToGitHubSponsor()
        addPreferenceLinkToGooglePlayStore()
    }

    /**
     * Add a preference that opens up our play store page.
     */
    private fun addPreferenceLinkToGooglePlayStore() {
        // We invite user to installer our Google Play Store release
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.title = resources.getString(R.string.pref_title_no_sponsorship)
        pref.summary = resources.getString(R.string.pref_summary_no_sponsorship)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_arrow, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open up Fulguris play store page
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.slions.fulguris.full.playstore")))
            true
        }
        preferenceScreen.addPreference(pref)
    }






}
