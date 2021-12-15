package acr.browser.lightning.database

import acr.browser.lightning.preference.UserPreferences
import acr.browser.lightning.preference.delegates.booleanPreference
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit

/**
 * domain settings backed by one shared preferences file per domain
 * use for persistent storage of sites for dark mode, blocking/allowing js or ads, maybe other things
 */
class DomainSettings(val host: String?, private val context: Context, userPrefs: UserPreferences) {
    private val prefs: SharedPreferences = context.getSharedPreferences(host ?: "defaultHost", Context.MODE_PRIVATE)

    var darkMode by prefs.booleanPreference(DARK_MODE, userPrefs.darkModeDefault)

    var desktopMode by prefs.booleanPreference(DESKTOP_MODE, userPrefs.desktopModeDefault)

    fun remove(setting: String) {
        prefs.edit { remove(setting) }
        // remove file if we removed the last preference (only on N and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefs.all.isEmpty())
            host?.let { context.deleteSharedPreferences(it) }
    }

    companion object {
        const val DARK_MODE = "dark_mode"
        const val DESKTOP_MODE = "desktop_mode"
    }
}
