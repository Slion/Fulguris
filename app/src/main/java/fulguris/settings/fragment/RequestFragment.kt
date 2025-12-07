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

import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import java.util.Date

/**
 * Fragment to display detailed information about a page request
 */
@AndroidEntryPoint
class RequestFragment : AbstractSettingsFragment() {

    override fun titleResourceId(): Int = R.string.page_request_details_title

    override fun providePreferencesXmlResource() = R.xml.preference_page_request_details

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Get request details from arguments (passed from preference extras)
        val url = arguments?.getString("url") ?: ""
        val wasBlocked = arguments?.getBoolean("was_blocked", false) ?: false
        val timestamp = arguments?.getLong("timestamp", 0L) ?: 0L

        populateRequestDetails(url, wasBlocked, timestamp)
    }

    /**
     * Populate the preference screen with request details
     */
    private fun populateRequestDetails(url: String, wasBlocked: Boolean, timestamp: Long) {
        val preferenceScreen = preferenceScreen

        // Update the back preference with scheme as summary and make it visible
        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_back))?.apply {
            isVisible = true
            summary = try {
                Uri.parse(url).scheme ?: url
            } catch (e: Exception) {
                ""
            }
        }

        // Open URL preference
        val openPref = x.Preference(requireContext()).apply {
            title = getString(R.string.action_open)
            summary = url
            isSingleLineSummary = true
            isIconSpaceReserved = true
            setIcon(R.drawable.ic_link)
            intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(requireContext().packageName)
                // Make sure we tell ourselves where this is coming from
                // Avoid sending ourselves to the background when closing this tab
                putExtra("PACKAGE", requireContext().packageName)
            }
        }
        preferenceScreen.addPreference(openPref)


        // Status category
        val statusCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.request_status)
            isIconSpaceReserved = false
        }
        preferenceScreen.addPreference(statusCategory)

        // Status preference
        val statusPref = x.Preference(requireContext()).apply {
            title = if (wasBlocked) {
                getString(R.string.page_requests_blocked)
            } else {
                getString(R.string.page_requests_allowed)
            }
            isIconSpaceReserved = true
            setIcon(if (wasBlocked) R.drawable.ic_block else R.drawable.ic_check)
            isSelectable = false
        }
        statusCategory.addPreference(statusPref)

        // Parse URL for details
        try {
            val uri = Uri.parse(url)

            // URL Details category
            val detailsCategory = PreferenceCategory(requireContext()).apply {
                title = getString(R.string.request_url)
                isIconSpaceReserved = false
            }
            preferenceScreen.addPreference(detailsCategory)

            // Copy URL preference (first in category)
            val copyPref = x.Preference(requireContext()).apply {
                title = getString(R.string.action_copy)
                summary = getString(R.string.request_url_length, url.length)
                isIconSpaceReserved = true
                setIcon(R.drawable.ic_content_copy)
                setOnPreferenceClickListener {
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("URL", url)
                    clipboard.setPrimaryClip(clip)
                    // Show a toast to confirm
                    android.widget.Toast.makeText(requireContext(), R.string.message_text_copied, android.widget.Toast.LENGTH_SHORT).show()
                    true
                }
            }
            detailsCategory.addPreference(copyPref)

            // Full URL preference
            val urlPref = x.Preference(requireContext()).apply {
                title = getString(R.string.request_url)
                summary = url
                isIconSpaceReserved = false
                isSelectable = false
            }
            detailsCategory.addPreference(urlPref)

            // Domain preference
            uri.host?.let { host ->
                val domainPref = x.Preference(requireContext()).apply {
                    title = getString(R.string.request_domain)
                    summary = host
                    isIconSpaceReserved = false
                    isSelectable = false
                }
                detailsCategory.addPreference(domainPref)
            }

            // Path preference
            uri.path?.let { path ->
                val pathPref = x.Preference(requireContext()).apply {
                    title = getString(R.string.request_path)
                    summary = path
                    isIconSpaceReserved = false
                    isSelectable = false
                }
                detailsCategory.addPreference(pathPref)
            }

            // Parameters category (if URL has query parameters)
            val queryParams = uri.queryParameterNames
            if (queryParams != null && queryParams.isNotEmpty()) {
                val paramsCategory = PreferenceCategory(requireContext()).apply {
                    title = getString(R.string.request_parameters)
                    isIconSpaceReserved = false
                }
                preferenceScreen.addPreference(paramsCategory)

                // Add each parameter as a preference
                queryParams.forEach { paramName ->
                    val paramValue = uri.getQueryParameter(paramName) ?: ""
                    val paramPref = x.Preference(requireContext()).apply {
                        title = paramName
                        summary = paramValue
                        isSingleLineSummary = true
                        isIconSpaceReserved = false
                        isSelectable = true
                    }
                    paramsCategory.addPreference(paramPref)
                }
            }
        } catch (e: Exception) {
            // If URL parsing fails, just show the raw URL
            val urlPref = x.Preference(requireContext()).apply {
                title = getString(R.string.request_url)
                summary = url
                isIconSpaceReserved = false
                isSelectable = false
            }
            preferenceScreen.addPreference(urlPref)
        }

        // Timestamp category
        val timestampCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.request_timestamp)
            isIconSpaceReserved = false
        }
        preferenceScreen.addPreference(timestampCategory)

        // Timestamp preference
        val timestampPref = x.Preference(requireContext()).apply {
            title = getString(R.string.request_timestamp)
            summary = if (timestamp > 0) {
                DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(timestamp)).toString()
            } else {
                getString(R.string.unknown)
            }
            isIconSpaceReserved = false
            isSelectable = false
        }
        timestampCategory.addPreference(timestampPref)
    }
}

