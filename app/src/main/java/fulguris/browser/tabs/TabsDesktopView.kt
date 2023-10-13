package fulguris.browser.tabs

import fulguris.R
import fulguris.browser.TabsView
import fulguris.activity.WebBrowserActivity
import fulguris.browser.WebBrowser
import fulguris.databinding.TabDesktopViewBinding
import fulguris.extensions.inflater
import fulguris.utils.ItemDragDropSwipeHelper
import fulguris.view.WebPageTab
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber
import java.lang.Exception

/**
 * A view which displays browser tabs in a horizontal [RecyclerView].
 * TODO: Rename to horizontal?
 */
class TabsDesktopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr),
    TabsView {

    private val webBrowser = context as WebBrowser
    private val tabsAdapter: TabsDesktopAdapter
    private val tabList: RecyclerView
    private var iItemTouchHelper: ItemTouchHelper? = null
    // Inflate our layout with binding support
    val iBinding: TabDesktopViewBinding = TabDesktopViewBinding.inflate(context.inflater,this, true)

    init {
        // Provide UI controller
        iBinding.uiController = webBrowser

        val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)


        tabsAdapter = TabsDesktopAdapter(context, context.resources, webBrowser)

        tabList = findViewById<RecyclerView>(R.id.tabs_list).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            // We don't want that morphing animation for now
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            this.layoutManager = layoutManager
            adapter = tabsAdapter
            setHasFixedSize(true)
        }

        // Enable drag & drop but not swipe
        val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(tabsAdapter, true, false, ItemTouchHelper.END or ItemTouchHelper.START)
        iItemTouchHelper = ItemTouchHelper(callback)
        iItemTouchHelper?.attachToRecyclerView(iBinding.tabsList)
    }

    /**
     * Enable tool bar buttons according to current state of things
     * TODO: Find a way to share that code with TabsDrawerView
     */
    private fun updateTabActionButtons() {
        // If more than one tab, enable close all tabs button
        iBinding.actionCloseAllTabs.isEnabled = webBrowser.getTabModel().allTabs.count()>1
        // If we have more than one tab in our closed tabs list enable restore all pages button
        iBinding.actionRestoreAllPages.isEnabled = ((webBrowser as WebBrowserActivity).tabsManager.closedTabs.bundleStack.count() ?: 0) > 1
        // If we have at least one tab in our closed tabs list enable restore page button
        iBinding.actionRestorePage.isEnabled = ((webBrowser as WebBrowserActivity).tabsManager.closedTabs.bundleStack.count() ?: 0) > 0
        // No sessions in incognito mode
        if (webBrowser.isIncognito()) {
            iBinding.actionSessions.visibility = View.GONE
        }
    }


    override fun tabAdded() {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabRemoved(position: Int) {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabChanged(position: Int) {
        displayTabs()
        // Needed for the foreground tab color to update.
        // However sometimes it throws an illegal state exception so make sure we catch it.
        try {
            tabsAdapter.notifyItemChanged(position)
        } catch (e: Exception) {
            Timber.e(e,"notifyItemChanged")
        }

    }

    private fun displayTabs() {
        tabsAdapter.showTabs(webBrowser.getTabModel().allTabs.map(WebPageTab::asTabViewState))
    }

    override fun tabsInitialized() {
        tabsAdapter.notifyDataSetChanged()
        updateTabActionButtons()
    }

    override fun setGoBackEnabled(isEnabled: Boolean) = Unit

    override fun setGoForwardEnabled(isEnabled: Boolean) = Unit

}
