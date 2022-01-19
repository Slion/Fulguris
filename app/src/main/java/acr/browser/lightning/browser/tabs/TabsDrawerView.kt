package acr.browser.lightning.browser.tabs

import acr.browser.lightning.browser.TabsView
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.databinding.TabDrawerViewBinding
import acr.browser.lightning.di.configPrefs
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.ItemDragDropSwipeHelper
import acr.browser.lightning.utils.fixScrollBug
import acr.browser.lightning.view.LightningView
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import javax.inject.Inject


/**
 * A view which displays tabs in a vertical [RecyclerView].
 */
class TabsDrawerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), TabsView {

    private val uiController = context as UIController
    private val tabsAdapter: TabsDrawerAdapter

    private var mItemTouchHelper: ItemTouchHelper? = null

    var iBinding: TabDrawerViewBinding

    init {

        //context.injector.inject(this)

        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        // Inflate our layout with binding support
        iBinding = TabDrawerViewBinding.inflate(context.inflater,this, true)
        // Provide UI controller for data binding to work
        iBinding.uiController = uiController

        tabsAdapter = TabsDrawerAdapter(uiController)

        iBinding.tabsList.apply {
            //setLayerType(View.LAYER_TYPE_NONE, null)
            // We don't want that morphing animation for now
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            // Reverse layout if using bottom tool bars
            // LinearLayoutManager.setReverseLayout is also adjusted from BrowserActivity.setupToolBar
            val lm = LinearLayoutManager(context, RecyclerView.VERTICAL, context.configPrefs.toolbarsBottom)
            // Though that should not be needed as it is taken care of by [fixScrollBug]
            // See: https://github.com/Slion/Fulguris/issues/212
            lm.stackFromEnd = context.configPrefs.toolbarsBottom
            layoutManager = lm
            adapter = tabsAdapter
            // That would prevent our recycler to resize as needed with bottom sheets
            setHasFixedSize(false)
        }



        val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(tabsAdapter)

        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(iBinding.tabsList)
    }

    /**
     * Enable tool bar buttons according to current state of things
     * TODO: Find a way to share that code with TabsDesktopView
     */
    private fun updateTabActionButtons() {
        // If more than one tab, enable close all tabs button
        iBinding.actionCloseAllTabs.isEnabled = uiController.getTabModel().allTabs.count()>1
        // If we have more than one tab in our closed tabs list enable restore all pages button
        iBinding.actionRestoreAllPages.isEnabled = (uiController as BrowserActivity).presenter?.closedTabs?.bundleStack?.count()?:0>1
        // If we have at least one tab in our closed tabs list enable restore page button
        iBinding.actionRestorePage.isEnabled = (uiController as BrowserActivity).presenter?.closedTabs?.bundleStack?.count()?:0>0
        // No sessions in incognito mode
        if (uiController.isIncognito()) {
            iBinding.actionSessions.visibility = View.GONE
        }

    }

    override fun tabAdded() {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabRemoved(position: Int) {
        displayTabs()
        //tabsAdapter.notifyItemRemoved(position)
        updateTabActionButtons()
    }

    override fun tabChanged(position: Int) {
        displayTabs()
        //tabsAdapter.notifyItemChanged(position)
    }

    /**
     * TODO: this is called way to often for my taste and should be optimized somehow.
     */
    private fun displayTabs() {
        tabsAdapter.showTabs(uiController.getTabModel().allTabs.map(LightningView::asTabViewState))

        if (fixScrollBug(iBinding.tabsList)) {
            // Scroll bug was fixed trigger a scroll to current item then
            (context as BrowserActivity).apply {
                mainHandler.postDelayed({ scrollToCurrentTab() }, 0)
            }
        }
    }

    override fun tabsInitialized() {
        tabsAdapter.notifyDataSetChanged()
        updateTabActionButtons()
    }

    override fun setGoBackEnabled(isEnabled: Boolean) {
        //actionBack.isEnabled = isEnabled
    }

    override fun setGoForwardEnabled(isEnabled: Boolean) {
        //actionForward.isEnabled = isEnabled
    }

}
