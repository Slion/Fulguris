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

package fulguris.utils

import android.app.Application
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

/**
 * Utility class for recovering tab data from corrupted session files.
 *
 * When WebView is upgraded, the internal bundle state can become incompatible,
 * causing BadParcelableException during normal deserialization. This class
 * provides methods to extract tab URLs and titles directly from the raw binary
 * data by searching for known string patterns.
 *
 * Bundle/Parcel format notes:
 * - Strings in Parcel are stored as: length (4 bytes, little-endian), followed by UTF-16LE characters, followed by null terminator
 * - The length is the number of characters (not bytes)
 * - Tab data is stored with keys like "TAB_00000", "TAB_00001", etc.
 * - Each tab bundle contains: URL, TITLE, DESKTOP_MODE, DARK_MODE, SEARCH_QUERY, SEARCH_ACTIVE, FAVICON, WEB_VIEW
 */
object SessionRecovery {

    // Keys used in TabModel
    private const val URL_KEY = "URL"
    private const val TAB_TITLE_KEY = "TITLE"
    private const val TAB_KEY_PREFIX = "TAB_"

    /**
     * Represents a recovered tab with minimal information
     */
    data class RecoveredTab(
        val url: String,
        val title: String,
        val tabKey: String? = null
    )

    /**
     * Attempt to recover tabs from a corrupted session file.
     * This method reads the raw binary data and searches for URL patterns.
     *
     * @param app The application context
     * @param sessionFileName The name of the session file (e.g., "SESSION_⚡ Fulguris")
     * @return List of recovered tabs, or empty list if recovery failed
     */
    fun recoverTabsFromSession(app: Application, sessionFileName: String): List<RecoveredTab> {
        val file = File(app.filesDir, sessionFileName)
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val data = FileInputStream(file).use { it.readBytes() }
            recoverTabsFromBinary(data)
        } catch (e: Exception) {
            Timber.w(e, "SessionRecovery: Failed to read file")
            emptyList()
        }
    }

    /**
     * Recover tabs from raw binary data.
     *
     * @param data The raw bytes from a session file
     * @return List of recovered tabs
     */
    fun recoverTabsFromBinary(data: ByteArray): List<RecoveredTab> {
        val tabs = mutableListOf<RecoveredTab>()

        // Strategy 1: Find URL key markers and extract following strings
        val urlKeyPositions = findStringInParcel(data, URL_KEY)
        val tabKeyPositions = findTabKeys(data)

        // Process each URL key position to extract URL and title
        for (pos in urlKeyPositions) {
            try {
                val urlValue = extractStringAfterKey(data, pos, URL_KEY)
                if (urlValue != null && urlValue.isNotEmpty() && isValidUrl(urlValue)) {
                    val title = findNearbyTitle(data, pos) ?: ""
                    val tabKey = findNearbyTabKey(data, pos, tabKeyPositions)
                    tabs.add(RecoveredTab(urlValue, title, tabKey))
                }
            } catch (_: Exception) {
                // Skip this position
            }
        }

        // Fallback: If no URLs found via key method, try pattern matching
        if (tabs.isEmpty()) {
            findUrls(data).forEach { url ->
                tabs.add(RecoveredTab(url, "", null))
            }
        }

        // Remove duplicates while preserving order
        val uniqueTabs = tabs.distinctBy { it.url }
        if (uniqueTabs.isNotEmpty()) {
            Timber.d("SessionRecovery: Recovered ${uniqueTabs.size} tabs")
        }

        return uniqueTabs
    }

    /**
     * Find all positions where a string appears in parcel format.
     * Parcel strings are: length (4 bytes LE) + UTF-16LE chars + null terminator
     */
    private fun findStringInParcel(data: ByteArray, target: String): List<Int> {
        val positions = mutableListOf<Int>()
        val targetBytes = target.toByteArray(StandardCharsets.UTF_16LE)

        var i = 0
        while (i < data.size - targetBytes.size - 4) {
            // Check if this could be a string length prefix matching our target
            val len = readInt32LE(data, i)
            if (len == target.length && i + 4 + len * 2 + 2 <= data.size) {
                // Check if the UTF-16LE bytes match
                var match = true
                for (j in targetBytes.indices) {
                    if (data[i + 4 + j] != targetBytes[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    positions.add(i)
                }
            }
            i++
        }

        return positions
    }

    /**
     * Find TAB_XXXXX keys in the data
     */
    private fun findTabKeys(data: ByteArray): List<Pair<Int, String>> {
        val tabKeys = mutableListOf<Pair<Int, String>>()
        val prefix = TAB_KEY_PREFIX.toByteArray(StandardCharsets.UTF_16LE)

        var i = 0
        while (i < data.size - prefix.size - 4) {
            // Look for TAB_ prefix after a length field
            val len = readInt32LE(data, i)
            // TAB_XXXXX is 9 characters
            if (len in 5..15 && i + 4 + len * 2 + 2 <= data.size) {
                // Check if it starts with TAB_
                var match = true
                for (j in prefix.indices) {
                    if (data[i + 4 + j] != prefix[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    val tabKey = readParcelString(data, i)
                    if (tabKey != null && tabKey.startsWith(TAB_KEY_PREFIX)) {
                        tabKeys.add(Pair(i, tabKey))
                    }
                }
            }
            i++
        }

        return tabKeys
    }

    /**
     * Extract the string value that follows a key in a Bundle parcel.
     */
    private fun extractStringAfterKey(data: ByteArray, keyPos: Int, keyString: String): String? {
        // After the key string, we have:
        // - null terminator (2 bytes for UTF-16)
        // - padding to 4-byte boundary (maybe)
        // - value type (4 bytes) - VAL_STRING = 0
        // - string length (4 bytes)
        // - string data (UTF-16LE)

        val keyLen = keyString.length
        // Position after key: keyPos + 4 (length) + keyLen*2 (UTF-16LE chars) + 2 (null terminator)
        var pos = keyPos + 4 + keyLen * 2 + 2

        // Align to 4-byte boundary
        pos = (pos + 3) and 0x7FFFFFFC

        // Read value type
        if (pos + 4 > data.size) return null
        val valType = readInt32LE(data, pos)
        pos += 4

        // VAL_STRING = 0, VAL_BUNDLE = 3
        if (valType != 0) {
            // Not a string type, try alternative parsing
            // Sometimes the layout is slightly different
            return tryAlternativeExtraction(data, keyPos, keyString)
        }

        return readParcelString(data, pos)
    }

    /**
     * Try alternative methods to extract the URL value
     */
    @Suppress("UNUSED_PARAMETER")
    private fun tryAlternativeExtraction(data: ByteArray, keyPos: Int, keyString: String): String? {
        // Search forward from key position for URL-like strings
        val searchStart = keyPos
        val searchEnd = minOf(keyPos + 2000, data.size) // Search within 2KB

        for (i in searchStart until searchEnd - 4) {
            val len = readInt32LE(data, i)
            if (len in 5..2000) {
                val str = readParcelString(data, i)
                if (str != null && isValidUrl(str)) {
                    return str
                }
            }
        }

        return null
    }

    /**
     * Find the TITLE value near a URL key position
     */
    private fun findNearbyTitle(data: ByteArray, urlKeyPos: Int): String? {
        // Search for TITLE key within a reasonable range
        val searchStart = maxOf(0, urlKeyPos - 1000)
        val searchEnd = minOf(urlKeyPos + 1000, data.size)

        val titlePositions = findStringInParcel(data.sliceArray(searchStart until searchEnd), TAB_TITLE_KEY)

        for (relPos in titlePositions) {
            val absPos = searchStart + relPos
            val title = extractStringAfterKey(data, absPos, TAB_TITLE_KEY)
            if (title != null && title.isNotEmpty() && !isValidUrl(title)) {
                return title
            }
        }

        return null
    }

    /**
     * Find the TAB_XXXXX key that this URL belongs to
     */
    @Suppress("UNUSED_PARAMETER")
    private fun findNearbyTabKey(data: ByteArray, urlKeyPos: Int, tabKeys: List<Pair<Int, String>>): String? {
        // Find the closest TAB_ key before this URL position
        return tabKeys
            .filter { it.first < urlKeyPos }
            .maxByOrNull { it.first }
            ?.second
    }

    /**
     * Find URLs by searching for common URL patterns in the binary data
     */
    private fun findUrls(data: ByteArray): List<String> {
        val urls = mutableSetOf<String>()

        // Common URL prefixes to search for
        val prefixes = listOf(
            "https://",
            "http://",
            "file://",
            "fulguris://",
            "about:"
        )

        for (prefix in prefixes) {
            val prefixBytes = prefix.toByteArray(StandardCharsets.UTF_16LE)

            var i = 0
            while (i < data.size - prefixBytes.size) {
                var match = true
                for (j in prefixBytes.indices) {
                    if (data[i + j] != prefixBytes[j]) {
                        match = false
                        break
                    }
                }

                if (match) {
                    // Found a prefix, try to extract the full URL
                    // Go back to find the string length
                    if (i >= 4) {
                        val url = readParcelString(data, i - 4)
                        if (url != null && isValidUrl(url)) {
                            urls.add(url)
                        }
                    }
                }
                i++
            }
        }

        return urls.toList()
    }

    /**
     * Read a 32-bit little-endian integer
     */
    private fun readInt32LE(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return -1
        return (data[offset].toInt() and 0xFF) or
               ((data[offset + 1].toInt() and 0xFF) shl 8) or
               ((data[offset + 2].toInt() and 0xFF) shl 16) or
               ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    /**
     * Read a parcel string at the given position.
     * Format: length (4 bytes LE) + UTF-16LE chars + null terminator (2 bytes)
     */
    private fun readParcelString(data: ByteArray, offset: Int): String? {
        if (offset + 4 > data.size) return null

        val length = readInt32LE(data, offset)
        if (length < 0 || length > 10000) return null // Sanity check

        val stringStart = offset + 4
        val stringBytes = length * 2 // UTF-16LE uses 2 bytes per char

        if (stringStart + stringBytes > data.size) return null

        return try {
            String(data, stringStart, stringBytes, StandardCharsets.UTF_16LE)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Check if a string looks like a valid URL
     */
    private fun isValidUrl(str: String): Boolean {
        return str.startsWith("http://") ||
               str.startsWith("https://") ||
               str.startsWith("file://") ||
               str.startsWith("fulguris://") ||
               str.startsWith("about:") ||
               str.startsWith("data:") ||
               str.startsWith("javascript:") ||
               str.startsWith("content://")
    }

    /**
     * List all session files in the app's files directory
     */
    fun listSessionFiles(app: Application): List<File> {
        val filesDir = app.filesDir
        return filesDir.listFiles()?.filter {
            it.name.startsWith("SESSION_") || it.name == "SESSIONS"
        } ?: emptyList()
    }

    /**
     * Dump session file structure for debugging
     */
    fun dumpSessionFileInfo(app: Application, sessionFileName: String): String {
        val file = File(app.filesDir, sessionFileName)
        if (!file.exists()) {
            return "File not found: ${file.path}"
        }

        val sb = StringBuilder()
        sb.appendLine("Session file: ${file.path}")
        sb.appendLine("Size: ${file.length()} bytes")
        sb.appendLine("Last modified: ${java.util.Date(file.lastModified())}")
        sb.appendLine()

        try {
            val data = FileInputStream(file).use { it.readBytes() }

            // Find TAB keys
            val tabKeys = findTabKeys(data)
            sb.appendLine("Tab keys found: ${tabKeys.size}")
            tabKeys.forEach { (pos, key) ->
                sb.appendLine("  - $key at position $pos")
            }
            sb.appendLine()

            // Find URLs
            val urls = findUrls(data)
            sb.appendLine("URLs found: ${urls.size}")
            urls.forEach { url ->
                sb.appendLine("  - $url")
            }
            sb.appendLine()

            // Try recovery
            val recovered = recoverTabsFromBinary(data)
            sb.appendLine("Recovered tabs: ${recovered.size}")
            recovered.forEach { tab ->
                sb.appendLine("  - [${tab.tabKey ?: "?"}] ${tab.title.take(50)}")
                sb.appendLine("    ${tab.url}")
            }

        } catch (e: Exception) {
            sb.appendLine("Error analyzing file: ${e.message}")
        }

        return sb.toString()
    }
}
