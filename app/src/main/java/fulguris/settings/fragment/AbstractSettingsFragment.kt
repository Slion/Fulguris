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

package fulguris.settings.fragment

import fulguris.R
import fulguris.activity.SettingsActivity
import fulguris.extensions.flash
import android.os.Bundle
import androidx.annotation.XmlRes
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import x.PreferenceFragmentBase
import timber.log.Timber

// Intent extra key for passing preference key to flash
const val PREFERENCE_KEY = "PREFERENCE"

/**
 * An abstract settings fragment which performs wiring for an instance of [PreferenceFragmentBase].
 */
abstract class AbstractSettingsFragment : PreferenceFragmentBase() {

    lateinit var prefGroup: PreferenceGroup

    /**
     * Provide the XML resource which holds the preferences.
     */
    @XmlRes
    protected abstract fun providePreferencesXmlResource(): Int

    /**
     *
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // TODO: Override this method so that our inflater setDefaultPackage can be set and thus we shorten the names our XML tags
        setPreferencesFromResource(providePreferencesXmlResource(),rootKey)
        prefGroup = preferenceScreen

        // Hide back button preference in settings activity
        if (activity is SettingsActivity
            // Also hide back button when there is nothing to go back to
            // Notably the case when SSL error settings is set to abort and snackbar is shown with direct access to domain settings
            || parentFragmentManager.backStackEntryCount == 0) {
            // Back buttons are there for navigation in options menu bottom sheet
            findPreference<Preference>(getString(R.string.pref_key_back))?.isVisible = false
        }
    }

    /**
     * SL: Start here to be able to inflate using shorter XML element tag using setDefaultPackage.
     * The inflater class would need to be duplicated though.
     *
     * Inflates the given XML resource and replaces the current preference hierarchy (if any) with
     * the preference hierarchy rooted at `key`.
     *
     * @param preferencesResId The XML resource ID to inflate
     * @param key              The preference key of the [PreferenceScreen] to use as the
     * root of the preference hierarchy, or `null` to use the root
     * [PreferenceScreen].
     */
    /*
    @SuppressLint("RestrictedApi")
    override fun setPreferencesFromResource(@XmlRes preferencesResId: Int, key: String?) {

        val xmlRoot = preferenceManager.inflateFromResource(
            requireContext(),
            preferencesResId, null
        )
        val root: Preference?
        if (key != null) {
            root = xmlRoot.findPreference(key)
            require(root is PreferenceScreen) {
                ("Preference object with key " + key
                        + " is not a PreferenceScreen")
            }
        } else {
            root = xmlRoot
        }
        preferenceScreen = root
    }
    */

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        super.onNavigateToScreen(preferenceScreen)
        Timber.d("onNavigateToScreen")
    }

    /**
     * Checks if a preference key was passed via intent extra and flashes that preference.
     * Should be called after preferences are populated (e.g., in onResume).
     *
     * @param times Number of times to flash (default: 2)
     * @param delayMs Delay in milliseconds before starting flash and between flashes (default: 500ms initial, 800ms between)
     */
    protected fun flashPreferenceIfRequested(times: Int = 2, delayMs: Long = 800, initialDelayMs: Long = 500) {
        val preferenceKey = activity?.intent?.getStringExtra(PREFERENCE_KEY)
        Timber.d("${this::class.simpleName}: flashPreferenceIfRequested - $PREFERENCE_KEY extra = $preferenceKey")

        preferenceKey?.let { key ->
            Timber.d("${this::class.simpleName}: Attempting to flash preference with key: $key")

            // Flash the preference to draw attention to it
            view?.postDelayed({
                // Check if preference exists
                val pref = findPreference<Preference>(key)
                Timber.d("${this::class.simpleName}: Found preference: ${pref != null}, key: $key")

                if (pref != null) {
                    // Scroll to the preference first to ensure it's visible
                    scrollToPreference(pref)

                    // Then flash it after a short delay
                    view?.postDelayed({
                        flash(key, times, delayMs)
                    }, 200)
                } else {
                    Timber.w("${this::class.simpleName}: Could not find preference with key: $key")
                }

                // Clear the extra so we don't flash again on configuration change
                activity?.intent?.removeExtra(PREFERENCE_KEY)
            }, initialDelayMs)
        }
    }


    /**
     * Creates a [CheckBoxPreference] with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isChecked true if it should be initialized as checked, false otherwise.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onCheckChange the function that should be called when the check box is toggled.
     */
    protected fun checkBoxPreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit
    ): CheckBoxPreference = (findPreference<CheckBoxPreference>(preference) as CheckBoxPreference).apply {
        this.isChecked = isChecked
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, any: Any ->
            onCheckChange(any as Boolean)
            true
        }
    }

    /**
     * Creates a simple [Preference] which reacts to clicks with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onClick the function that should be called when the preference is clicked.
     */
    protected fun clickablePreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: (() -> Boolean)? = null
    ): Preference = clickableDynamicPreference(
        preference = preference,
        isEnabled = isEnabled,
        summary = summary,
        onClick = onClick?.let {{_: SummaryUpdater -> it.invoke()}}
    )

    /**
     * Creates a simple [Preference] which reacts to clicks with the provided options and listener.
     * It also allows its summary to be updated when clicked.
     *
     * @param preference the preference to create.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onClick the function that should be called when the preference is clicked. The
     * function is supplied with a [SummaryUpdater] object so that it can update the summary if
     * desired.
     */
    protected fun clickableDynamicPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: ((SummaryUpdater) -> Boolean)?
    ): Preference = (findPreference<Preference>(preference) as Preference).apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }

        if (onClick!=null) {
            val summaryUpdate = SummaryUpdater(this)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                onClick(summaryUpdate)
            }
        }
    }

    /**
     * Creates a [SwitchPreference] with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isChecked true if it should be initialized as checked, false otherwise.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param onCheckChange the function that should be called when the toggle is toggled.
     */
    protected fun switchPreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        isVisible: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit
    ): x.SwitchPreference = (findPreference<x.SwitchPreference>(preference) as x.SwitchPreference).apply {
        this.isChecked = isChecked
        this.isEnabled = isEnabled
        this.isVisible = isVisible
        summary?.let {
            this.summary = summary
        }
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, any: Any ->
            onCheckChange(any as Boolean)
            true
        }
    }

    /**
     * Setup a [ListPreference] with the provided options and listener.
     *
     * @param preference Preference key.
     * @param value Default string value, typically an enum class value converted to string.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param onPreferenceChange Callback function used when that preference value is changed.
     */
    protected fun listPreference(
            preference: String,
            value: String,
            isEnabled: Boolean = true,
            onPreferenceChange: (String) -> Unit
    ): ListPreference = (findPreference<ListPreference>(preference) as ListPreference).apply {
        this.value = value
        this.isEnabled = isEnabled
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, any: Any ->
            onPreferenceChange(any as String)
            true
        }
    }


    /**
     * Hook-in our own list preference dialog.
     * Thus ours dialog are constructed using MaterialAlertDialogBuilder rather than the legacy dialog builder.
     * TODO: Support more than just ListPreference, notably text editor dialog for instance.
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference) {
            showListPreferenceDialog(preference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

}
