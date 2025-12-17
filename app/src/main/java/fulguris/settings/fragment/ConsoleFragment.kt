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

import android.os.Bundle
import android.text.TextUtils
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.activity.WebBrowserActivity
import fulguris.view.WebPageTab
import android.webkit.ConsoleMessage
import fulguris.extensions.px

/**
 * Fragment to display all JavaScript console messages from the current page
 * Shows message level, source, line number, and timestamp
 */
@AndroidEntryPoint
class ConsoleFragment : AbstractSettingsFragment() {

    private lateinit var messages: List<WebPageTab.ConsoleMessage>

    override fun titleResourceId(): Int = R.string.pref_category_title_console

    override fun providePreferencesXmlResource() = R.xml.preference_console

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Get console messages from the current tab
        (activity as? WebBrowserActivity)?.tabsManager?.currentTab?.let { tab ->
            messages = tab.getConsoleMessages()
        } ?: run {
            messages = emptyList()
        }

        populateConsoleMessages()
    }

    /**
     * Display all console messages in chronological order
     */
    private fun populateConsoleMessages() {
        val preferenceScreen = preferenceScreen

        // Calculate statistics for the summary
        val totalMessages = messages.size
        val errorCount = messages.count { it.consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR }
        val warningCount = messages.count { it.consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING }

        // Update the back preference summary with statistics and make it visible
        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_back))?.apply {
            isVisible = true
            summary = getString(R.string.pref_category_summary_console, totalMessages, errorCount, warningCount)
        }

        if (messages.isEmpty()) {
            val emptyCategory = PreferenceCategory(requireContext()).apply {
                //title = getString(R.string.console_title)
                summary = getString(R.string.pref_summary_console_empty)
                isIconSpaceReserved = false
            }
            preferenceScreen.addPreference(emptyCategory)
            return
        }

        // Create a single category for all messages
        val category = PreferenceCategory(requireContext()).apply {
            //title = getString(R.string.console_messages, totalMessages)
            isIconSpaceReserved = false
        }
        preferenceScreen.addPreference(category)

        // Add each message as a preference (in chronological order)
        messages.forEach { timestampedMessage ->
            val message = timestampedMessage.consoleMessage
            val pref = x.Preference(requireContext()).apply {
                // Use timestamp as the key to make it unique
                key = "console_message_${timestampedMessage.timestamp}"

                // Title shows source and line number
//                title = if (message.sourceId()?.isNotEmpty() == true) {
//                    "${extractFilename(message.sourceId())}:${message.lineNumber()}"
//                } else {
//                    getString(R.string.console_no_source)
//                }
                isSingleLineTitle = true

                // Summary shows timestamp and message text
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                val timeString = timeFormat.format(java.util.Date(timestampedMessage.timestamp))
                title = timeString
                summary = message.message()
                //summary = "$timeString - ${message.message()}"
                verticalPadding = 4.px
                minHeight = 0
                isSingleLineSummary = false
                summaryEllipsize = TextUtils.TruncateAt.END
                isIconSpaceReserved = false
                summaryMaxLines = 100

                // Set icon based on message level
                titleDrawableStart = when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> R.drawable.ic_error_outline
                    ConsoleMessage.MessageLevel.WARNING -> R.drawable.ic_warning_outline
                    ConsoleMessage.MessageLevel.DEBUG -> R.drawable.ic_bug_report_outline
                    ConsoleMessage.MessageLevel.TIP -> R.drawable.ic_info
                    ConsoleMessage.MessageLevel.LOG -> R.drawable.ic_info
                    else -> R.drawable.ic_info
                }

                // Make it non-clickable since we show all info inline
                isCopyingEnabled = true
            }
            category.addPreference(pref)
        }
    }

    /**
     * Extract just the filename from a full URL/path
     */
    private fun extractFilename(sourceId: String): String {
        return try {
            sourceId.substringAfterLast('/')
        } catch (e: Exception) {
            sourceId
        }
    }
}

