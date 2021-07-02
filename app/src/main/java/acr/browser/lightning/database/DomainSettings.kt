package acr.browser.lightning.database

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

// domain settings backed by one shared preferences file per domain
//  use for persistent storage of sites for dark mode, blocking/allowing js or ads, maybe other things
class DomainSettings(applicationContext: Context, url: String) {
    val prefs: SharedPreferences = applicationContext.getSharedPreferences(Uri.parse(url).host ?: "default", Context.MODE_PRIVATE)

    fun set(setting: String, tf: Boolean) {
        prefs.edit().putBoolean(setting, tf).apply()
    }

    fun get(setting: String, default: Boolean): Boolean {
        return prefs.getBoolean(setting, default)
    }

    fun remove(setting: String) {
        prefs.edit().remove(setting).apply()
        if (prefs.all.isEmpty())
            clear() // remove file if we removed the last preference
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        const val DARK_MODE = "dark_mode"
        const val DESKTOP_MODE = "desktop_mode"
    }
}
