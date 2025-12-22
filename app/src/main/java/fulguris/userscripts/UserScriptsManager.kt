package fulguris.userscripts

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages userscripts - JavaScript files that can be injected into web pages
 * Userscripts are stored in the app's files directory under "userscripts/"
 * Each script can be enabled/disabled individually
 */
@Singleton
class UserScriptsManager @Inject constructor(
    private val context: Context,
    private val preferences: SharedPreferences
) {

    companion object {
        private const val PREF_KEY_PREFIX = "userscript_enabled_"
        private const val USERSCRIPTS_DIR = "userscripts"
    }

    /**
     * Data class representing a userscript
     */
    data class UserScript(
        val name: String,
        val fileName: String,
        val enabled: Boolean,
        val content: String
    )

    /**
     * Get the userscripts directory, creating it if needed
     */
    private fun getUserScriptsDirectory(): File {
        val dir = File(context.filesDir, USERSCRIPTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get all userscripts (enabled and disabled)
     */
    fun getAllUserScripts(): List<UserScript> {
        val dir = getUserScriptsDirectory()
        val files = dir.listFiles { file -> file.extension == "js" } ?: return emptyList()

        return files.map { file ->
            val fileName = file.name
            val name = fileName.removeSuffix(".js")
            val enabled = isScriptEnabled(fileName)
            val content = try {
                file.readText()
            } catch (e: Exception) {
                Timber.e(e, "Failed to read userscript: $fileName")
                ""
            }
            UserScript(name, fileName, enabled, content)
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Get only enabled userscripts
     */
    fun getEnabledUserScripts(): List<UserScript> {
        return getAllUserScripts().filter { it.enabled }
    }

    /**
     * Check if a script is enabled
     */
    fun isScriptEnabled(fileName: String): Boolean {
        return preferences.getBoolean(PREF_KEY_PREFIX + fileName, false)
    }

    /**
     * Enable or disable a script
     */
    fun setScriptEnabled(fileName: String, enabled: Boolean) {
        preferences.edit().putBoolean(PREF_KEY_PREFIX + fileName, enabled).apply()
        Timber.d("Userscript $fileName ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Install a new userscript from content
     */
    fun installUserScript(fileName: String, content: String): Boolean {
        return try {
            val dir = getUserScriptsDirectory()
            val file = File(dir, fileName)
            file.writeText(content)
            Timber.i("Installed userscript: $fileName")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to install userscript: $fileName")
            false
        }
    }

    /**
     * Delete a userscript
     */
    fun deleteUserScript(fileName: String): Boolean {
        return try {
            val dir = getUserScriptsDirectory()
            val file = File(dir, fileName)
            val deleted = file.delete()
            if (deleted) {
                // Also remove the enabled preference
                preferences.edit().remove(PREF_KEY_PREFIX + fileName).apply()
                Timber.i("Deleted userscript: $fileName")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete userscript: $fileName")
            false
        }
    }

    /**
     * Get the combined JavaScript code of all enabled scripts
     * This can be injected into a WebView
     */
    fun getCombinedEnabledScripts(): String {
        val scripts = getEnabledUserScripts()
        if (scripts.isEmpty()) {
            return ""
        }

        return scripts.joinToString("\n\n") { script ->
            """
            // === ${script.name} ===
            (function() {
                'use strict';
                ${script.content}
            })();
            """.trimIndent()
        }
    }

    /**
     * Check if there are any enabled userscripts
     */
    fun hasEnabledScripts(): Boolean {
        return getEnabledUserScripts().isNotEmpty()
    }
}

