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
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import fulguris.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL

/**
 * Specifies when a user script should be injected into the page.
 */
enum class RunAt {
    DOCUMENT_START,  // Inject as early as possible
    DOCUMENT_END,    // After DOM is loaded but before images/subframes
    DOCUMENT_IDLE    // After page is completely loaded
}

/**
 * Represents a user script with its metadata and code.
 */
data class UserScript(
    val id: String,              // Unique identifier (filename without extension)
    val name: String,            // @name
    val namespace: String = "",  // @namespace
    val description: String = "", // @description
    val version: String = "",    // @version
    val author: String = "",     // @author
    val iconUrl: String = "",    // @icon or @iconURL
    val matchUrls: List<String> = emptyList(),  // @match patterns
    val excludeUrls: List<String> = emptyList(), // @exclude patterns
    val includeUrls: List<String> = emptyList(), // @include patterns (less specific than @match)
    val runAt: RunAt = RunAt.DOCUMENT_END,      // @run-at
    val code: String,            // The actual JavaScript code
    val enabled: Boolean = true,  // Whether the script is enabled
    val installedDate: Long = System.currentTimeMillis()
) {
    /**
     * Check if this script should run on the given URL.
     */
    fun shouldRunOn(url: String): Boolean {
        if (!enabled) return false

        // Check exclude patterns first
        if (excludeUrls.any { matchesPattern(url, it) }) {
            return false
        }

        // Check match patterns (more specific)
        if (matchUrls.isNotEmpty()) {
            return matchUrls.any { matchesPattern(url, it) }
        }

        // Check include patterns (fallback)
        if (includeUrls.isNotEmpty()) {
            return includeUrls.any { matchesPattern(url, it) }
        }

        // If no patterns defined, don't run
        return false
    }

    //Match a URL against a pattern.
//Supports wildcards: * and **
//Example patterns:
    //- *://example.com/*
    //- https://www.example.com/path/*
    //- *://*.example.com/*
    private fun matchesPattern(url: String, pattern: String): Boolean {
// Convert pattern to regex
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", "\\?")

        return try {
            Regex(regexPattern).matches(url)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load the script icon from the iconUrl.
     * Returns a default icon immediately and can load custom icon asynchronously.
     *
     * @param context Android context for loading resources
     * @return Default icon drawable
     */
    fun getDefaultIcon(context: Context): Drawable? {
        return AppCompatResources.getDrawable(context, R.drawable.ic_script)
    }

    /**
     * Load the script icon from the iconUrl asynchronously.
     * Must be called from a coroutine context.
     *
     * @param context Android context for loading resources
     * @param iconSizeDp Size of icon in dp (default 24)
     * @return Drawable if loading succeeds, null otherwise
     */
    suspend fun loadIcon(context: Context, iconSizeDp: Int = 24): Drawable? {
        if (iconUrl.isEmpty()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // Download icon from URL
                val connection = URL(iconUrl).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()

                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    // Convert dp to pixels based on screen density
                    val iconSizePx = (iconSizeDp * context.resources.displayMetrics.density).toInt()

                    // Scale bitmap to proper size
                    val scaledBitmap = bitmap.scale(iconSizePx, iconSizePx)

                    // Convert to drawable
                    val drawable = scaledBitmap.toDrawable(context.resources)
                    Timber.d("Loaded icon for script: $name")
                    drawable
                } else {
                    Timber.w("Failed to decode icon bitmap from $iconUrl")
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load icon from $iconUrl")
                null
            }
        }
    }

    companion object {
        /**
         * Extract metadata from userscript header.
         * Parses the UserScript metadata block and returns a map of key-value pairs.
         *
         * @param scriptContent The full userscript source code
         * @return Map of metadata keys to their values (e.g., "name" -> "Script Name")
         */
        fun extractMetadata(scriptContent: String): Map<String, String> {
            val metadata = mutableMapOf<String, String>()
            var inMetadata = false

            scriptContent.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.contains("==UserScript==") -> inMetadata = true
                    trimmed.contains("==/UserScript==") -> inMetadata = false
                    inMetadata && trimmed.startsWith("//") -> {
                        val match = Regex("""//\s*@(\w+(?:-\w+)?)\s+(.+)""").find(trimmed)
                        if (match != null) {
                            val key = match.groupValues[1]
                            val value = match.groupValues[2].trim()
                            // Only store first occurrence
                            if (!metadata.containsKey(key)) {
                                metadata[key] = value
                            }
                        }
                    }
                }
            }
            return metadata
        }
    }
}
