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
import fulguris.activity.ThemedActivity
import fulguris.di.MainScheduler
import fulguris.di.NetworkScheduler
import fulguris.di.UserPrefs
import fulguris.extensions.isDarkTheme
import fulguris.extensions.launch
import fulguris.extensions.reverseDomainName
import fulguris.favicon.FaviconModel
import fulguris.settings.preferences.DomainPreferences
import slions.pref.BasicPreference
import fulguris.utils.Utils
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.app
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Settings page displaying all visited domains to enable access to domain settings
 */
@AndroidEntryPoint
class DomainsSettingsFragment : AbstractSettingsFragment() {

    @Inject
    @UserPrefs
    internal lateinit var preferences: SharedPreferences

    @Inject internal lateinit var faviconModel: FaviconModel

    @Inject @NetworkScheduler
    internal lateinit var networkScheduler: Scheduler
    @Inject @MainScheduler
    internal lateinit var mainScheduler: Scheduler

    private var catDomains: PreferenceCategory? = null
    private var deleteAll: Preference? = null

    // Track how many domains with overrides we have
    private var domainCount = 0

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_domains
    }

    // Set to the domain settings the user last opened
    var domain: String = ""


    override fun providePreferencesXmlResource() = R.xml.preference_domains

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // TODO: Have a custom preference with test input to filter out our list

        // Disable ordering as added to sort alphabetically by title
        catDomains = findPreference<PreferenceCategory>(resources.getString(R.string.pref_key_domains))?.apply {
            isOrderingAsAdded = false
            // Initial summary will be updated after scanning
            summary = getString(R.string.settings_summary_loading)
        }

        deleteAll = clickablePreference(
            preference = getString(R.string.pref_key_delete_all_domains_settings),
            onClick = {
                // Show confirmation dialog and proceed if needed
                MaterialAlertDialogBuilder(requireContext())
                    .setCancelable(true)
                    .setTitle(R.string.question_delete_all_domain_settings)
                    //.setMessage(R.string.prompt_please_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        DomainPreferences.deleteAll(requireContext())
                        catDomains?.removeAll()
                        domainCount = 0
                        updateDomainCountSummary()
                    }
                    .launch()

                true
            }
        ).apply { isVisible = false }
    }

    /**
     * Update the domains category summary with current domain count.
     * Note: English doesn't support quantity="zero" in plurals, so we handle it explicitly.
     */
    private fun updateDomainCountSummary() {
        catDomains?.summary = if (domainCount == 0) {
            getString(R.string.settings_summary_no_overrides)
        } else {
            resources.getQuantityString(
                R.plurals.settings_summary_overrides,
                domainCount,
                domainCount
            )
        }
    }


    /**
     *
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateDomainList(view)
    }

    var populated = false

    /**
     *
     */
    @SuppressLint("CheckResult")
    fun populateDomainList(view: View) {

        // Make sure we only do that once
        // Otherwise we would populate duplicates each time we come back from a domain settings page
        if (populated) {
            Timber.d("populateDomainList populated")

            if (domain.isEmpty()) {
                // Happens if visiting default domain settings
                return
            }

            // Update the summary for the domain we just came back from
            // This shows any changes made to overrides
            if (DomainPreferences.exists(domain)) {
                findPreference<Preference>(domain)?.let { pref ->
                    val domainPref = DomainPreferences(requireContext(), domain)
                    pref.summary = domainPref.getOverridesSummary(requireContext())
                }
            }

            // Check if our domain was deleted when coming back from a specific domain preference page
            if (!DomainPreferences.exists(domain)) {
                // Domain settings was deleted, remove the preference then
                findPreference<Preference>(domain)?.let {
                    it.parent?.removePreference(it)
                    domainCount--
                    updateDomainCountSummary()
                }
            }

            // Handle parent domain changes (created or deleted)
            val tpd = "http://$domain".toHttpUrl().topPrivateDomain()
            if (tpd?.isNotEmpty() == true) {
                if (!DomainPreferences.exists(tpd)) {
                    // Parent domain was deleted, remove its preference
                    findPreference<Preference>(tpd)?.let {
                        it.parent?.removePreference(it)
                        domainCount--
                        updateDomainCountSummary()
                    }
                } else {
                    // Parent domain exists - check if preference already exists
                    val existingPref = findPreference<Preference>(tpd)
                    if (existingPref != null) {
                        // Update existing parent domain summary
                        val domainPref = DomainPreferences(requireContext(), tpd)
                        existingPref.summary = domainPref.getOverridesSummary(requireContext())
                    } else {
                        // Parent domain was just created - add it to the list
                        val domainPref = DomainPreferences(requireContext(), tpd)
                        if (domainPref.hasAnyOverrides()) {
                            Timber.d("Adding newly created parent domain: $tpd")

                            // Create new preference for parent domain
                            val pref = BasicPreference(requireContext())
                            pref.isSingleLineTitle = false
                            pref.key = tpd
                            pref.title = tpd
                            pref.summary = domainPref.getOverridesSummary(requireContext())
                            pref.breadcrumb = tpd
                            pref.fragment = "fulguris.settings.fragment.DomainSettingsFragment"
                            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                                this.domain = tpd
                                app.domain = tpd
                                false
                            }

                            // Add favicon
                            faviconModel.faviconForUrl("http://$tpd", "", (activity as? ThemedActivity)?.isDarkTheme() == true)
                                .subscribeOn(networkScheduler)
                                .observeOn(mainScheduler)
                                .subscribeBy(
                                    onSuccess = { bitmap ->
                                        pref.icon = bitmap.scale(Utils.dpToPx(24f), Utils.dpToPx(24f)).toDrawable(resources)
                                    }
                                )

                            catDomains?.addPreference(pref)
                            domainCount++
                            updateDomainCountSummary()
                        }
                    }
                }
            }
            return
        }

        populated = true

        // Add default domain settings
        val prefDefault = BasicPreference(requireContext())
        prefDefault.isSingleLineTitle = false
        prefDefault.title = getString(R.string.default_theme)
        prefDefault.summary = getString(R.string.settings_summary_default_domain_settings)
        prefDefault.breadcrumb = prefDefault.summary!!
        prefDefault.order = 5
        prefDefault.fragment = "fulguris.settings.fragment.DomainSettingsFragment"
        prefDefault.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            domain = ""
            app.domain = ""
            false
        }
        preferenceScreen.addPreference(prefDefault)

        // Fetch category to add

        // Build our list of known domains
        val directory = File(requireContext().applicationInfo.dataDir, "shared_prefs")
        if (directory.exists() && directory.isDirectory) {
            val list = directory.list { _, name -> name.startsWith(DomainPreferences.prefix) }
            // Sorting is not needed anymore as we let the PreferenceGroup do it for us now
            //list?.sortWith ( compareBy(String.CASE_INSENSITIVE_ORDER){it})
            // Sort our domains using reversed string so that subdomains are grouped together
            var delay = 300L
            val delayIncrement = 1L
            // Reset domain count before scanning
            domainCount = 0

            // Fill our list asynchronously
            list?.forEach {
                view.postDelayed({
                    // Create preferences entry
                    // Workout our domain name from the file name, skip [Domain] prefix and drop .xml suffix
                    val domainReverse = it.substring(DomainPreferences.prefix.length).dropLast(4)
                    val domain = domainReverse.reverseDomainName
                    //Timber.d(title.reverseDomainName)

                    if (domainReverse.isEmpty()) {
                        // We skip the default preferences as it was already added above
                        return@postDelayed
                    }

                    // Check if this domain has any overrides, delete if none
                    // Depollute existing installations as it should not be needed for new ones
                    val domainPref = DomainPreferences(requireContext(), domain)
                    if (domainPref.deleteIfNoOverrides()) {
                        // Domain settings was deleted, don't add it to the list
                        Timber.d("Deleted domain settings with no overrides: $domain")
                        return@postDelayed
                    }

                    // Count this domain
                    domainCount++

                    // Update summary as we add domains to show progress
                    updateDomainCountSummary()

                    // Create domain preference
                    val pref = BasicPreference(requireContext())
                    // Make sure domains are shown as titles
                    pref.isSingleLineTitle = false
                    pref.key = domain
                    // Use reversed domain as title for sorting
                    // Sorting by reversed domain makes sure subdomains and parent are grouped together
                    pref.title = domainReverse
                    // But actually show the domain name
                    pref.displayedTitle = domain
                    // Show active overrides in summary
                    pref.summary = domainPref.getOverridesSummary(requireContext())
                    pref.breadcrumb = domain
                    pref.fragment = "fulguris.settings.fragment.DomainSettingsFragment"
                    pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        this.domain = domain
                        app.domain = domain
                        false
                    }

                    faviconModel.faviconForUrl("http://$domain","",(activity as? ThemedActivity)?.isDarkTheme() == true)
                        .subscribeOn(networkScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy(
                            onSuccess = { bitmap ->
                                pref.icon = bitmap.scale(fulguris.utils.Utils.dpToPx(24f), fulguris.utils.Utils.dpToPx(24f)).toDrawable(resources)
                            }
                        )

                    catDomains?.addPreference(pref)

                },delay)
                // We could lower or just remove the delay as it seems to be smooth without it too
                // However having a bit of a delay does make it look prettier
                delay+=delayIncrement
            }

            // Show delete button once our list is parsed and update summary
            view.postDelayed({
                deleteAll?.isVisible = true
                // Update category summary with domain count
                // Note: English doesn't support quantity="zero" in plurals
                updateDomainCountSummary()
            }, delay)
        }
    }

}
