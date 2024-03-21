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

package fulguris.settings.preferences

import fulguris.app
import fulguris.R
import fulguris.extensions.reverseDomainName
import fulguris.settings.NoYesAsk
import fulguris.settings.preferences.delegates.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_MULTI_PROCESS
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fulguris.settings.preferences.delegates.booleanPreference
import fulguris.settings.preferences.delegates.enumPreference
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import java.io.File

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

    var wasCreated = false
    var parentWasCreated = false

    init {
        Timber.d("init: $domain")
    }

    // Workout top private domain
    val topPrivateDomain = if (domain.isNotEmpty()) {
        // Had to go through HttpUrl object otherwise IP address are acting funny
        // TODO: Maybe that should be fixed upstream?
        // Needed to catch in case we get malformed domain
        // See: https://github.com/Slion/Fulguris/issues/596
        try {
            "http://$domain".toHttpUrl().topPrivateDomain()
        } catch (ex: Exception) {
            Timber.w(ex,"Malformed domain")
            // Fallback to default settings then
            null
        }
        /*PublicSuffixDatabase.get().getEffectiveTldPlusOne(domain)*/ } else null

    /*
    var tpd: String? = ""
    Timber.d("getEffectiveTldPlusOne ${measureTimeMillis {
        tpd = PublicSuffixDatabase.get().getEffectiveTldPlusOne(domain)
    }} ms")
    tpd*/

    // Remember order matters, do this first
    init {

        // Make sure default domain settings exists
        if (!exists("")) {
            Timber.d("Create default domain settings")
            context.getSharedPreferences(name(""), MODE_PRIVATE).edit().putBoolean(context.getString(R.string.pref_key_entry_point), false).commit()
        }

        // Make sure parents settings exists
        if (isSubDomain) {
            if (!exists(topPrivateDomain!!)) {
                Timber.d("Create top private domain settings")
                createFromParent(topPrivateDomain,"")
                parentWasCreated = true
            }

            // If our file does not exist yet, create it from default settings
            if (!exists(domain)) {
                createFromParent(domain,topPrivateDomain)
                wasCreated = true
            }
        } else if (!isDefault) {
            // We are either a top level domain or a private address
            if (!exists(domain)) {
                createFromParent(domain,"")
                wasCreated = true
            }
        }
    }

    // Preferences for this domain
    val preferences: SharedPreferences = context.getSharedPreferences(name(domain), MODE_PRIVATE)
    // Preferences of the parent, either the top private domain or the default settings
    // TODO: I don't think that will be working for sub-sub-domain. It seems we just jump to the top level domain anyway
    // See: https://github.com/Slion/Fulguris/issues/554
    val parent: DomainPreferences? = if (isDefault) null else DomainPreferences(context,if (isSubDomain) topPrivateDomain!! else "")

    /**
     * Used to distinguish main domain from resource domain
     */
    var entryPoint by preferences.booleanPreference(R.string.pref_key_entry_point, false)

    /**
     * True if the browser should allow execution of javascript, false otherwise.
     */
    var javaScriptEnabledOverride by preferences.booleanPreference(R.string.pref_key_javascript_override, false)
    var javaScriptEnabledLocal by preferences.booleanPreference(R.string.pref_key_javascript, true)
    val javaScriptEnabledParent: Boolean get() { return parent?.javaScriptEnabled ?: javaScriptEnabledLocal}
    val javaScriptEnabled: Boolean get() { return if (isDefault || !javaScriptEnabledOverride) { javaScriptEnabledParent } else { javaScriptEnabledLocal } }

    /**
     * Accept or reject third-party cookies
     */
    var thirdPartyCookiesOverride by preferences.booleanPreference(R.string.pref_key_third_party_cookies_override, false)
    var thirdPartyCookiesLocal by preferences.booleanPreference(R.string.pref_key_third_party_cookies, true)
    val thirdPartyCookiesParent: Boolean get() { return parent?.thirdPartyCookies ?: thirdPartyCookiesLocal}
    val thirdPartyCookies: Boolean get() { return if (isDefault || !thirdPartyCookiesOverride) { thirdPartyCookiesParent } else { thirdPartyCookiesLocal } }

    /**
     * True if desktop mode should be enabled by default for new tabs, false otherwise.
     */
    var desktopModeOverride by preferences.booleanPreference(R.string.pref_key_desktop_mode_override, false)
    var desktopModeLocal by preferences.booleanPreference(R.string.pref_key_desktop_mode, false)
    val desktopModeParent: Boolean get() { return parent?.desktopMode ?: desktopModeLocal}
    val desktopMode: Boolean get() { return if (isDefault || !desktopModeOverride) { desktopModeParent } else { desktopModeLocal } }

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
     *
     * Must remain YES by default as this is the expected behaviour of browsers on Android.
     */
    var launchAppOverride by preferences.booleanPreference(R.string.pref_key_launch_app_override, false)
    var launchAppLocal by preferences.enumPreference(R.string.pref_key_launch_app, NoYesAsk.YES)
    val launchAppParent: NoYesAsk get() { return parent?.launchApp ?: launchAppLocal}
    val launchApp: NoYesAsk get() { return if (isDefault || !launchAppOverride) { launchAppParent } else { launchAppLocal } }

    /**
     * Define what to do when an SSL error is detected
     * - Yes: Proceed anyway
     * - No: Abort
     * - Ask: Ask the user whether to proceed or abort
     */
    var sslErrorOverride by preferences.booleanPreference(R.string.pref_key_ssl_error_override, false)
    var sslErrorLocal by preferences.enumPreference(R.string.pref_key_ssl_error, NoYesAsk.ASK)
    val sslErrorParent: NoYesAsk get() { return parent?.sslError ?: sslErrorLocal}
    val sslError: NoYesAsk get() { return if (isDefault || !sslErrorOverride) { sslErrorParent } else { sslErrorLocal } }

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

