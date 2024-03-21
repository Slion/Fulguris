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
import fulguris.settings.preferences.PreferenceCategoryEx
import fulguris.utils.IntentUtils
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.XmlRes
import androidx.core.content.res.ResourcesCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * An abstract settings fragment which performs wiring for an instance of [PreferenceFragment].
 */
abstract class AbstractSettingsFragment : PreferenceFragmentCompat() {

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

    /**
     * Called by the framework once our view has been created from its XML definition.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enable fading edge when scrolling settings, looks much better
        view.findViewById<RecyclerView>(R.id.recycler_view)?.apply{
            isVerticalFadingEdgeEnabled = true
        }
    }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        super.onNavigateToScreen(preferenceScreen)
        Timber.d("onNavigateToScreen")
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
    ): SwitchPreferenceCompat = (findPreference<SwitchPreferenceCompat>(preference) as SwitchPreferenceCompat).apply {
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
     *
     */
    fun addCategoryContribute() {
        val prefCat = PreferenceCategoryEx(requireContext())
        prefCat.key = getString(R.string.pref_key_contribute_category)
        prefCat.title = getString(R.string.settings_contribute)
        //prefCat.summary = getString(R.string.pref_summary_subscriptions)
        prefCat.order = 1 // Important so that it comes after the subscriptions category
        prefCat.isIconSpaceReserved = true
        preferenceScreen.addPreference(prefCat)
        prefGroup = prefCat
    }

    /**
     * Add a preference that links to GitHub sponsor.
     */
    protected fun addPreferenceLinkToGitHubSponsor() {
        // We invite user to installer our Google Play Store release
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.title = resources.getString(R.string.pref_title_sponsorship_github)
        pref.summary = resources.getString(R.string.pref_summary_sponsorship_github)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_github_mark, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open up Fulguris play store page
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sponsors/Slion")))
            true
        }
        prefGroup.addPreference(pref)
    }

    /**
     * Add a preference that opens up our play store page.
     */
    protected fun addPreferenceLinkToGooglePlayStoreFiveStarsReview() {
        // We invite user to installer our Google Play Store release
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.title = resources.getString(R.string.pref_title_sponsorship_five_stars)
        pref.summary = resources.getString(R.string.pref_summary_sponsorship_five_stars)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_star_full, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open up Fulguris play store page
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.slions.fulguris.full.playstore")))
            true
        }
        prefGroup.addPreference(pref)
    }

    /**
     * Add a preference that opens up our Crowdin project page.
     */
    protected fun addPreferenceLinkToCrowdin() {
        // We invite user to installer our Google Play Store release
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.title = resources.getString(R.string.pref_title_contribute_translations)
        pref.summary = resources.getString(R.string.pref_summary_contribute_translations)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_translate, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open up Fulguris Crowdin project page
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdin.com/project/fulguris-web-browser")))
            true
        }
        prefGroup.addPreference(pref)
    }

    /**
     * Add a preference to share Fulguris.
     */
    protected fun addPreferenceShareLink() {
        // We invite user to installer our Google Play Store release
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.title = resources.getString(R.string.pref_title_contribute_share)
        pref.summary = resources.getString(R.string.pref_summary_contribute_share)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_share, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Share Fulguris
            fulguris.utils.IntentUtils(requireActivity()).shareUrl(getString(R.string.url_app_home_page), getString(R.string.locale_app_name),R.string.pref_title_contribute_share)
            true
        }
        prefGroup.addPreference(pref)
    }

    abstract fun titleResourceId() : Int

    open fun title() : String = resources.getString(titleResourceId())

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference) {
            showListPreferenceDialog(preference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

}
