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
package fulguris.browser

import acr.browser.lightning.browser.sessions.Session
import android.app.Application
import android.os.Bundle
import fulguris.utils.FileUtils
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages session persistence independently of TabsManager initialization state.
 * This allows session access from system settings without requiring the app to be running.
 */
@Singleton
class SessionsManager @Inject constructor(
    private val application: Application
) {

    companion object {
        private const val KEY_CURRENT_SESSION = "KEY_CURRENT_SESSION"
        private const val KEY_SESSIONS = "KEY_SESSIONS"
        private const val FILENAME_SESSIONS = "SESSIONS"
        const val FILENAME_SESSION_PREFIX = "SESSION_"
    }

    // In-memory cache of sessions data - the single source of truth
    private var iSessions: ArrayList<Session> = ArrayList()
    private var iCurrentSessionName: String = ""

    init {
        // Load sessions eagerly when singleton is created
        loadSessions()
    }

    /**
     * Get the current list of sessions.
     */
    fun sessions(): ArrayList<Session> {
        return iSessions
    }

    /**
     * Get the current session name.
     */
    fun currentSessionName(): String {
        return iCurrentSessionName
    }


    /**
     * Load all available sessions from disk.
     * This method can be called without TabsManager initialization.
     */
    @Suppress("DEPRECATION")
    private fun loadSessions() {
        Timber.d("loadSessions")

        val bundle = FileUtils.readBundleFromStorage(application, FILENAME_SESSIONS)

        val sessions = ArrayList<Session>()
        var currentSessionName = ""

        bundle?.apply {
            getParcelableArrayList<Session>(KEY_SESSIONS)?.let { sessions.addAll(it) }
            getString(KEY_CURRENT_SESSION)?.let { currentSessionName = it }
        }

        // If sessions list is empty, try to recover from session files
        if (sessions.isEmpty()) {
            recoverSessions(sessions)
            // Set the first one as current if we recovered any
            if (sessions.isNotEmpty()) {
                currentSessionName = sessions[0].name
            }
        }

        // Always update cache - even if empty, we need initialized values
        iSessions = sessions
        iCurrentSessionName = currentSessionName
        updateCurrentSessionFlags()

        Timber.d("loadSessions complete: ${sessions.size} sessions, current='$currentSessionName'")
    }

    /**
     * Force reload sessions from disk, clearing the cache.
     * Useful when sessions may have been modified externally.
     */
    fun reloadSessions() {
        loadSessions()
    }

    /**
     * Search the file system for session files and populate the sessions list.
     * Used as a recovery mechanism when the sessions bundle is lost.
     */
    private fun recoverSessions(sessions: ArrayList<Session>) {
        Timber.i("recoverSessions")

        // Search for session files
        val files = application.filesDir?.listFiles { _, name ->
            name.startsWith(FILENAME_SESSION_PREFIX)
        }

        // Add recovered sessions to our collection
        files?.forEach { f ->
            val sessionName = f.name.substring(FILENAME_SESSION_PREFIX.length)
            sessions.add(Session(sessionName, -1))
        }
    }

    /**
     * Get the file for a session name.
     */
    fun fileFromSessionName(sessionName: String): File {
        return File(application.filesDir, FILENAME_SESSION_PREFIX + sessionName)
    }

    /**
     * Check if a session file exists.
     */
    fun sessionExists(sessionName: String): Boolean {
        return fileFromSessionName(sessionName).exists()
    }

    /**
     * Get session file name from session name.
     */
    fun fileNameFromSessionName(sessionName: String): String {
        return FILENAME_SESSION_PREFIX + sessionName
    }

    /**
     * Get the index of the current session in the sessions list.
     * Returns -1 if not found.
     */
    fun currentSessionIndex(): Int {
        return iSessions.indexOfFirst { s -> s.name == iCurrentSessionName }
    }

    /**
     * Get the current session object.
     * Returns an empty Session if not found.
     */
    fun currentSession(): Session {
        return session(iCurrentSessionName)
    }

    /**
     * Provide the session matching the given name.
     * Returns an empty Session if not found.
     * TODO: have a better implementation
     */
    fun session(aName: String): Session {
        if (iSessions.isEmpty()) {
            // TODO: Return session with Default name
            return Session()
        }

        val list = iSessions.filter { s -> s.name == aName }
        if (list.isEmpty()) {
            // TODO: Return session with Default name
            return Session()
        }

        // Should only be one session item in that list
        return list[0]
    }

    /**
     * Set the current session name and persist changes.
     * This marks the new current session and updates all session states.
     */
    fun setCurrentSession(sessionName: String) {
        // Update the current session name in cache
        iCurrentSessionName = sessionName
        //
        updateCurrentSessionFlags()
        //
        saveSessions()
    }

    /**
     * Update isCurrent flag for all sessions based on current session name
     */
    fun updateCurrentSessionFlags() {
        // Mark all sessions as not current
        iSessions.forEach { s -> s.isCurrent = false }
        // Mark the new current session
        iSessions.filter { s -> s.name == iCurrentSessionName }.apply {
            if (isNotEmpty()) get(0).isCurrent = true
        }
    }

    /**
     * Save sessions list and current session name to disk.
     * This can be called independently of TabsManager initialization.
     * Uses the cached sessions and current session name.
     */
    fun saveSessions() {
        Timber.d("saveSessions")

        val bundle = Bundle(javaClass.classLoader)
        bundle.putString(KEY_CURRENT_SESSION, iCurrentSessionName)
        bundle.putParcelableArrayList(KEY_SESSIONS, iSessions)

        // Write synchronously since this is typically called during import
        FileUtils.writeBundleToStorage(application, bundle, FILENAME_SESSIONS)
    }

    /**
     * Delete a session by name.
     * Deletes the session file and removes it from the sessions list.
     * Cannot delete the current session.
     *
     * @param aSessionName The name of the session to delete.
     * @return true if the session was deleted, false if it couldn't be deleted (e.g., it's the current session).
     */
    fun deleteSession(aSessionName: String): Boolean {
        // TODO: handle case where we delete current session
        if (aSessionName == iCurrentSessionName) {
            // Can't do that for now
            return false
        }

        val index = iSessions.indexOf(session(aSessionName))

        if (index < 0) {
            // Session not found
            return false
        }

        // Delete session file
        FileUtils.deleteBundleInStorage(application, fileNameFromSessionName(iSessions[index].name))

        // Remove session from our list
        iSessions.removeAt(index)

        // Save updated sessions list
        saveSessions()

        return true
    }

    /**
     * Check if the given string is a valid session name
     */
    fun isValidSessionName(aName: String): Boolean {
        // Empty strings are not valid names
        if (aName.isBlank()) {
            return false
        }

        return if (iSessions.isEmpty()) {
            // Null or empty session list so that name is valid
            true
        } else {
            // That name is valid if not already in use
            iSessions.none { s -> s.name == aName }
        }
    }

    /**
     * Rename the session [aOldName] to [aNewName].
     * Takes care of checking parameters validity before proceeding.
     * Changes current session name if needed.
     * Rename matching session data file too.
     * Commit session list changes to persistent storage.
     *
     * @param [aOldName] Name of the session to rename in our session list.
     * @param [aNewName] New name to be assumed by specified session.
     */
    fun renameSession(aOldName: String, aNewName: String) {
        Timber.d("Try rename session $aOldName to $aNewName")

        val index = iSessions.indexOf(session(aOldName))
        Timber.d("Session index $index")

        // Check if we can indeed rename that session
        if (iSessions.isEmpty() // Check if we have sessions at all
                || !isValidSessionName(aNewName) // Check if new session name is valid
                || !(index>=0 && index<iSessions.count())) { // Check if index is in range
            Timber.d("Session rename aborted")
            return
        }

        // Proceed with rename then
        val oldName = iSessions[index].name
        // Change session name
        iSessions[index].name = aNewName

        // Renamed session is the current session
        val needsCurrentSessionUpdate = iCurrentSessionName == oldName

        Timber.d("Rename session files $oldName to $aNewName")
        // Rename our session file
        FileUtils.renameBundleInStorage(application, fileNameFromSessionName(oldName), fileNameFromSessionName(aNewName))

        // I guess it makes sense to persist our changes
        // Update current session name if needed
        if (needsCurrentSessionUpdate) {
            iCurrentSessionName = aNewName
            updateCurrentSessionFlags()
        }

        saveSessions()
    }

    /**
     * Clear all saved sessions data from disk and memory.
     * Used for panic clean functionality.
     */
    fun clearAllSavedState() {
        Timber.d("clearAllSavedState")

        // Delete all session files
        val files = application.filesDir?.listFiles { _, name ->
            name.startsWith(FILENAME_SESSION_PREFIX)
        }
        files?.forEach { it.delete() }

        // Delete sessions list file
        FileUtils.deleteBundleInStorage(application, FILENAME_SESSIONS)

        // Clear cache
        iSessions.clear()
        iCurrentSessionName = ""
    }
}

