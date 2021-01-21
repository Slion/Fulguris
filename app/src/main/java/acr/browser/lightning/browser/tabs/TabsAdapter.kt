package acr.browser.lightning.browser.tabs

import acr.browser.lightning.controller.UIController
import acr.browser.lightning.utils.ItemDragDropSwipeAdapter
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import java.util.*

/**
 * Abstract base tabs adapter.
 * Implement functionality common to our concrete tabs adapters.
 */
abstract class TabsAdapter(val uiController: UIController): RecyclerView.Adapter<TabViewHolder>(), ItemDragDropSwipeAdapter {

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
        holder.tab = TabViewState()
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
        Collections.swap(uiController.getTabModel().allTabs, fromPosition, toPosition)
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
        uiController.tabCloseClicked(position)
    }


}