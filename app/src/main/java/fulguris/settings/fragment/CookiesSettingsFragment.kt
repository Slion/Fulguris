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
import fulguris.activity.WebBrowserActivity
import fulguris.di.UserPrefs
import fulguris.extensions.resizeAndShow
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.app
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class CookiesSettingsFragment : AbstractSettingsFragment() {

    @Inject
    @UserPrefs
    internal lateinit var preferences: SharedPreferences

    private var catCookies: PreferenceCategory? = null
    private var deleteAll: Preference? = null


    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_cookies
    }

    // Set to the domain settings the user last opened
    var domain: String = app.domain


    override fun providePreferencesXmlResource() =
        R.xml.preference_cookies

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // TODO: Have a custom preference with test input to filter out our list

        // Disable ordering as added to sort alphabetically by title
        catCookies = findPreference<PreferenceCategory>(resources.getString(R.string.pref_key_cat_cookies))?.apply { isOrderingAsAdded = true }


        deleteAll = clickablePreference(
            preference = getString(R.string.pref_key_delete_page_cookies),
            onClick = {
                // Show confirmation dialog and proceed if needed
                MaterialAlertDialogBuilder(requireContext())
                    .setCancelable(true)
                    .setTitle(R.string.question_delete_all_page_cookies)
                    //.setMessage(R.string.prompt_please_confirm)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        deleteAllPageCookies()
                    }
                    .resizeAndShow()
            }
        )
    }

    /**
     *
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateCookieList()
    }

    /**
     *
     */
    private fun deleteAllPageCookies() {
        Timber.v("Domain: $domain")
        (requireActivity() as? WebBrowserActivity)?.let { browser ->
            browser.tabsManager.currentTab?.url?.let { url ->
                Timber.d("URL: $url")
                val httpUrl = url.toHttpUrl()
                val paths = httpUrl.encodedPathSegments.scan("/") { prefix, segment -> "$prefix$segment/" }
                Timber.d("Paths: $paths")
                val domains = ArrayList<String>()
                var host = httpUrl.host
                domains.add(host)
                val tld = httpUrl.topPrivateDomain()
                if (tld!=null) {
                    // Not an IP address great
                    while (host!=tld) {
                        host = host.substringAfter('.')
                        domains.add(host)
                    }
                    //domains.add(tld)
                }
                // Needed to delete all cookies without domain specified
                domains.add("")
                Timber.d("Domains: $domains")

                // Build our list of cookies
                val cm = CookieManager.getInstance()
                val cookies = cm.getCookie(url)?.split(';')
                Timber.v("Cookies count: ${cookies?.count()}")
                cookies?.forEach { cookie ->
                    Timber.v(cookie.trim())
                    val parsed = cookie.split('=', limit = 2)
                    if (parsed.isNotEmpty()) {
                        // See: https://stackoverflow.com/q/35390590/3969362
                        val cookieName = parsed[0].trim()
                        // In our chain of domains delete all cookies on our path
                        // That's the only way to make sure we hit the cookie cause we can't tell where it was defined
                        // See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie
                        // We can't delete cookies with __Secure- and __Host- prefixes
                        // I wonder how that works with our WebkitCookieManager?
                        domains.forEach { domain ->
                            paths.forEach { path ->
                                cm.setCookie(url,"$cookieName=;Domain=$domain;Path=$path;Max-Age=0") {
                                    Timber.v("Cookie $cookieName deleted: $it")
                                }
                            }
                        }
                    }
                }

                populateCookieList()
            }
        }
    }


    /**
     *
     */
    @SuppressLint("CheckResult")
    fun populateCookieList() {

        catCookies?.removeAll()


        var cookiesCount = 0

        (requireActivity() as? WebBrowserActivity)?.let { browser ->
            browser.tabsManager.currentTab?.url?.let { url ->
                Timber.d("URL: $url")
                // Build our list of cookies
                val cookies = CookieManager.getInstance().getCookie(url)?.apply{Timber.v("Raw cookies: $this")}?.split(';')
                Timber.v("Cookies count: ${cookies?.count()}")
                cookiesCount = cookies?.count() ?: 0
                cookies?.forEach { cookie ->
                    Timber.v(cookie.trim())
                    val parsed = cookie.split('=', limit = 2)
                    if (parsed.count() == 2) {

                            // Create cookie preference
                            val pref = Preference(requireContext())
                            // Make sure domains and not reversed domains are shown as titles
                            pref.isSingleLineTitle = false
                            //pref.key = title
                            // We are using preference built-in alphabetical sorting by title
                            // We want sorting to group sub-domains together so we use reverse domain
                            pref.title = parsed[0].trim()
                            pref.summary = parsed[1].trim()
                            //pref.breadcrumb = domain

                            catCookies?.addPreference(pref)

                            /*
                            pref.fragment = "fulguris.settings.fragment.DomainSettingsFragment"
                            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                                this.domain = domain
                                app.domain = domain
                                false
                            }*/

                    }
                }
            }
        }

        if (cookiesCount!=0) {
            catCookies?.summary = resources.getQuantityString(R.plurals.settings_summary_cookies, cookiesCount, cookiesCount)
            deleteAll?.isEnabled = true
        } else {
            catCookies?.summary = resources.getString(R.string.settings_summary_cookies)
            deleteAll?.isEnabled = false
        }

    }
}
