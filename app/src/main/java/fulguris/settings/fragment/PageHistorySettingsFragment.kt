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
import fulguris.browser.TabsManager
import fulguris.activity.WebBrowserActivity
import fulguris.di.MainScheduler
import fulguris.di.NetworkScheduler
import fulguris.extensions.isDarkTheme
import fulguris.favicon.FaviconModel
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.Utils
import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebBackForwardList
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import slions.pref.BasicPreference
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

/**
 * Page history screen
 * TODO: Could add an option to clear the page history?
 * Could also display the number of items in history in the category title
 */
@AndroidEntryPoint
class PageHistorySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var tabsManager: TabsManager
    @Inject internal lateinit var faviconModel: FaviconModel

    @Inject @NetworkScheduler
    internal lateinit var networkScheduler: Scheduler
    @Inject @MainScheduler
    internal lateinit var mainScheduler: Scheduler

    override fun providePreferencesXmlResource() = R.xml.preference_page_history

    lateinit var category: PreferenceCategory
    lateinit var history: WebBackForwardList
    private var currentIndex = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState,rootKey)

        category = findPreference<PreferenceCategory>(resources.getString(R.string.pref_key_page_history))?.apply { isOrderingAsAdded = true }!!
        history = tabsManager.currentTab?.webView?.copyBackForwardList()!!
        currentIndex = history.currentIndex
        populateHistory()
    }

    /**
     *
     */
    @SuppressLint("CheckResult")
    private fun populateHistory() {

        category.removeAll()

        // Populate current page history
        for ( i in history.size-1 downTo 0) {

            history.getItemAtIndex(i).let {item ->
                // Create history item preference
                val pref = BasicPreference(requireContext())
                //pref.swapTitleSummary = true
                pref.isSingleLineTitle = false
                pref.key = "item$i"
                pref.title = item.title
                if (history.currentIndex==i) {
                    pref.title = "✔ " + pref.title
                }
                pref.summary = item.url
                pref.icon = item.favicon?.scale(fulguris.utils.Utils.dpToPx(24f), fulguris.utils.Utils.dpToPx(24f))?.toDrawable(resources)
                // As favicon is usually null for restored tab we still need to fetch it from our cache
                if (pref.icon==null) {
                    faviconModel.faviconForUrl(item.url,"",context?.isDarkTheme() == true)
                        .subscribeOn(networkScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy(
                            onSuccess = { bitmap ->
                                pref.icon = bitmap.scale(fulguris.utils.Utils.dpToPx(24f), fulguris.utils.Utils.dpToPx(24f)).toDrawable(resources)
                            }
                        )
                }

                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    // Compute to which item we should jump to
                    val steps = i - currentIndex

                    if (steps>0) {
                        // Going forward
                        (activity as WebBrowserActivity).animateTabFlipLeft()
                    } else if (steps<0) {
                        // Going back
                        (activity as WebBrowserActivity).animateTabFlipRight()
                    }

                    tabsManager.currentTab?.webView?.goBackOrForward(steps)
                    // Remove tick from former current item
                    category.findPreference<Preference>("item$currentIndex")?.title = history.getItemAtIndex(currentIndex).title
                    // Update current item
                    currentIndex = i
                    // Add tick to new current item
                    pref.title = "✔ " + pref.title
                    // TODO: Optionally exit our bottom sheet?
                    true
                }

                category.addPreference(pref)
            }
        }
    }



    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_page_history
    }
}
