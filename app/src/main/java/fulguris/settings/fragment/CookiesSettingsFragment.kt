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
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.GET_COOKIE_INFO
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.app
import fulguris.extensions.copyToClipboard
import fulguris.extensions.snackbar
import fulguris.extensions.toast
import slions.pref.BasicPreference
import fulguris.utils.WebUtils
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

                true
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
     * Try that stuff against http://setcookie.net which conveniently allows you to specify a path too
     */
    private fun deleteAllPageCookies() {
        Timber.v("Domain: $domain")
        (requireActivity() as? WebBrowserActivity)?.let { browser ->
            browser.tabsManager.currentTab?.url?.let { url ->
                Timber.d("URL: $url")
                val httpUrl = url.toHttpUrl()
                val paths = httpUrl.encodedPathSegments.scan("") { prefix, segment -> "$prefix/$segment" }
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
                val cookies = WebUtils.getCookies(url)
                Timber.v("Cookies count: ${cookies.count()}")
                cookies.forEach { cookie ->
                    Timber.v(cookie.trim())

                    val attrs = cookie.split(";").map { it.split("=", limit=2) }.map { it[0].trim() to if (it.count()==2) it[1].trim() else null }
                    val name = attrs[0].first
                    val value = attrs[0].second
                    val pathAttr = attrs.find { it.first.equals("path",true)  }
                    val domainAttr = attrs.find { it.first.equals("domain",true)  }

                    // If we have a path attribute we don't need to use brut force
                    if (pathAttr==null || domainAttr==null) {
                    //if (true) {
                        // Use legacy brut force way to delete cookies
                        // See: https://stackoverflow.com/q/35390590/3969362
                        // In our chain of domains delete all cookies on our path
                        // That's the only way to make sure we hit the cookie cause we can't tell where it was defined
                        // See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie
                        // We can't delete cookies with __Secure- and __Host- prefixes
                        // I wonder how that works with our WebkitCookieManager?
                        domains.forEach { domain ->
                            paths.forEach { path ->
                                val delCookie = "$name=;Domain=$domain;Path=$path;Max-Age=0"
                                Timber.d("Delete cookie: $delCookie")
                                cm.setCookie(url,delCookie) {
                                    Timber.v("Cookie $name deleted: $it")
                                }
                            }
                        }
                    } else {
                        // New slightly more sane way to delete cookies
                        // Do it once without domain as this is needed when a path is specified
                        var delCookie = "$name=;Path=${pathAttr.second};Max-Age=0"
                        Timber.d("Delete cookie: $delCookie")
                        cm.setCookie(url,delCookie) {
                            Timber.v("Cookie $name deleted: $it")
                        }

                        // Do it once with domain as this is needed path is root /
                        delCookie = "$name=;Domain=${domainAttr.second};Path=${pathAttr.second};Max-Age=0"
                        //val delCookie = "$name=;Path=${pathAttr.second};Max-Age=0"
                        Timber.d("Delete cookie: $delCookie")
                        cm.setCookie(url,delCookie) {
                            Timber.v("Cookie $name deleted: $it")
                        }

                        // Just don't ask
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
                val cookies = WebUtils.getCookies(url)

                Timber.v("Cookies count: ${cookies.count()}")
                cookiesCount = cookies.count() ?: 0
                cookies.forEach { cookie ->
                    Timber.v(cookie.trim())
                    // Create cookie preference
                    val pref = BasicPreference(requireContext())
                    // Make sure domains and not reversed domains are shown as titles
                    pref.isSingleLineTitle = false
                    //pref
                    var summary = ""
                    // For each attribute of that cookie
                    cookie.split(';').forEach { attr ->
                        if (pref.title.isNullOrEmpty()) {
                            // First deal with name and value
                            attr.split('=', limit = 2).forEach { comp ->
                                if (pref.title.isNullOrEmpty()) {
                                    pref.title = comp.trim()
                                } else {
                                    summary = comp.trim()
                                }
                            }
                        } else {
                            // We do not parse attributes beyond name and value
                            // Just display them as they are then
                            summary += "\n${attr.trim()}"
                        }
                    }

                    // Tapping on the preference copies the cookie to the clipboard
                    pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.copyToClipboard(cookie,"Cookie")
                        activity?.toast(R.string.message_cookie_copied)
                        false
                    }
                    pref.summary = summary
                    catCookies?.addPreference(pref)

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
