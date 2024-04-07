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
import fulguris.extensions.resizeAndShow
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
    private var catResources: PreferenceCategory? = null
    private var deleteAll: Preference? = null

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
        catDomains = findPreference<PreferenceCategory>(resources.getString(R.string.pref_key_domains))?.apply { isOrderingAsAdded = false }
        catResources = findPreference<PreferenceCategory>(resources.getString(R.string.pref_key_resource_domains))?.apply { isOrderingAsAdded = false }
        // Hide resources category while we are filling our preferences otherwise it looks ugly
        catResources?.isVisible = false

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
                        catResources?.removeAll()
                    }
                    .resizeAndShow()

                true
            }
        ).apply { isVisible = false }
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

            // TODO: Have a function instead of duplicating code for sub-domain and its parent
            // Check if our domain was deleted when coming back from a specific domain preference page
            if (!DomainPreferences.exists(domain)) {
                // Domain settings was deleted remove the preference then
                findPreference<Preference>(domain)?.let {
                    it.parent?.removePreference(it)
                }
            } else {
                // Our setting still exist
                // Check if it was moved to another category and take action
                val dp = DomainPreferences(requireContext(),domain)
                findPreference<Preference>(domain)?.let {
                    val newParent = if (dp.entryPoint) catDomains else catResources
                    if (newParent!=it.parent) {
                        it.parent?.removePreference(it)
                        newParent?.addPreference(it)
                    }
                }
            }

            // Remove the top private domain if it was deleted
            val tpd = "http://$domain".toHttpUrl().topPrivateDomain()
            if (tpd?.isNotEmpty() == true) {
                if (!DomainPreferences.exists(tpd)) {
                    // Domain settings was deleted remove the preference then
                    findPreference<Preference>(tpd)?.let {
                        it.parent?.removePreference(it)
                    }
                } else {
                    // Our settings still exist
                    // Check if it was moved to another category and take action
                    val dp = DomainPreferences(requireContext(),tpd)
                    findPreference<Preference>(tpd)?.let {
                        val newParent = if (dp.entryPoint) catDomains else catResources
                        if (newParent!=it.parent) {
                            it.parent?.removePreference(it)
                            newParent?.addPreference(it)
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
            var delayIncrement = 1L
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

                    // Create domain preference
                    val pref = BasicPreference(requireContext())
                    // Make sure domains and not reversed domains are shown as titles
                    pref.swapTitleSummary = true
                    pref.isSingleLineTitle = false
                    pref.key = domain
                    // We are using preference built-in alphabetical sorting by title
                    // We want sorting to group sub-domains together so we use reverse domain
                    pref.title = domainReverse
                    pref.summary = domain
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

                    val domainPref = DomainPreferences(requireContext(),domain)

                    if (domainPref.entryPoint) {
                        catDomains?.addPreference(pref)
                    } else {
                        catResources?.addPreference(pref)
                    }

                },delay)
                // We could lower or just remove the delay as it seems to be smooth without it too
                // However having a bit of a delay does make it look prettier
                delay+=delayIncrement
            }

            // Show resources category only once our list is parsed otherwise it looks ugly
            view.postDelayed({
                catResources?.isVisible = true
                deleteAll?.isVisible = true
                             },delay)
        }
    }

}
