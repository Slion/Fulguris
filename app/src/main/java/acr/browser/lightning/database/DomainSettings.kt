package acr.browser.lightning.database

import acr.browser.lightning.settings.preferences.UserPreferences
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * domain settings backed by one shared preferences file per host (not actually per domain!)
 * use for persistent storage of sites for dark mode, blocking/allowing js or ads, maybe other things
 */
class DomainSettings(_host: String?, private val context: Context, private val userPrefs: UserPreferences) {
    var host = _host
        set(value) {
            if (value == field) return
            // update preferences if necessary
            prefs = if (host.isNullOrBlank())
                null
            else
                context.getSharedPreferences(host, Context.MODE_PRIVATE)
            field = value
        }

    // prefs are null if host is null or empty.
    // This should never happen, but during testing it sometimes did (there was a defaultHost.xml file at some point)
    private var prefs: SharedPreferences? = context.getSharedPreferences(host ?: "defaultHost", Context.MODE_PRIVATE)

    //var darkMode by prefs.booleanPreference(DARK_MODE, userPrefs.darkModeDefault)
    // not using delegate because prefs might be null
    // not as nice code, but still ok
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

    fun remove(setting: String) {
        if (prefs == null) return
        prefs!!.edit().remove(setting).apply()
        // remove file if we removed the last preference (only on N and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefs!!.all.isEmpty())
            host?.let { context.deleteSharedPreferences(it) }
    }

    companion object {
        const val DARK_MODE = "dark_mode"
        const val DESKTOP_MODE = "desktop_mode"
        const val LOAD_IMAGES = "load_images"
        const val JAVA_SCRIPT_ENABLED = "java_script_enabled"

        private fun SharedPreferences?.set(pref: String, value: Boolean) {
            if (this == null) return
            edit().putBoolean(pref, value).apply()
        }

        private fun SharedPreferences?.get(pref: String, default: Boolean) =
                this?.getBoolean(pref, default) ?: default

    }
}
