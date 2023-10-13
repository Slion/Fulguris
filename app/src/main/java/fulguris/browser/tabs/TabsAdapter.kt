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

package fulguris.browser.tabs

import fulguris.browser.WebBrowser
import fulguris.utils.ItemDragDropSwipeAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.*

/**
 * Abstract base tabs adapter.
 * Implement functionality common to our concrete tabs adapters.
 */
abstract class TabsAdapter(val webBrowser: WebBrowser): RecyclerView.Adapter<TabViewHolder>(),
    ItemDragDropSwipeAdapter {

    protected var tabList: List<TabViewState> = emptyList()

    /**
     * Show tabs and compute diffs.
     * TODO: Though I wonder how that works without copying the list which we had to do in our SessionsAdapter.
     */
    fun showTabs(tabs: List<TabViewState>) {
        val oldList = tabList
        tabList = tabs
        DiffUtil.calculateDiff(TabViewStateDiffCallback(oldList, tabList)).dispatchUpdatesTo(this)
    }

    /**
     * From [RecyclerView.Adapter]
     */
    override fun getItemCount() = tabList.size

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onViewRecycled(holder: TabViewHolder) {
        super.onViewRecycled(holder)
        // I'm not convinced that's needed
        //(uiController as BrowserActivity).toast("Recycled: " + holder.tab.title)
        holder.tab = null
    }

    /**
     * From [ItemDragDropSwipeAdapter]
     * An item was was moved through drag & drop
     */
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    {
        // Note: recent tab list is not affected
        // Swap local list position
        Collections.swap(tabList, fromPosition, toPosition)
        // Swap model list position
        Collections.swap(webBrowser.getTabModel().allTabs, fromPosition, toPosition)
        // Tell base class an item was moved
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    /**
     * From [ItemDragDropSwipeAdapter]
     * An item was was dismissed through swipe
     */
    override fun onItemDismiss(position: Int)
    {
        webBrowser.tabCloseClicked(position)
    }


}