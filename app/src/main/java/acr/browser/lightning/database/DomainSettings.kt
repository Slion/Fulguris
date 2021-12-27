package acr.browser.lightning.database

import acr.browser.lightning.settings.preferences.UserPreferences
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * domain settings backed by one shared preferences file per host (not actually per domain!)
 * use for persistent storage of sites for dark mode, blocking/allowing js or ads, maybe other things
 * settings changed in one instance of DomainSettings affect other instances with the same host immediately
 */
class DomainSettings(_host: String?, private val context: Context, private val userPrefs: UserPreferences) {
    var host = _host
        set(newHost) {
            if (newHost == field) return
            // update preferences if necessary
            if (newHost.isNullOrBlank() || !newHost.contains('.')) {
                prefs = null // only have prefs for actual hosts
                field = null // always set host to null if it's not backed by any SharedPreferences
            } else {
                prefs = context.getSharedPreferences(newHost, Context.MODE_PRIVATE)
                field = newHost
            }
        }

    // prefs are null if host is null or empty.
    // This should never happen, but during testing it sometimes did (there was a defaultHost.xml file at some point)
    private var prefs: SharedPreferences? = context.getSharedPreferences(host ?: "defaultHost", Context.MODE_PRIVATE)

    //var darkMode by prefs.booleanPreference(DARK_MODE, userPrefs.darkModeDefault)
    // not using delegate because prefs might be null
    var darkMode: Boolean
        set(value) = prefs.set(DARK_MODE, value)
        get() = prefs.get(DARK_MODE, userPrefs.darkModeDefault)

    var desktopMode: Boolean
        set(value) = prefs.set(DESKTOP_MODE, value)
        get() = prefs.get(DESKTOP_MODE, userPrefs.desktopModeDefault)

    var loadImages: Boolean
        set(value) = prefs.set(LOAD_IMAGES, value)
        get() = prefs.get(LOAD_IMAGES, userPrefs.loadImages)

    var javaScriptEnabled: Boolean
        set(value) = prefs.set(JAVA_SCRIPT_ENABLED, value)
        get() = prefs.get(JAVA_SCRIPT_ENABLED, userPrefs.javaScriptEnabled)

    var thirdPartyAppLaunch: Boolean
        set(value) = prefs.set(THIRD_PARTY_APP_LAUNCH, value)
        get() = prefs.get(THIRD_PARTY_APP_LAUNCH, false)

    fun exists(setting: String) = prefs?.contains(setting) ?: false

    // these get/put functions mimic the original SharedPreferences methods
    //  but sometimes access by name is convenient, see the provideSpinner function on BrowserActivity
    fun getBoolean(setting: String) =
         when(setting) {
            DARK_MODE -> darkMode
            DESKTOP_MODE -> desktopMode
            LOAD_IMAGES -> loadImages
            JAVA_SCRIPT_ENABLED -> javaScriptEnabled
            else -> throw(IllegalArgumentException())
        }

    fun putBoolean(setting: String, value: Boolean) {
        when (setting) {
            DARK_MODE -> darkMode = value
            DESKTOP_MODE -> desktopMode = value
            LOAD_IMAGES -> loadImages = value
            JAVA_SCRIPT_ENABLED -> javaScriptEnabled = value
            else -> throw(IllegalArgumentException())
        }
    }

    fun remove(setting: String) {
        if (prefs == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefs!!.contains(setting) && prefs!!.all.size == 1) {
            // we remove the last setting, so the remaining empty file can be deleted
            val h = host ?: return // commit takes a few ms, we need to consider that host may change during this time
            prefs!!.edit().remove(setting).commit() // using commit() instead of apply(), because otherwise deletion reports success, but is still not happening
            context.deleteSharedPreferences(h)
        }
        else
            prefs!!.edit().remove(setting).apply()
    }

    fun removeAll() {
        val h = host ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            prefs!!.edit().clear().commit() // prefs are not affected if the file is deleted, so clear them first
            context.deleteSharedPreferences(h)
        }
        else
            prefs!!.edit().clear().apply()
    }

    companion object {
        const val DARK_MODE = "dark_mode"
        const val DESKTOP_MODE = "desktop_mode"
        const val LOAD_IMAGES = "load_images"
        const val JAVA_SCRIPT_ENABLED = "java_script_enabled"
        const val THIRD_PARTY_APP_LAUNCH = "third_party_app_launch"

        private fun SharedPreferences?.set(pref: String, value: Boolean) {
            if (this == null) return
            edit().putBoolean(pref, value).apply()
        }

        private fun SharedPreferences?.get(pref: String, default: Boolean) =
                this?.getBoolean(pref, default) ?: default

    }
}
