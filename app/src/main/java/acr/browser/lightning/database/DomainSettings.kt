package acr.browser.lightning.database

import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.settings.preferences.delegates.booleanPreference
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

/**
 * domain settings backed by one shared preferences file per domain
 * use for persistent storage of sites for dark mode, blocking/allowing js or ads, maybe other things
 */
class DomainSettings(_host: String?, private val context: Context, userPrefs: UserPreferences) {
    var host = _host
        set(value) {
            if (value == field) return
            // update preferences if necessary
            prefs = context.getSharedPreferences(host ?: "defaultHost", Context.MODE_PRIVATE)
            field = value
        }
    // TODO: actually if host == null, no setting should be written...
    //  maybe it is enough to delete the "defaultHost" file on app start, just in case it DID happen?
    private var prefs: SharedPreferences = context.getSharedPreferences(host ?: "defaultHost", Context.MODE_PRIVATE)

    var darkMode by prefs.booleanPreference(DARK_MODE, userPrefs.darkModeDefault)

    var desktopMode by prefs.booleanPreference(DESKTOP_MODE, userPrefs.desktopModeDefault)

    var loadImages by prefs.booleanPreference(LOAD_IMAGES, userPrefs.loadImages)

    var javaScriptEnabled by prefs.booleanPreference(JAVA_SCRIPT_ENABLED, userPrefs.javaScriptEnabled)

    fun remove(setting: String) {
        prefs.edit { remove(setting) }
        // remove file if we removed the last preference (only on N and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefs.all.isEmpty())
            host?.let { context.deleteSharedPreferences(it) }
    }

    companion object {
        const val DARK_MODE = "dark_mode"
        const val DESKTOP_MODE = "desktop_mode"
        const val LOAD_IMAGES = "load_images"
        const val JAVA_SCRIPT_ENABLED = "java_script_enabled"
    }
}
