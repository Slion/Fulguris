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
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.dialog.BrowserDialog
import fulguris.extensions.launch
import fulguris.settings.preferences.ProfilePreferences
import fulguris.utils.FileNameInputFilter
import timber.log.Timber

/**
 * Settings page listing all profiles.
 *
 * Each profile is shown as a [DetailSwitchPreference]:
 *  - The **switch** activates that profile (radio-button style: only one is on at a time).
 *    The currently active profile's switch cannot be turned off directly.
 *  - **Clicking the item** opens a rename / delete action dialog.
 *
 * See https://github.com/Slion/Fulguris/issues/618
 */
@AndroidEntryPoint
class ProfilesSettingsFragment : AbstractSettingsFragment() {

    private var catProfiles: PreferenceCategory? = null

    // keyed by profile name
    private val profilePrefs = mutableMapOf<String, DetailSwitchPreference>()

    override fun titleResourceId(): Int = R.string.pref_title_profiles

    override fun providePreferencesXmlResource(): Int = R.xml.preference_profiles

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        catProfiles = findPreference(getString(R.string.pref_key_profiles))

        ProfilePreferences.ensureDefaultExists()

        clickablePreference(
            preference = getString(R.string.pref_key_add_profile),
            onClick = {
                showAddProfileDialog()
                true
            }
        )

        clickablePreference(
            preference = getString(R.string.pref_key_delete_all_profiles),
            onClick = {
                MaterialAlertDialogBuilder(requireContext())
                    .setCancelable(true)
                    .setIcon(R.drawable.ic_delete_forever_outline)
                    .setTitle(R.string.question_delete_all_profiles)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        ProfilePreferences.deleteAll()
                        ProfilePreferences.ensureDefaultExists()
                        ProfilePreferences.activate(ProfilePreferences.DEFAULT)
                        repopulate()
                    }
                    .launch()
                true
            }
        )
    }

    override fun onResume() {
        super.onResume()
        repopulate()
    }

    // ---- Dialogs ----------------------------------------------------------------

    private fun showAddProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_text, null)
        val textView = dialogView.findViewById<EditText>(R.id.dialog_edit_text)
        textView.filters = arrayOf<InputFilter>(FileNameInputFilter())

        BrowserDialog.showCustomDialog(requireActivity()) {
            setTitle(R.string.profile_name_prompt)
            setView(dialogView)
            setPositiveButton(R.string.action_ok) { _, _ ->
                val name = textView.text.toString().trim()
                if (ProfilePreferences.isValidName(name)) {
                    ProfilePreferences.create(name)
                    repopulate()
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

    /** Opens a rename/delete action menu for [name]. Not shown for the Default profile. */
    private fun showProfileActionsDialog(name: String) { /* moved to ProfileDetailSettingsFragment */ }

    // ---- List management -------------------------------------------------------

    /** Rebuild the profile list from disk. */
    private fun repopulate() {
        val cat = catProfiles ?: return
        cat.removeAll()
        profilePrefs.clear()

        val active = ProfilePreferences.activeProfile
        // Default always first, then remaining profiles sorted alphabetically.
        val profiles = ProfilePreferences.list()
            .sortedWith(compareBy { if (it == ProfilePreferences.DEFAULT) 0 else 1 })
        Timber.d("repopulate: active='$active' profiles=$profiles")

        profiles.forEach { name ->
            val isDefault = name == ProfilePreferences.DEFAULT
            val isActive = name == active
            val pref = DetailSwitchPreference(
                context = requireContext(),
                onSwitchChanged = { enabled ->
                    if (!enabled) {
                        // Can't turn off the active profile directly — snap it back.
                        profilePrefs[name]?.isChecked = true
                    } else {
                        ProfilePreferences.activate(name)
                        repopulate()
                    }
                },
                onPreferenceClicked = null // item tap navigates to detail via pref.fragment
            ).apply {
                key = name
                title = name
                isSingleLineTitle = false
                // Don't persist switch state — we manage it ourselves via ProfilePreferences.
                isPersistent = false
                // Navigate to profile detail page on item tap.
                fragment = ProfileDetailSettingsFragment::class.java.name
                extras.putString(ProfileDetailSettingsFragment.ARG_PROFILE, name)
            }
            profilePrefs[name] = pref
            cat.addPreference(pref)
            // isChecked MUST be set after addPreference: addPreference triggers
            // onSetInitialValue() which resets the checked state to false.
            pref.isChecked = isActive
            Timber.d("repopulate: profile='$name' isActive=$isActive pref.isChecked=${pref.isChecked}")
        }
    }
}
