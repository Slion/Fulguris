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

package acr.browser.lightning.settings.preferences

import fulguris.BrowserApp
import acr.browser.lightning.R
import acr.browser.lightning.extensions.reverseDomainName
import acr.browser.lightning.settings.NoYesAsk
import acr.browser.lightning.settings.preferences.delegates.*
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.io.File

/**
 * Domain preferences
 *
 * TODO: Should we get the default value from the default preferences somehow?
 */
class DomainPreferences constructor(
    val context: Context,
    val domain: String = ""
) {

    // Preferences for this domain
    val preferences: SharedPreferences = context.getSharedPreferences(name(domain),MODE_PRIVATE)
    // Default domain preferences
    val default : SharedPreferences = context.getSharedPreferences(name(""),MODE_PRIVATE)

    /**
     * Used to mark if we have already seen this domain
     */
    var knownDomain by preferences.booleanPreference(R.string.pref_key_known_domain, false)

    /**
     * Used to distinguish main domain from resource domain
     */
    var entryPoint by preferences.booleanPreference(R.string.pref_key_entry_point, false)
    
    /**
     * True if dark mode should be enabled by default for this domain, false otherwise.
     */
    var darkModeOverride by preferences.booleanPreference(R.string.pref_key_dark_mode_override, false)
    var darkModeLocal by preferences.booleanPreference(R.string.pref_key_dark_mode, false)
    var darkModeDefault by default.booleanPreference(R.string.pref_key_dark_mode, false)
    val darkMode: Boolean get() { return if (isDefault || !darkModeOverride) { darkModeDefault } else { darkModeLocal } }

    /**
     * Define what to do when a third-party app is available:
     * - Yes: Launch the app
     * - No: Do not launch the app
     * - Ask: Ask the user whether or not to launch the app
     */
    var launchAppOverride by preferences.booleanPreference(R.string.pref_key_launch_app_override, false)
    var launchAppLocal by preferences.enumPreference(R.string.pref_key_launch_app, NoYesAsk.ASK)
    var launchAppDefault by default.enumPreference(R.string.pref_key_launch_app, NoYesAsk.ASK)
    val launchApp: NoYesAsk get() { return if (isDefault || !launchAppOverride) { launchAppDefault } else { launchAppLocal } }

    /**
     * Is this the default domain settings?
     */
    val isDefault: Boolean get() = domain==""


    companion object {

        /**
         * Provide name of shared preferences file for the specified domain.
         * See [PreferenceManager.getDefaultSharedPreferencesName]
         */
        fun name(aDomain: String): String {
            return "$prefix${aDomain.reverseDomainName}"
        }

        /**
         * Provide name of shared preferences file for the specified domain.
         * See [PreferenceManager.getDefaultSharedPreferencesName]
         */
        val prefix: String
            get() = "[Domain]" // $packageName

        /**
         *
         */
        fun exists(domain: String) : Boolean {
            return File(fileName(domain)).exists()
        }

        /**
         *
         */
        fun fileName(domain: String) : String {
            return BrowserApp.instance.applicationInfo.dataDir + "/shared_prefs/" + name(domain) + ".xml"
        }

        /**
         * Load the default domain settings
         */
        fun loadDefaults(domain: String) : DomainPreferences {
            val defaultFileName = fileName("")
            val thisFileName = fileName(domain)
            try {
                File(defaultFileName).copyTo(File(thisFileName), true)
            } catch (ex: Exception) {
                Timber.d("File copy failed: $ex")
            }

            return DomainPreferences(BrowserApp.instance,domain)
        }

        /**
         * Delete all our domain settings
         * TODO: Make it async
         */
        fun deleteAll(ctx : Context) {
            // Open our shared preferences directory
            val directory = File(ctx.applicationInfo.dataDir, "shared_prefs")
            if (directory.exists() && directory.isDirectory) {
                // Delete each file in this directory matching our domain prefix
                directory.listFiles { _, name -> name.startsWith(prefix) }?.forEach {
                    Timber.d("Delete ${it.absolutePath}: ${it.delete()}")
                }
            }
        }
    }
}

