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

import fulguris.app
import acr.browser.lightning.R
import acr.browser.lightning.extensions.reverseDomainName
import acr.browser.lightning.settings.NoYesAsk
import acr.browser.lightning.settings.preferences.delegates.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_MULTI_PROCESS
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import timber.log.Timber
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Domain preferences
 *
 * TODO: Should we get the default value from the default preferences somehow?
 */
@SuppressLint("ApplySharedPref")
class DomainPreferences constructor(
    val context: Context,
    val domain: String = "",
) {

    init {
        Timber.d("init: $domain")
    }

    // Workout top private domain
    val topPrivateDomain = if (domain.isNotEmpty()) {
        // Had to through HttpUrl object otherwise IP address are acting funny
        // TODO: Maybe that should be fixed upstream?
        "http://$domain".toHttpUrl().topPrivateDomain()
        /*PublicSuffixDatabase.get().getEffectiveTldPlusOne(domain)*/ } else null

    /*
    var tpd: String? = ""
    Timber.d("getEffectiveTldPlusOne ${measureTimeMillis {
        tpd = PublicSuffixDatabase.get().getEffectiveTldPlusOne(domain)
    }} ms")
    tpd*/

    // Remember order matters, do this first
    init {

        if (!exists("")) {
            Timber.d("Create default domain settings")
            context.getSharedPreferences(name(""), MODE_PRIVATE).edit().putBoolean(context.getString(R.string.pref_key_entry_point), false).commit()
        }

        // Make sure parents settings exists
        if (isSubDomain) {
            if (!exists(topPrivateDomain!!)) {
                Timber.d("Create top private domain settings")
                createFromParent(topPrivateDomain,"")
            }

            // If our file does not exist yet, create it from default settings
            if (!exists(domain)) {
                createFromParent(domain,topPrivateDomain)
            }
        } else if (!isDefault) {
            // We are either a top level domain or a private address
            if (!exists(domain)) {
                createFromParent(domain,"")
            }
        }
    }

    // Preferences for this domain
    val preferences: SharedPreferences = context.getSharedPreferences(name(domain), MODE_PRIVATE)
    // Preferences of the parent, either the top private domain or the default settings
    val parent: DomainPreferences? = if (isDefault) null else DomainPreferences(context,if (isSubDomain) topPrivateDomain!! else "")

    /**
     * Used to distinguish main domain from resource domain
     */
    var entryPoint by preferences.booleanPreference(R.string.pref_key_entry_point, false)
    
    /**
     * True if dark mode should be enabled by default for this domain, false otherwise.
     */
    var darkModeOverride by preferences.booleanPreference(R.string.pref_key_dark_mode_override, false)
    var darkModeLocal by preferences.booleanPreference(R.string.pref_key_dark_mode, false)
    val darkModeParent: Boolean get() { return parent?.darkMode ?: darkModeLocal}
    val darkMode: Boolean get() { return if (isDefault || !darkModeOverride) { darkModeParent } else { darkModeLocal } }

    /**
     * Define what to do when a third-party app is available:
     * - Yes: Launch the app
     * - No: Do not launch the app
     * - Ask: Ask the user whether or not to launch the app
     */
    var launchAppOverride by preferences.booleanPreference(R.string.pref_key_launch_app_override, false)
    var launchAppLocal by preferences.enumPreference(R.string.pref_key_launch_app, NoYesAsk.ASK)
    val launchAppParent: NoYesAsk get() { return parent?.launchApp ?: launchAppLocal}
    val launchApp: NoYesAsk get() { return if (isDefault || !launchAppOverride) { launchAppParent } else { launchAppLocal } }

    /**
     * Is this the default domain settings?
     */
    val isDefault: Boolean get() = domain==""

    /**
     * Is this a subdomain settings?
     */
    val isSubDomain: Boolean get() = !isDefault && topPrivateDomain!=null && domain!=topPrivateDomain

    /**
     * Is this settings for top private domain?
     */
    val isTopPrivateDomain: Boolean get() = domain==topPrivateDomain


    /**
     * Load the default domain settings
     */
    private fun createFromParent(domain: String, parent: String) {
        Timber.d("createFromParent: $domain")
        val defaultFileName = fileName("")
        val thisFileName = fileName(domain)
        try {
            File(defaultFileName).copyTo(File(thisFileName), true)
            // Make sure cached values are reloaded from disk
            app.getSharedPreferences(name(domain),MODE_MULTI_PROCESS)
        } catch (ex: Exception) {
            Timber.d("File copy failed: $ex")
        }
    }

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
            // TODO: use getSharedPreferencesPath
            return app.applicationInfo.dataDir + "/shared_prefs/" + name(domain) + ".xml"
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

        /**
         * Delete the settings file belonging to the specified domain.
         */
        fun delete(domain: String) {
            val file = File(fileName(domain))
            Timber.d("Delete ${file.absolutePath}: ${file.delete()}")
        }
    }
}

