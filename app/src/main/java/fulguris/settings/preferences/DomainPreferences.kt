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
import fulguris.extensions.originToDomain
import fulguris.extensions.reverseDomainName
import fulguris.settings.NoYesAsk
import fulguris.enums.IncomingUrlAction
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fulguris.settings.preferences.delegates.booleanPreference
import fulguris.settings.preferences.delegates.enumPreference
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import java.io.File
import androidx.core.content.edit

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
            // Create an empty preferences file for the default domain
            context.getSharedPreferences(name(""), MODE_PRIVATE).edit().apply()
        }

        // Don't automatically create domain settings anymore
        // They will be created on-demand when a user actually changes a setting
    }

    // Preferences for this domain
    val preferences: SharedPreferences = context.getSharedPreferences(name(domain), MODE_PRIVATE)

    // Preferences of the parent, either the top private domain or the default settings
    // TODO: I don't think that will be working for sub-sub-domain. It seems we just jump to the top level domain anyway
    // See: https://github.com/Slion/Fulguris/issues/554
    val parent: DomainPreferences? = if (isDefault) null else DomainPreferences(context, if (isSubDomain) topPrivateDomain!! else "")

    /**
     * True if the browser should allow execution of javascript, false otherwise.
     */
    var javaScriptEnabledOverride by preferences.booleanPreference(R.string.pref_key_javascript_override, false)
    var javaScriptEnabledLocal by preferences.booleanPreference(R.string.pref_key_javascript, true)
    val javaScriptEnabled: Boolean get() {
        val override = javaScriptEnabledOverride
        val local = javaScriptEnabledLocal
        val parentValue = parent?.javaScriptEnabled ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }

    /**
     * Accept or reject third-party cookies
     */
    var thirdPartyCookiesOverride by preferences.booleanPreference(R.string.pref_key_third_party_cookies_override, false)
    var thirdPartyCookiesLocal by preferences.booleanPreference(R.string.pref_key_third_party_cookies, true)
    val thirdPartyCookies: Boolean get() {
        val override = thirdPartyCookiesOverride
        val local = thirdPartyCookiesLocal
        val parentValue = parent?.thirdPartyCookies ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }

    /**
     * Enable or disable geolocation access for this domain (stored preference).
     * For default domain: stores whether location should be enabled globally.
     * For specific domains: this property is not used, use [hasLocationPermission] function instead.
     */
    var locationEnabled by preferences.booleanPreference(R.string.pref_key_location, true)

    /**
     * True if desktop mode should be enabled by default for new tabs, false otherwise.
     */
    var desktopModeOverride by preferences.booleanPreference(R.string.pref_key_desktop_mode_override, false)
    var desktopModeLocal by preferences.booleanPreference(R.string.pref_key_desktop_mode, false)
    val desktopMode: Boolean get() {
        val override = desktopModeOverride
        val local = desktopModeLocal
        val parentValue = parent?.desktopMode ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }

    /**
     * True if dark mode should be enabled by default for this domain, false otherwise.
     */
    var darkModeOverride by preferences.booleanPreference(R.string.pref_key_dark_mode_override, false)
    var darkModeLocal by preferences.booleanPreference(R.string.pref_key_dark_mode, false)
    val darkMode: Boolean get() {
        val override = darkModeOverride
        val local = darkModeLocal
        val parentValue = parent?.darkMode ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }

    /**
     * Define what to do when a third-party app is available:
     * - Yes: Launch the app
     * - No: Do not launch the app
     * - Ask: Ask the user whether or not to launch the app
     * TODO: Add fourth option to show a snackbar with an option the launch the app?
     *
     * Changed to ASK by default to give users more control over app launches.
     */
    var launchAppOverride by preferences.booleanPreference(R.string.pref_key_launch_app_override, false)
    var launchAppLocal by preferences.enumPreference(R.string.pref_key_launch_app, NoYesAsk.ASK)
    val launchApp: NoYesAsk get() {
        val override = launchAppOverride
        val local = launchAppLocal
        val parentValue = parent?.launchApp ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }

    /**
     * Define what to do when an SSL error is detected
     * - Yes: Proceed anyway
     * - No: Abort
     * - Ask: Ask the user whether to proceed or abort
     */
    var sslErrorOverride by preferences.booleanPreference(R.string.pref_key_ssl_error_override, false)
    var sslErrorLocal by preferences.enumPreference(R.string.pref_key_ssl_error, NoYesAsk.ASK)
    val sslError: NoYesAsk get() {
        val override = sslErrorOverride
        val local = sslErrorLocal
        val parentValue = parent?.sslError ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }

    /**
     * Define what to do with incoming URLs
     * - NEW_TAB: Open in new tab
     * - INCOGNITO_TAB: Open in incognito tab
     * - ASK: Ask the user what to do
     */
    var incomingUrlActionOverride by preferences.booleanPreference(R.string.pref_key_incoming_url_action_override, false)
    var incomingUrlActionLocal by preferences.enumPreference(R.string.pref_key_incoming_url_action, IncomingUrlAction.NEW_TAB)
    val incomingUrlAction: IncomingUrlAction get() {
        val override = incomingUrlActionOverride
        val local = incomingUrlActionLocal
        val parentValue = parent?.incomingUrlAction ?: local
        return if (isDefault || !override) { parentValue } else { local }
    }


    /**
     * Is this the default domain settings?
     */
    val isDefault: Boolean get() = domain.isEmpty()

    /**
     * Is this a subdomain settings?
     */
    val isSubDomain: Boolean get() = !isDefault && topPrivateDomain!=null && domain!=topPrivateDomain

    /**
     * Is this settings for top private domain?
     */
    val isTopPrivateDomain: Boolean get() = domain==topPrivateDomain


    /**
     * Check if any overrides are actually enabled.
     * Returns true if at least one override is turned on.
     */
    fun hasAnyOverrides(): Boolean {
        return javaScriptEnabledOverride ||
               thirdPartyCookiesOverride ||
               desktopModeOverride ||
               darkModeOverride ||
               launchAppOverride ||
               sslErrorOverride ||
               incomingUrlActionOverride
    }

    /**
     * Delete this domain's settings file if no overrides are enabled.
     * Should be called when exiting domain settings to keep the list clean.
     * @return true if the settings file was deleted, false otherwise
     */
    fun deleteIfNoOverrides(): Boolean {
        if (!isDefault && exists(domain) && !hasAnyOverrides()) {
            Timber.d("Deleting domain settings with no overrides: $domain")
            delete(domain)
            return true
        }
        return false
    }

    /**
     * Generate a summary string showing which overrides are active for this domain
     * Order matches the preference XML file order
     * Returns the domain name if it doesn't exist or has no overrides
     * @param callback Called with the summary string
     */
    fun getOverridesSummary(context: Context, callback: (String) -> Unit) {

        val overrides = mutableListOf<String>()

        if (!isDefault && exists(domain)) {
            // Order matches preference_domain_default.xml
            if (darkModeOverride) {
                overrides.add(context.getString(R.string.pref_title_dark_mode_default))
            }
            if (desktopModeOverride) {
                overrides.add(context.getString(R.string.pref_title_desktop_mode_default))
            }
            if (javaScriptEnabledOverride) {
                overrides.add(context.getString(R.string.pref_title_javascript))
            }
            if (thirdPartyCookiesOverride) {
                overrides.add(context.getString(R.string.pref_title_third_party_cookies))
            }
            if (launchAppOverride) {
                overrides.add(context.getString(R.string.pref_title_launch_app))
            }
            if (sslErrorOverride) {
                overrides.add(context.getString(R.string.pref_title_ssl_error))
            }
            if (incomingUrlActionOverride) {
                overrides.add(context.getString(R.string.pref_title_incoming_url_action))
            }
        }

        // Check geolocation permission asynchronously
        checkLocationPermission { geoStatus ->
            when (geoStatus) {
                true -> overrides.add(context.getString(R.string.pref_summary_override_location_granted))
                false -> overrides.add(context.getString(R.string.pref_summary_override_location_denied))
                null -> {} // No permission set, don't add to overrides
            }

            val result = if (overrides.isEmpty()) {
                // Return domain name if no overrides exist
                domain.ifEmpty {
                    Timber.w("getOverridesSummary does not make sense for default domain settings")
                    context.getString(R.string.pref_summary_no_overrides)
                }
            } else {
                overrides.joinToString(", ")
            }
            callback(result)
        }
    }

    /**
     * Query geolocation permission status for this domain.
     * Uses WebView's GeolocationPermissions API.
     * @param callback Called with true if permission is granted, false otherwise
     */
    fun hasLocationPermission(callback: (Boolean) -> Unit) {
        if (isDefault) {
            callback(locationEnabled)
            return
        }

        val origin = "https://$domain"
        android.webkit.GeolocationPermissions.getInstance().getAllowed(origin) { allowed ->
            callback(allowed == true)
        }
    }

    /**
     * Check geolocation permission status for this domain.
     * Since getOrigins() returns both granted and denied origins, we need to:
     * 1. Check if domain is in the origins list
     * 2. If yes, use getAllowed() to check if it's granted or denied
     * @param callback Called with: null if no permission set, true if granted, false if denied
     */
    fun checkLocationPermission(callback: (Boolean?) -> Unit) {
        if (isDefault) {
            callback(locationEnabled)
            return
        }

        // First, check if this domain exists in the origins list
        android.webkit.GeolocationPermissions.getInstance().getOrigins { origins: Set<String>? ->
            val matchingOrigin = origins?.firstOrNull { origin ->
                try {
                    // Parse the origin as a URI to properly extract just the host
                    val uri = java.net.URI(origin)
                    val originDomain = uri.host ?: origin.originToDomain()
                    originDomain == domain
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse geolocation origin: $origin")
                    false
                }
            }

            if (matchingOrigin != null) {
                // Domain has a permission state, check if it's granted or denied
                android.webkit.GeolocationPermissions.getInstance().getAllowed(matchingOrigin) { allowed ->
                    callback(allowed)
                }
            } else {
                // Domain not in origins list, no permission set
                callback(null)
            }
        }
    }

    /**
     * Grant geolocation permission for this domain.
     * Uses WebView's GeolocationPermissions API.
     * This domain will be able to access location without calling [fulguris.view.WebPageChromeClient.onGeolocationPermissionsShowPrompt].
     */
    fun grantLocationPermission() {
        if (isDefault) {
            Timber.w("grantLocationPermission is noop for default domain settings")
            return
        }

        val origin = "https://$domain"
        android.webkit.GeolocationPermissions.getInstance().allow(origin)
        Timber.d("Granted geolocation permission for: $origin")
    }

    /**
     * Revoke geolocation permission for this domain.
     * Uses WebView's GeolocationPermissions API.
     * Next time this domain tries to access location [fulguris.view.WebPageChromeClient.onGeolocationPermissionsShowPrompt] will be called.
     */
    fun clearLocationPermission() {
        if (isDefault) {
            Timber.w("clearLocationPermission is noop for default domain settings")
            return
        }

        val origin = "https://$domain"
        android.webkit.GeolocationPermissions.getInstance().clear(origin)
        Timber.i("Revoked geolocation permission for: $origin")
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
         * Check if we have a settings file for the specified domain.
         */
        fun exists(domain: String) : Boolean {
            return File(fileName(domain)).exists()
        }

        /**
         * Check if domain should be visible in the domains list.
         * A domain is visible if:
         * - It has a settings file, OR
         * - It has geolocation permission set (granted or denied)
         * @param domain The domain to check
         * @param callback Called with true if domain should be visible, false otherwise
         */
        fun visible(domain: String, callback: (Boolean) -> Unit) {
            // Check if settings file exists
            if (exists(domain)) {
                callback(true)
                return
            }

            // Check if domain has geolocation permission
            val domainPrefs = DomainPreferences(app, domain)
            domainPrefs.checkLocationPermission { geoStatus ->
                callback(geoStatus != null)
            }
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
         * Also clears SharedPreferences cache for each domain by calling delete() for each
         * Note: This does NOT delete the default domain settings (empty domain)
         * TODO: Make it async
         */
        fun deleteAll(ctx : Context) {
            // Open our shared preferences directory
            val directory = File(ctx.applicationInfo.dataDir, "shared_prefs")
            if (directory.exists() && directory.isDirectory) {
                // Delete each file in this directory matching our domain prefix
                directory.listFiles { _, name -> name.startsWith(prefix) }?.forEach { file ->
                    // Extract domain name from filename (remove prefix and .xml suffix)
                    val domainName = file.nameWithoutExtension.substring(prefix.length)
                    val reversedDomain = domainName.reverseDomainName

                    // Skip the default domain settings (empty domain)
                    if (reversedDomain.isEmpty()) {
                        return@forEach
                    }

                    // Call delete() to handle both file deletion and cache clearing
                    delete(reversedDomain)
                }
            }
        }

        /**
         * Delete the settings file belonging to the specified domain.
         * Also clears the SharedPreferences cache to prevent stale values.
         */
        fun delete(domain: String) {
            // First, clear SharedPreferences from Android's cache
            // This ensures cached values are removed before we delete the file
            try {
                val prefs = app.getSharedPreferences(name(domain), Context.MODE_PRIVATE)
                prefs.edit(commit = true) { clear() }
                Timber.d("Cleared SharedPreferences cache for domain: $domain")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear SharedPreferences cache for domain: $domain")
            }

            // Then, delete the physical file
            val file = File(fileName(domain))
            val deleted = file.delete()
            Timber.d("Delete ${file.absolutePath}: $deleted")
        }

        /**
         * Delete the default domain settings (empty domain).
         * Also clears the SharedPreferences cache to prevent stale values.
         */
        fun deleteDefault() {
            delete("")
        }
    }
}
