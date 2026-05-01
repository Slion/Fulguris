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
package fulguris.settings.fragment

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.widget.EditText
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.browser.SessionsManager
import fulguris.dialog.BrowserDialog
import fulguris.extensions.launch
import fulguris.extensions.snackbar
import fulguris.settings.preferences.ProfilePreferences
import fulguris.utils.FileNameInputFilter
import timber.log.Timber
import javax.inject.Inject

/**
 * Detail page for a single profile.
 *
 * Shows:
 *  - Rename action (hidden for Default)
 *  - Delete action (hidden for Default)
 *  - Sessions category: each session as a SwitchPreference.
 *    Toggle ON  → assign the session to this profile.
 *    Toggle OFF → remove explicit assignment; session falls back to Default.
 *    For the Default profile switches are informational only (disabled).
 *
 * Receives the profile name via [ARG_PROFILE] in [arguments].
 * See https://github.com/Slion/Fulguris/issues/618
 */
@AndroidEntryPoint
class ProfileDetailSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var sessionsManager: SessionsManager

    private lateinit var profileName: String
    private var catSessions: PreferenceCategory? = null

    override fun titleResourceId(): Int = 0 // dynamic — set by the preference item's title

    override fun providePreferencesXmlResource(): Int = R.xml.preference_profile_detail

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        profileName = arguments?.getString(ARG_PROFILE) ?: ProfilePreferences.DEFAULT

        // Graceful: if the profile was deleted since we navigated here, go back.
        if (profileName != ProfilePreferences.DEFAULT && !ProfilePreferences.exists(profileName)) {
            Timber.w("ProfileDetailSettingsFragment: profile '$profileName' no longer exists, popping back")
            (parentFragment as? ResponsiveSettingsFragment)?.popBackStackWithBreadcrumbs()
            return
        }

        val isDefault = profileName == ProfilePreferences.DEFAULT

        catSessions = findPreference(getString(R.string.pref_key_profile_sessions))

        if (isDefault) {
            // Rename and delete are not available for the Default profile.
            findPreference<Preference>(getString(R.string.pref_key_profile_rename))?.isVisible = false
            findPreference<Preference>(getString(R.string.pref_key_profile_delete))?.isVisible = false
        } else {
            clickablePreference(preference = getString(R.string.pref_key_profile_rename)) {
                showRenameDialog()
                true
            }
            clickablePreference(preference = getString(R.string.pref_key_profile_delete)) {
                showDeleteDialog()
                true
            }
        }
        // Clear actions are available for all profiles, including Default.
        clickablePreference(preference = getString(R.string.pref_key_profile_clear)) {
            showClearAllDialog()
            true
        }
        clickablePreference(preference = getString(R.string.pref_key_profile_clear_cookies)) {
            showClearCookiesDialog()
            true
        }
        clickablePreference(preference = getString(R.string.pref_key_profile_clear_storage)) {
            showClearStorageDialog()
            true
        }
        clickablePreference(preference = getString(R.string.pref_key_profile_clear_geolocation)) {
            showClearGeolocationDialog()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        repopulateSessions()
    }

    // ---- Sessions list ---------------------------------------------------------

    private var repopulateCount = 0

    private fun repopulateSessions() {
        repopulateCount++
        val callNum = repopulateCount
        val cat = catSessions ?: return
        cat.removeAll()

        val sessions = sessionsManager.sessions()

        if (sessions.isEmpty()) {
            cat.isVisible = false
            return
        }

        cat.isVisible = true
        sessions.forEach { session ->
            val assignedProfile = ProfilePreferences.sessionProfile(session.name)
            val isThisProfile = assignedProfile == profileName
            Timber.d("repopulateSessions[$callNum]: profile='$profileName' session='${session.name}' assignedProfile='$assignedProfile' isThisProfile=$isThisProfile")

            x.SwitchPreference(requireContext()).apply {
                key = session.name
                val tabCount = session.tabCount.takeIf { it >= 0 }
                title = if (tabCount != null) "${session.name}  ($tabCount)" else session.name
                isPersistent = false
                isIconSpaceReserved = false
                summary = assignedProfile
                setOnPreferenceChangeListener { _, newValue ->
                    val turningOff = !(newValue as Boolean)
                    if (turningOff && profileName == ProfilePreferences.DEFAULT) {
                        // Can't unassign from Default here — the user must go to the target
                        // profile and enable the session there.
                        activity?.snackbar(getString(R.string.profile_session_assign_hint))
                        return@setOnPreferenceChangeListener false
                    }
                    if (newValue) {
                        ProfilePreferences.setSessionProfile(session.name, profileName)
                    } else {
                        // Unassign — session falls back to Default.
                        ProfilePreferences.setSessionProfile(session.name, ProfilePreferences.DEFAULT)
                    }
                    repopulateSessions()
                    // Return true so the framework doesn't revert the visual state after
                    // repopulateSessions() has already rebuilt the list with correct values.
                    // isPersistent = false ensures nothing is written to SharedPreferences.
                    true
                }
            }.also {
                cat.addPreference(it)
                // isChecked MUST be set after addPreference: addPreference triggers
                // onSetInitialValue() which calls setChecked() and would override any
                // value set inside the apply block above.
                it.isChecked = isThisProfile
                Timber.d("repopulateSessions[$callNum]: after addPreference session='${session.name}' it.isChecked=${it.isChecked}")
            }
        }
    }

    // ---- Dialogs ---------------------------------------------------------------

    private fun showRenameDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_text, null)
        val textView = dialogView.findViewById<EditText>(R.id.dialog_edit_text)
        textView.filters = arrayOf<InputFilter>(FileNameInputFilter())
        textView.setText(profileName)

        BrowserDialog.showCustomDialog(requireActivity()) {
            setTitle(R.string.profile_name_prompt)
            setView(dialogView)
            setPositiveButton(R.string.action_ok) { _, _ ->
                val newName = textView.text.toString().trim()
                if (newName == profileName) return@setPositiveButton
                if (ProfilePreferences.isValidName(newName)) {
                    if (ProfilePreferences.activeProfile == profileName) {
                        ProfilePreferences.activate(newName)
                    }
                    ProfilePreferences.rename(profileName, newName)
                    profileName = newName
                    // Update the breadcrumb title shown in the toolbar.
                    preferenceScreen.title = profileName
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.profile_already_exists)
                        .setPositiveButton(R.string.action_ok, null)
                        .launch()
                }
            }
            setNegativeButton(R.string.action_cancel, null)
        }
    }

    private fun showDeleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_forever_outline)
            .setTitle(R.string.dialog_title_profile_deletion)
            .setMessage(getString(R.string.dialog_message_profile_deletion, profileName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (ProfilePreferences.activeProfile == profileName) {
                    ProfilePreferences.activate(ProfilePreferences.DEFAULT)
                }
                ProfilePreferences.delete(profileName)
                (parentFragment as? ResponsiveSettingsFragment)?.popBackStackWithBreadcrumbs()
            }
            .launch()
    }

    private fun showClearAllDialog() {
        val storeName = ProfilePreferences.toProfileStoreName(profileName)
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_action_delete)
            .setTitle(R.string.pref_title_profile_clear)
            .setMessage(getString(R.string.dialog_message_profile_clear, profileName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val profile = ProfileStore.getInstance().getOrCreateProfile(storeName)
                    profile.getCookieManager().removeAllCookies(null)
                    profile.getWebStorage().deleteAllData()
                    profile.getGeolocationPermissions().clearAll()
                }
            }
            .launch()
    }

    private fun showClearCookiesDialog() {
        val storeName = ProfilePreferences.toProfileStoreName(profileName)
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_action_delete)
            .setTitle(R.string.pref_title_profile_clear_cookies)
            .setMessage(getString(R.string.dialog_message_profile_clear_cookies, profileName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getOrCreateProfile(storeName)
                        .getCookieManager().removeAllCookies(null)
                }
            }
            .launch()
    }

    private fun showClearStorageDialog() {
        val storeName = ProfilePreferences.toProfileStoreName(profileName)
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_action_delete)
            .setTitle(R.string.pref_title_profile_clear_storage)
            .setMessage(getString(R.string.dialog_message_profile_clear_storage, profileName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getOrCreateProfile(storeName)
                        .getWebStorage().deleteAllData()
                }
            }
            .launch()
    }

    private fun showClearGeolocationDialog() {
        val storeName = ProfilePreferences.toProfileStoreName(profileName)
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_action_delete)
            .setTitle(R.string.pref_title_profile_clear_geolocation)
            .setMessage(getString(R.string.dialog_message_profile_clear_geolocation, profileName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().getOrCreateProfile(storeName)
                        .getGeolocationPermissions().clearAll()
                }
            }
            .launch()
    }

    companion object {
        const val ARG_PROFILE = "profile_name"
        const val ARG_PROFILE_DEFAULT = "Default"
    }
}
