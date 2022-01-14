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
import acr.browser.lightning.di.UserPrefs
import acr.browser.lightning.utils.IntentUtils
import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppsSettingsFragment : AbstractSettingsFragment() {

    @Inject
    @UserPrefs
    internal lateinit var preferences: SharedPreferences

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_apps
    }


    override fun providePreferencesXmlResource() = R.xml.preference_apps

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        //injector.inject(this)

        val intentUtils = IntentUtils(activity as Activity)

        // Get all our preferences for external apps and populate our settings page with them
        val allEntries: Map<String, *> = preferences.all
        for ((key, value) in allEntries) {

            if (key.startsWith(getString(R.string.settings_app_prefix))) {
                //Log.d("map values", key + ": " + value.toString())

                val checkBoxPreference = SwitchPreferenceCompat(context)
                checkBoxPreference.title = key.substring(getString(R.string.settings_app_prefix).length)
                checkBoxPreference.key = key
                checkBoxPreference.isChecked = value as Boolean

                // SL: Can't get the icon color to be proper, we always get that white filter it seems
                // Leave it at that for now
                /*
                val intent = intentUtils.intentForUrl(null, "http://" + checkBoxPreference.title)
                val pm = (activity as Activity).packageManager

                val pkgAppsList: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                if (pkgAppsList.size > 0) {
                    checkBoxPreference.icon = pkgAppsList[0].activityInfo.loadIcon(pm)
                    checkBoxPreference.icon.colorFilter = null
                    DrawableCompat.setTint(checkBoxPreference.icon, Color.RED);
                    DrawableCompat.setTintList(checkBoxPreference.icon, null);
                    //Resources.Theme()
                    //DrawableCompat.applyTheme(checkBoxPreference.icon, )
                }
                */
                preferenceScreen.addPreference(checkBoxPreference)
            }
        }
    }
}
