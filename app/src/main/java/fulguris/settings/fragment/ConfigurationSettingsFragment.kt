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
import fulguris.constant.PrefKeys
import fulguris.device.ScreenSize
import fulguris.settings.preferences.ConfigurationPreferences
import fulguris.settings.preferences.UserPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.TwoStatePreference
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Configuration settings abstract base class
 */
@AndroidEntryPoint
abstract class ConfigurationSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var screenSize: ScreenSize

    override fun providePreferencesXmlResource() = R.xml.preference_configuration

    abstract fun configurationPreferences() : ConfigurationPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        //injector.inject(this)
        super.onCreate(savedInstanceState)


        /*
        // Sample to loop through our preference store
        preferenceManager.sharedPreferences.all.forEach {
            findPreference<Preference>(it.key)
        }
        */

        /*
        // For each preference element on our screen
        val preferenceCount: Int = preferenceManager.preferenceScreen.preferenceCount
        for (i in 0 until preferenceCount) {
            val preference: Preference = preferenceManager.preferenceScreen.getPreference(i)
            val curKey = preference.key
            //if (preference.setDefaultValue())
        }
        */

    }

    /**
     * Reflect actual default in our UI unless user already changed that preference.
     * Needed because we can not rely on XML defaults for configurations since they are instantiated.
     * [aKey] is our preference key.
     * [aValue] is our preference default value.
     */
    fun setDefaultIfNeeded(aKey: String, aValue: Any) {
        if (preferenceManager.sharedPreferences!!.contains(aKey)) {
            // User defined to settings option, no need to initialize it's default then
            Timber.d("User defined: $aKey")
        } else {
            // There is no user defined value for this preference therefore we need to set it's value to the default one
            // TODO: extend this if we need to support new kind of preferences or value types
            if (aValue is Boolean) {
                findPreference<TwoStatePreference>(aKey)?.isChecked = preferenceManager!!.sharedPreferences!!.getBoolean(aKey,aValue)
            } else if (aValue is Int) {
                findPreference<SeekBarPreference>(aKey)?.value = preferenceManager!!.sharedPreferences!!.getInt(aKey,aValue)
            }
            // That is useless at this stage probably only used during construction
            //findPreference<Preference>(it.key)?.setDefaultValue(aValue)
        }

    }

    /**
     * Called from [PreferenceFragmentCompat.onCreate]
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // At this stage our preferences have been created
        // Go through our defaults and set them as needed
        configurationPreferences().getDefaults().forEach {
            setDefaultIfNeeded(it.key,it.value)
        }

        // Handle special case for vertical tab default which is only known at runtime
        setDefaultIfNeeded(PrefKeys.TabBarVertical,!screenSize.isTablet())
        setDefaultIfNeeded(PrefKeys.TabBarInDrawer,!screenSize.isTablet())

    }

}
