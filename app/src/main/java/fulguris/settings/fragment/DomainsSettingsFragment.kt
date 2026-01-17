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
    private var clearLocation: Preference? = null

    // Track how many domains with overrides we have
    private var domainCount = 0

    // Track if we've already populated the list on first load
    private var populated = false

    // Set to the domain settings the user last opened
    var domain: String = ""

    // Track if we have any preference files
    private var hasPreferenceFiles = false
    // Track if we have any geolocation origins
    private var hasGeolocationOrigins = false

    companion object {
        // Delay increment for staggered domain preference creation (in milliseconds)
        private const val delayIncrement = 1L
        // Initial delay before starting domain creation
        private const val initialDelay = 300L
    }

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.pref_title_domains
    }

    override fun providePreferencesXmlResource() = R.xml.preference_domains

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // TODO: Have a custom preference with test input to filter out our list

        // Disable ordering as added to sort alphabetically by title
        catDomains = findPreference<x.PreferenceCategory>(resources.getString(R.string.pref_key_domains))?.apply {
            isOrderingAsAdded = false
            // Initial summary will be updated after scanning
            summary = getString(R.string.pref_summary_loading)
        }

        deleteAll = clickablePreference(
            preference = getString(R.string.pref_key_delete_all_domains_settings),
            onClick = {
                // Show confirmation dialog and proceed if needed
                MaterialAlertDialogBuilder(requireContext())
                    .setCancelable(true)
                    .setTitle(R.string.question_delete_all_domain_settings)
                    .setIcon(R.drawable.ic_delete_forever_outline)
                    //.setMessage(R.string.prompt_please_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        DomainPreferences.deleteAll(requireContext())
                        hasPreferenceFiles = false
                        deleteAll?.isEnabled = false
                        // Update the list to remove domains that no longer have settings files
                        updateDomainList()
                    }
                    .launch()

                true
            }
        ).apply { isEnabled = false }

        // Hook in delete all location permissions
        clearLocation = clickablePreference(
            preference = getString(R.string.pref_key_clear_location_permissions),
            onClick = {
                // Show confirmation dialog and proceed if needed
                MaterialAlertDialogBuilder(requireContext())
                    .setCancelable(true)
                    .setTitle(R.string.question_clear_location_permissions)
                    .setIcon(R.drawable.ic_wrong_location_outline)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_reset) { _, _ ->
                        clearGeolocationPermissions()
                        hasGeolocationOrigins = false
                        clearLocation?.isEnabled = false
                        // Update the list to remove domains that no longer have geolocation permissions
                        updateDomainList()
                    }
                    .launch()

                true
            }
        ).apply { isEnabled = false }
    }

    /**
     * Update the domains category summary with current domain count.
     * Note: English doesn't support quantity="zero" in plurals, so we handle it explicitly.
     */
    private fun updateDomainCountSummary() {
        catDomains?.summary = if (domainCount == 0) {
            getString(R.string.pref_summary_no_overrides)
        } else {
            resources.getQuantityString(
                R.plurals.pref_summary_overrides,
                domainCount,
                domainCount
            )
        }
    }

    /**
     * Update the enabled state of delete all and clear location buttons.
     * Checks if there are any preference files (excluding default) and geolocation origins.
     */
    private fun updateButtonStates() {
        // Check for preference files (excluding default domain)
        val prefsDir = File(requireContext().applicationInfo.dataDir, "shared_prefs")
        hasPreferenceFiles = false

        prefsDir.takeIf {
            it.exists() && it.isDirectory
        }?.list { _, name ->
            name.startsWith(DomainPreferences.prefix)
        }?.forEach {
            val domainName = it.substring(DomainPreferences.prefix.length).dropLast(4)
            val reversedDomain = domainName.reverseDomainName

            // Skip default domain settings file when counting
            if (reversedDomain.isNotEmpty()) {
                hasPreferenceFiles = true
                return@forEach
            }
        }

        // Check for geolocation origins
        android.webkit.GeolocationPermissions.getInstance().getOrigins { origins: Set<String>? ->
            hasGeolocationOrigins = !origins.isNullOrEmpty()

            // Update button states
            deleteAll?.isEnabled = hasPreferenceFiles
            clearLocation?.isEnabled = hasGeolocationOrigins
        }
    }

    /**
     * Update the domain list by re-evaluating which domains should be visible.
     * This is called after clearing geolocation permissions or deleting domain settings.
     */
    private fun updateDomainList() {
        // Get all current domain preferences
        val currentDomains = mutableListOf<String>()
        for (i in 0 until (catDomains?.preferenceCount ?: 0)) {
            catDomains?.getPreference(i)?.key?.let { currentDomains.add(it) }
        }

        // Re-evaluate each domain and remove if no longer visible
        domainCount = 0
        currentDomains.forEach { domain ->
            DomainPreferences.visible(domain) { shouldBeVisible ->
                val pref = findPreference<Preference>(domain)
                if (!shouldBeVisible && pref != null) {
                    // Remove preference if it should no longer be visible
                    pref.parent?.removePreference(pref)
                } else if (shouldBeVisible) {
                    // Keep count of visible domains
                    domainCount++
                    // Update summary for visible domains
                    pref?.let {
                        val domainPref = DomainPreferences(requireContext(), domain)
                        domainPref.getOverridesSummary(requireContext()) { summary ->
                            it.summary = summary
                        }
                    }
                }
            }
        }

        // Update the summary after processing all domains
        view?.postDelayed({
            updateDomainCountSummary()
        }, 100)
    }

    /**
     *
     */
    private fun clearGeolocationPermissions() {
        android.webkit.GeolocationPermissions.getInstance().clearAll()
        //activity?.snackbar("Geolocation permissions cleared")
    }


    /**
     *
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateDomainList(view)
    }

    /**
     * Create or update a domain preference in the list.
     * @param domain The domain name to create/update preference for
     * @param isNewPreference Whether this is a new preference being added (affects count)
     */
    private fun createOrUpdateDomainPreference(domain: String, isNewPreference: Boolean = false) {
        val domainPref = DomainPreferences(requireContext(), domain)

        // Check if domain should be visible
        DomainPreferences.visible(domain) { shouldBeVisible ->
            val existingPref = findPreference<Preference>(domain)

            if (!shouldBeVisible) {
                // Remove preference if it exists but shouldn't
                existingPref?.let {
                    it.parent?.removePreference(it)
                    domainCount--
                    updateDomainCountSummary()
                }
                // Clean up settings file if no overrides
                domainPref.deleteIfNoOverrides()
                return@visible
            }

            // Update existing preference or create new one
            if (existingPref != null) {
                // Update summary for existing preference
                domainPref.getOverridesSummary(requireContext()) { existingPref.summary = it }
            } else if (isNewPreference) {
                // Create new preference
                createDomainPreference(domain, domainPref)
            }
        }
    }

    /**
     * Creates a new domain preference with favicon and adds it to the list.
     */
    private fun createDomainPreference(domain: String, domainPref: DomainPreferences) {
        val pref = x.Preference(requireContext()).apply {
            isSingleLineTitle = false
            key = domain
            title = domain.reverseDomainName  // For sorting
            displayedTitle = domain  // For display
            breadcrumb = domain
            fragment = "fulguris.settings.fragment.DomainSettingsFragment"
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                this@DomainsSettingsFragment.domain = domain
                app.domain = domain
                false
            }
        }

        // Set summary asynchronously
        domainPref.getOverridesSummary(requireContext()) { pref.summary = it }

        // Add favicon
        faviconModel.faviconForUrl(
            "http://$domain",
            "",
            (activity as? ThemedActivity)?.isDarkTheme() == true
        )
            .subscribeOn(networkScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(
                onSuccess = { bitmap ->
                    pref.icon = bitmap.scale(
                        Utils.dpToPx(24f),
                        Utils.dpToPx(24f)
                    ).toDrawable(resources)
                }
            )

        catDomains?.addPreference(pref)
        domainCount++
        updateDomainCountSummary()
    }

    /**
     * Populate or update the domain list.
     */
    @SuppressLint("CheckResult")
    fun populateDomainList(view: View) {
        // Handle updates when returning from domain settings
        if (populated) {
            Timber.d("populateDomainList: updating after domain settings visit")

            if (domain.isEmpty()) {
                // Returning from default domain settings - check if we need to update button states
                updateButtonStates()
                return
            }

            // Update the visited domain
            createOrUpdateDomainPreference(domain, isNewPreference = false)

            // Check parent domain for changes
            "http://$domain".toHttpUrl().topPrivateDomain()?.takeIf { it.isNotEmpty() }?.let { tpd ->
                createOrUpdateDomainPreference(tpd, isNewPreference = findPreference<Preference>(tpd) == null)
            }

            // Update button states after processing domain changes
            updateButtonStates()

            return
        }

        populated = true

        // Add default domain settings entry
        x.Preference(requireContext()).apply {
            isSingleLineTitle = false
            title = getString(R.string.pref_title_default_domain_settings)
            summary = getString(R.string.pref_summary_default_domain_settings)
            order = 5
            fragment = "fulguris.settings.fragment.DomainSettingsFragment"
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                domain = ""
                app.domain = ""
                false
            }
        }.also { preferenceScreen.addPreference(it) }

        // Collect and populate all domains
        collectAllDomains { allDomains ->
            populateDomainsUI(allDomains)
        }
    }

    /**
     * Collect all domains that should be displayed.
     * Includes domains with settings files and domains with geolocation permissions.
     */
    private fun collectAllDomains(callback: (Set<String>) -> Unit) {
        val candidateDomains = mutableSetOf<String>()

        // 1. Add domains with settings files (excluding default domain)
        val prefsDir = File(requireContext().applicationInfo.dataDir, "shared_prefs")
        hasPreferenceFiles = false

        prefsDir.takeIf {
            it.exists() && it.isDirectory
        }?.list { _, name ->
            name.startsWith(DomainPreferences.prefix)
        }?.forEach {
            val domainName = it.substring(DomainPreferences.prefix.length).dropLast(4)
            val reversedDomain = domainName.reverseDomainName

            // Skip default domain settings file when counting
            if (reversedDomain.isEmpty()) {
                return@forEach
            }

            hasPreferenceFiles = true
            reversedDomain.takeIf { domain -> domain.isNotEmpty() }
                ?.let { domain -> candidateDomains.add(domain) }
        }

        // 2. Add domains with geolocation permissions
        android.webkit.GeolocationPermissions.getInstance().getOrigins { origins: Set<String>? ->
            hasGeolocationOrigins = !origins.isNullOrEmpty()

            origins?.forEach { origin ->
                try {
                    java.net.URI(origin).host?.takeIf { it.isNotEmpty() }?.let { candidateDomains.add(it) }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse geolocation origin: $origin")
                }
            }

            // Update button states based on what we found
            deleteAll?.isEnabled = hasPreferenceFiles
            clearLocation?.isEnabled = hasGeolocationOrigins

            // Return all collected domains (we'll filter with visible() later)
            callback(candidateDomains)
        }
    }

    /**
     * Populate the domains list UI with the given set of domains.
     * Processes domains asynchronously with progressive UI updates.
     */
    private fun populateDomainsUI(domains: Set<String>) {
        if (domains.isEmpty()) {
            // Update summary and show buttons even when empty
            updateDomainCountSummary()
            return
        }

        domainCount = 0
        val sortedDomains = domains.sortedBy { it.reverseDomainName }
        var delay = initialDelay

        // Process each domain asynchronously with staggered delays
        sortedDomains.forEach { domain ->
            view?.postDelayed({
                createOrUpdateDomainPreference(domain, isNewPreference = true)
            }, delay)
            delay += delayIncrement
        }

        // Show delete button after all domains are processed
        view?.postDelayed({
            updateDomainCountSummary()
        }, delay)
    }

}
