package acr.browser.lightning.database

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

// generic list of hosts backed by a shared preferences file
//  use for persistent storage of sites for dark mode, blocking/allowing js or ads, maybe other things
class HostsList(applicationContext: Context, pref: String) {
    val prefs: SharedPreferences = applicationContext.getSharedPreferences(pref, Context.MODE_PRIVATE)

    fun add(url: String, tf: Boolean) {
        val host = url.host() ?: return // should there be some error message?
        prefs.edit().putBoolean(host, tf).apply()
    }

    fun exists(url: String): Boolean {
        val host = url.host() ?: return false
        return prefs.contains(host)
    }

    fun get(url: Uri, default: Boolean): Boolean {
        val host = url.host ?: return default
        return prefs.getBoolean(host, default)
    }

    fun get(url: String, default: Boolean): Boolean {
        return get(Uri.parse(url), default)
    }

    fun remove(url: String) {
        val host = url.host() ?: return
        prefs.edit().remove(host).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun String.host(): String? = Uri.parse(this).host

    companion object {
        const val DARK_MODE = "dark_mode"
        const val DESKTOP_MODE = "desktop_mode"
    }
}
 
