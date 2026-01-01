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

package fulguris.userscript

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user scripts: installation, deletion, enabling/disabling, and injection.
 */
@Singleton
class UserScriptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val scriptsDir = File(context.filesDir, "userscripts")
    private val prefs: SharedPreferences = context.getSharedPreferences("userscripts", Context.MODE_PRIVATE)

    init {
        // Ensure scripts directory exists
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }
    }

    /**
     * Parse and install a user script from its source code.
     * Returns the installed script ID on success, null on failure.
     */
    fun installScript(sourceCode: String): String? {
        try {
            val script = parseUserScript(sourceCode)

            // Generate ID from name if not exists
            val id = generateScriptId(script.name)

            // Save script file
            val scriptFile = File(scriptsDir, "$id.user.js")
            scriptFile.writeText(sourceCode)

            // Save enabled state and installation date
            val installDate = System.currentTimeMillis()
            prefs.edit()
                .putBoolean("enabled_$id", true)
                .putLong("install_date_$id", installDate)
                .apply()

            Timber.i("Installed user script: ${script.name} (ID: $id)")
            return id
        } catch (e: Exception) {
            Timber.e(e, "Failed to install user script")
            return null
        }
    }

    /**
     * Get all installed scripts.
     */
    fun getInstalledScripts(): List<UserScript> {
        val scripts = mutableListOf<UserScript>()

        scriptsDir.listFiles { file -> file.extension == "js" }?.forEach { file ->
            try {
                val sourceCode = file.readText()
                val script = parseUserScript(sourceCode)
                // Extract ID by removing .user.js extension (file.nameWithoutExtension only removes .js)
                val id = file.name.removeSuffix(".user.js")
                val enabled = prefs.getBoolean("enabled_$id", true)
                // Get installation date from preferences, fall back to file modification time for old scripts
                val installedDate = prefs.getLong("install_date_$id", file.lastModified())

                scripts.add(script.copy(
                    id = id,
                    enabled = enabled,
                    installedDate = installedDate
                ))
            } catch (e: Exception) {
                Timber.e(e, "Failed to load script: ${file.name}")
            }
        }

        return scripts.sortedByDescending { it.installedDate }
    }

    /**
     * Get a single script by ID.
     */
    fun getScript(id: String): UserScript? {
        return try {
            val scriptFile = File(scriptsDir, "$id.user.js")
            if (!scriptFile.exists()) return null

            val sourceCode = scriptFile.readText()
            val script = parseUserScript(sourceCode)
            val enabled = prefs.getBoolean("enabled_$id", true)
            val installedDate = prefs.getLong("install_date_$id", scriptFile.lastModified())

            script.copy(
                id = id,
                enabled = enabled,
                installedDate = installedDate
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load script: $id")
            null
        }
    }

    /**
     * Get scripts that should run on the given URL.
     */
    fun getScriptsForUrl(url: String): List<UserScript> {
        return getInstalledScripts().filter { it.shouldRunOn(url) }
    }

    /**
     * Delete a script by ID.
     */
    fun deleteScript(id: String): Boolean {
        try {
            val scriptFile = File(scriptsDir, "$id.user.js")
            val deleted = scriptFile.delete()

            // Remove preferences
            prefs.edit()
                .remove("enabled_$id")
                .remove("install_date_$id")
                .apply()

            Timber.i("Deleted user script: $id")
            return deleted
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete script: $id")
            return false
        }
    }

    /**
     * Enable or disable a script.
     */
    fun setScriptEnabled(id: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$id", enabled).apply()
        Timber.i("Set script $id enabled: $enabled")
    }

    /**
     * Check if a script with the same name is already installed.
     */
    fun isScriptInstalled(name: String): Boolean {
        val id = generateScriptId(name)
        return File(scriptsDir, "$id.user.js").exists()
    }

    /**
     * Parse user script metadata from source code.
     */
    private fun parseUserScript(sourceCode: String): UserScript {
        val metadata = extractMetadata(sourceCode)

        return UserScript(
            id = "", // Will be set later
            name = metadata["name"]?.firstOrNull() ?: "Unnamed Script",
            namespace = metadata["namespace"]?.firstOrNull() ?: "",
            description = metadata["description"]?.firstOrNull() ?: "",
            version = metadata["version"]?.firstOrNull() ?: "",
            author = metadata["author"]?.firstOrNull() ?: "",
            iconUrl = metadata["icon"]?.firstOrNull() ?: metadata["iconURL"]?.firstOrNull() ?: "",
            matchUrls = metadata["match"] ?: emptyList(),
            excludeUrls = metadata["exclude"] ?: emptyList(),
            includeUrls = metadata["include"] ?: emptyList(),
            runAt = parseRunAt(metadata["run-at"]?.firstOrNull()),
            code = sourceCode
        )
    }


    private fun extractMetadata(sourceCode: String): Map<String, List<String>> {
        val metadata = mutableMapOf<String, MutableList<String>>()
        var inMetadata = false

        sourceCode.lines().forEach { line ->
            val trimmed = line.trim()

            when {
                trimmed.contains("==UserScript==") -> inMetadata = true
                trimmed.contains("==/UserScript==") -> inMetadata = false
                inMetadata && trimmed.startsWith("//") -> {
                    // Parse metadata line: // @key value
                    val match = Regex("""//\s*@(\w+(?:-\w+)?)\s+(.+)""").find(trimmed)
                    if (match != null) {
                        val key = match.groupValues[1]
                        val value = match.groupValues[2].trim()
                        metadata.getOrPut(key) { mutableListOf() }.add(value)
                    }
                }
            }
        }

        return metadata
    }

    /**
     * Parse @run-at value.
     */
    private fun parseRunAt(value: String?): RunAt {
        return when (value?.lowercase()) {
            "document-start" -> RunAt.DOCUMENT_START
            "document-end" -> RunAt.DOCUMENT_END
            "document-idle" -> RunAt.DOCUMENT_IDLE
            else -> RunAt.DOCUMENT_END
        }
    }

    /**
     * Generate a unique script ID from its name.
     */
    private fun generateScriptId(name: String): String {
        // Create a safe filename from the script name
        val safeName = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(50)

        // Add hash to ensure uniqueness
        val hash = MessageDigest.getInstance("MD5")
            .digest(name.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)

        return "${safeName}_$hash"
    }

    /**
     * Get JavaScript code to inject for the given URL at the specified timing.
     * Each script is wrapped in its own try-catch to isolate errors.
     */
    fun getInjectionCode(url: String, runAt: RunAt): String? {
        val scripts = getScriptsForUrl(url).filter { it.runAt == runAt }

        if (scripts.isEmpty()) return null

        // Log which scripts are being injected
        scripts.forEach { script ->
            Timber.d("Injecting userscript: '${script.name}' (ID: ${script.id}, version: ${script.version})")
        }

        // Combine all matching scripts with error handling
        return scripts.joinToString("\n\n") { script ->
            """
            // === User Script: ${script.name} ===
            // Version: ${script.version}
            // ID: ${script.id}
            (function() {
                try {
                    ${script.code}
                    console.log('fulguris: ex: loaded "${script.name}"');
                } catch (e) {
                    console.error('fulguris: ex: "${script.name}" - ' + (e.stack || e.toString()));
                }
            })();
            """.trimIndent()
        }
    }
}


