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
import android.text.TextUtils
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.activity.WebBrowserActivity
import fulguris.view.WebPageClient

/**
 * Fragment to display all page requests and their blocked status
 * Groups requests by domain using preference categories
 * TODO: Add sort preference to toggle between sort by domain and sort by timestamp or order in our requests collection
 */
@AndroidEntryPoint
class RequestsFragment : AbstractSettingsFragment() {

    private lateinit var requests: List<WebPageClient.PageRequest>

    override fun titleResourceId(): Int = R.string.page_requests_title

    override fun providePreferencesXmlResource() = R.xml.preference_page_requests

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Get requests from the current tab
        (activity as? WebBrowserActivity)?.tabsManager?.currentTab?.let { tab ->
            requests = tab.webPageClient.getPageRequests()
        } ?: run {
            requests = emptyList()
        }

        populateRequestsList()
    }

    /**
     * Group requests by domain and create preference categories for each domain
     */
    private fun populateRequestsList() {
        val preferenceScreen = preferenceScreen

        // Group requests by domain
        val requestsByDomain = requests.groupBy { request ->
            try {
                Uri.parse(request.url).host ?: getString(R.string.unknown)
            } catch (e: Exception) {
                getString(R.string.unknown)
            }
        }

        // Calculate statistics for the summary
        val totalRequests = requests.size
        val blockedRequests = requests.count { it.wasBlocked }
        val allowedRequests = totalRequests - blockedRequests

        // Update the back preference summary with statistics and make it visible
        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_back))?.apply {
            isVisible = true
            summary = getString(R.string.page_requests_summary, totalRequests, blockedRequests, allowedRequests)
        }

        // Sort domains alphabetically
        requestsByDomain.keys.sorted().forEach { domain ->
            val domainRequests = requestsByDomain[domain] ?: emptyList()
            val blocked = domainRequests.count { it.wasBlocked }
            val allowed = domainRequests.size - blocked

            // Create category for this domain
            val category = PreferenceCategory(requireContext()).apply {
                title = domain
                summary = getString(R.string.page_requests_summary, domainRequests.size, blocked, allowed)
                isIconSpaceReserved = false
            }
            preferenceScreen.addPreference(category)

            // Add each request as a preference
            domainRequests.forEach { request ->
                val pref = x.Preference(requireContext()).apply {
                    // Use request URL as the key to make it unique
                    key = request.url + request.timestamp
                    title = extractPath(request.url).substringBefore('?')
                    isSingleLineTitle = true
                    titleEllipsize = TextUtils.TruncateAt.MIDDLE
                    // Show URL parameters, file type, or scheme in summary
                    summary = extractSummary(request.url)
                    isSingleLineSummary = true
                    summaryEllipsize = TextUtils.TruncateAt.MIDDLE
                    isIconSpaceReserved = false
                    isSingleLineTitle = true

                    // Set drawable start icon based on blocked status
                    titleDrawableStart = if (request.wasBlocked) {
                        R.drawable.ic_block
                    } else {
                        R.drawable.ic_check
                    }

                    // Set encryption icons
                    summaryDrawableStart = if (request.url.startsWith("https://", ignoreCase = true)) {
                        R.drawable.ic_encrypted_outline
                    } else {
                        R.drawable.ic_encrypted_off_outline
                    }

                    // Set fragment navigation with arguments
                    fragment = RequestFragment::class.java.name

                    // Store request data in extras to pass to the details fragment
                    extras.putString("url", request.url)
                    extras.putBoolean("was_blocked", request.wasBlocked)
                    extras.putLong("timestamp", request.timestamp)
                }
                category.addPreference(pref)
            }
        }

        // If no requests, show a message
        if (requests.isEmpty()) {
            val emptyCategory = PreferenceCategory(requireContext()).apply {
                title = getString(R.string.page_requests_title)
                summary = getString(R.string.page_requests_none)
                isIconSpaceReserved = false
            }
            preferenceScreen.addPreference(emptyCategory)
        }
    }

    /**
     * Extract the path from a URL for display as title
     */
    private fun extractPath(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: "/"
            val query = uri.query
            if (query != null && query.isNotEmpty()) {
                "$path?$query"
            } else {
                path
            }
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Extract summary information for display.
     * Priority: 1) URL parameters, 2) Content type category (Script, Style, Image, Font, etc.), 3) URL scheme in uppercase
     */
    private fun extractSummary(url: String): String {
        return try {
            val uri = Uri.parse(url)

            // First priority: return query parameters if they exist
            if (!uri.query.isNullOrEmpty()) {
                return uri.query!!
            }

            // Second priority: extract file type/extension and map to content category
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val lastSegment = path.substringAfterLast('/')
                if (lastSegment.contains('.')) {
                    val extension = lastSegment.substringAfterLast('.').lowercase()
                    if (extension.isNotEmpty()) {
                        val contentType = getMimeTypeOrExtension(extension)
                        if (contentType != null) {
                            return contentType
                        }
                    }
                }
            }

            // Third priority: return scheme in uppercase
            uri.scheme?.uppercase() ?: " "
        } catch (e: Exception) {
            " "
        }
    }

    /**
     * Get MIME type from file extension using Android's MimeTypeMap.
     * Returns the MIME type if known (e.g., "text/javascript"), otherwise returns the uppercase extension (e.g., "JS")
     */
    private fun getMimeTypeOrExtension(extension: String): String? {
        // Get MIME type from Android's MimeTypeMap
        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val ext = extension.uppercase()

        // Return MIME type if available, otherwise just the extension
        return mimeType ?: ext
    }
}

