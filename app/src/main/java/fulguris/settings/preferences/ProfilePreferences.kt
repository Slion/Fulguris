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
 * All portions of the code written by Stéphane Lenclud are Copyright © 2026 Stéphane Lenclud.
 * All Rights Reserved.
 */
package fulguris.settings.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import fulguris.app
import timber.log.Timber
import java.io.File

/**
 * Per-profile preferences wrapper.
 *
 * A profile is a named container that will eventually be used to isolate WebView storage
 * (cookies, cache, local storage, etc.) across sessions.
 * See https://github.com/Slion/Fulguris/issues/618
 *
 * For now this class only manages the profile metadata (name + persistence file).
 * Wiring profiles to actual WebView data partitioning will be done in a follow-up.
 */
@SuppressLint("ApplySharedPref")
class ProfilePreferences(
    val context: Context,
    val name: String,
) {

    /**
     * SharedPreferences file backing this profile.
     * Created on first write.
     */
    val preferences: SharedPreferences = context.getSharedPreferences(fileName(name), MODE_PRIVATE)

    companion object {

        /**
         * Prefix used for the SharedPreferences file backing each profile.
         * The square brackets keep these files visually grouped in `shared_prefs/` and
         * make them trivial to enumerate.
         */
        /**
         * Maps our profile name to the name used by [ProfileStore].
         * "Default" maps to "" (the webkit default profile) so existing user data is preserved.
         * All other profiles use their display name directly.
         */
        fun toProfileStoreName(name: String): String = if (name == DEFAULT) "" else name

        const val PREFIX = "[Profile]"

        /**
         * SharedPreferences file name that maps session names to profile names.
         * Key = session name, value = profile name. Absent key means Default.
         */
        private const val PREFS_SESSION_MAP = "[Profile]_session_map"

        /**
         * Returns the profile name assigned to [sessionName].
         * Falls back to [DEFAULT] if unset or if the stored profile no longer exists.
         */
        fun sessionProfile(sessionName: String): String {
            val mapped = app.getSharedPreferences(PREFS_SESSION_MAP, MODE_PRIVATE)
                .getString(sessionName, DEFAULT) ?: DEFAULT
            return if (mapped == DEFAULT || exists(mapped)) mapped else DEFAULT
        }

        /**
         * Assigns [sessionName] to [profileName].
         * Pass [DEFAULT] to remove any explicit assignment (session falls back to Default).
         */
        fun setSessionProfile(sessionName: String, profileName: String) {
            app.getSharedPreferences(PREFS_SESSION_MAP, MODE_PRIVATE)
                .edit(commit = true) {
                    if (profileName == DEFAULT) remove(sessionName)
                    else putString(sessionName, profileName)
                }
        }

        /**
         * Returns all session names from [allSessions] assigned to [profileName].
         * For [DEFAULT], returns sessions with no explicit non-default assignment.
         */
        fun sessionsForProfile(profileName: String, allSessions: List<String>): List<String> {
            return allSessions.filter { sessionProfile(it) == profileName }
        }

        /**
         * Name of the default profile. Sessions that do not explicitly select a profile
         * are expected to use this one.
         */
        const val DEFAULT = "Default"

        /**
         * Provide the SharedPreferences file name for the given profile [name].
         */
        fun fileName(name: String): String = "$PREFIX$name"

        /**
         * Path to the on-disk XML file backing the given profile [name].
         */
        fun filePath(name: String): String =
            app.applicationInfo.dataDir + "/shared_prefs/" + fileName(name) + ".xml"

        /**
         * Whether a settings file for the given profile [name] exists on disk.
         */
        fun exists(name: String): Boolean = File(filePath(name)).exists()

        /**
         * Whether the given string is a valid profile name.
         * Mirrors the rules used for session names: non-blank and not already in use.
         */
        fun isValidName(name: String): Boolean {
            if (name.isBlank()) return false
            return list().none { it.equals(name, ignoreCase = true) }
        }

        /**
         * Enumerate all known profile names by scanning the `shared_prefs` directory.
         */
        fun list(): List<String> {
            val directory = File(app.applicationInfo.dataDir, "shared_prefs")
            if (!directory.exists() || !directory.isDirectory) return emptyList()
            return directory.list { _, fileName -> fileName.startsWith(PREFIX) }
                ?.map { it.removePrefix(PREFIX).removeSuffix(".xml") }
                ?.filter { it.isNotEmpty() && !it.startsWith("_") }
                ?.sorted()
                ?: emptyList()
        }

        /**
         * Create an empty profile with the given [name].
         * Returns true if the profile was created, false if the name is invalid or
         * a profile with that name already exists.
         */
        fun create(name: String): Boolean {
            if (!isValidName(name)) return false
            // Write a sentinel key synchronously so the file exists on disk for list() to find.
            app.getSharedPreferences(fileName(name), MODE_PRIVATE).edit(commit = true) {
                putString("_name", name)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().getOrCreateProfile(toProfileStoreName(name))
            }
            Timber.d("Created profile: $name")
            return true
        }

        /**
         * Delete the profile with the given [name].
         * Clears the SharedPreferences cache and removes the backing file.
         */
        fun delete(name: String) {
            try {
                app.getSharedPreferences(fileName(name), Context.MODE_PRIVATE)
                    .edit(commit = true) { clear() }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear SharedPreferences cache for profile: $name")
            }
            val file = File(filePath(name))
            val deleted = file.delete()
            Timber.d("Delete profile $name -> ${file.absolutePath}: $deleted")
            // Reassign sessions that used this profile back to Default.
            val mapPrefs = app.getSharedPreferences(PREFS_SESSION_MAP, MODE_PRIVATE)
            mapPrefs.all.entries
                .filter { it.value == name }
                .forEach { mapPrefs.edit(commit = true) { remove(it.key) } }
            // Wipe the webkit data partition (cookies, cache, localStorage, etc.).
            // Cannot delete the webkit default profile (mapped from our "Default").
            if (name != DEFAULT && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                try {
                    ProfileStore.getInstance().deleteProfile(name)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete ProfileStore entry for profile: $name")
                }
            }
        }

        /**
         * Delete every profile, including the default one.
         */
        fun deleteAll() {
            list().forEach { delete(it) }
        }

        /**
         * Rename the profile [oldName] to [newName].
         * Returns true on success.
         */
        fun rename(oldName: String, newName: String): Boolean {
            if (oldName == newName) return false
            if (!exists(oldName)) return false
            if (!isValidName(newName)) return false
            val src = File(filePath(oldName))
            val dst = File(filePath(newName))
            // Clear caches before moving the file so Android does not write stale state back.
            try {
                app.getSharedPreferences(fileName(oldName), Context.MODE_PRIVATE)
                    .edit(commit = true) { /* flush */ }
            } catch (_: Exception) { /* best-effort */ }
            val ok = src.renameTo(dst)
            Timber.d("Rename profile $oldName -> $newName: $ok")
            // Keep session map consistent.
            if (ok) {
                val mapPrefs = app.getSharedPreferences(PREFS_SESSION_MAP, MODE_PRIVATE)
                mapPrefs.all.entries
                    .filter { it.value == oldName }
                    .forEach { mapPrefs.edit(commit = true) { putString(it.key, newName) } }
            }
            return ok
        }

        /**
         * Make sure the default profile exists.
         */
        fun ensureDefaultExists() {
            if (!exists(DEFAULT)) {
                create(DEFAULT)
            }
        }

        // ---- Active profile persistence (stored in UserPreferences) ----

        /**
         * The name of the currently active profile. Persisted in [UserPreferences].
         * Changing this takes effect after the next app restart (WebView data dir is set at startup).
         */
        var activeProfile: String
            get() = app.userPreferences.activeProfile
            set(value) { app.userPreferences.activeProfile = value }

        /**
         * Activate [name] as the current profile. Saves the selection immediately.
         */
        fun activate(name: String) {
            activeProfile = name
        }

        /**
         * Returns the [android.webkit.GeolocationPermissions] for the currently active profile.
         * When MULTI_PROFILE is supported, returns the profile-specific store so that
         * domain settings correctly reflect permissions granted while that profile was in use.
         * Falls back to the system default when the feature is unavailable.
         */
        fun activeGeolocationPermissions(): android.webkit.GeolocationPermissions {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val storeName = toProfileStoreName(activeProfile)
                return ProfileStore.getInstance().getOrCreateProfile(storeName)
                    .getGeolocationPermissions()
            }
            return android.webkit.GeolocationPermissions.getInstance()
        }
    }
}
