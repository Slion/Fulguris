package acr.browser.lightning.browser.tabs

import TabTouchHelperCallback
import acr.browser.lightning.R
import acr.browser.lightning.browser.TabsView
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.databinding.TabDrawerViewBinding
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.list.VerticalItemAnimator
import acr.browser.lightning.view.LightningView
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.tab_drawer_view.view.*

/**
 * A view which displays tabs in a vertical [RecyclerView].
 */
class TabsDrawerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), TabsView {

    private val uiController = context as UIController
    private val tabsAdapter = TabsDrawerAdapter(uiController)

    private var mItemTouchHelper: ItemTouchHelper? = null

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        // Inflate our layout with binding support, provide UI controller
        TabDrawerViewBinding.inflate(context.inflater,this, true).uiController = uiController


        val animator = VerticalItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = 200
            changeDuration = 0
            removeDuration = 200
            moveDuration = 200
        }

        tabs_list.apply {
            //setLayerType(View.LAYER_TYPE_NONE, null)
            itemAnimator = animator
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = tabsAdapter
            setHasFixedSize(true)
        }

        val callback: ItemTouchHelper.Callback = TabTouchHelperCallback(tabsAdapter)

        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(tabs_list)

    }

    /**
     * Enable tool bar buttons according to current state of things
     */
    private fun updateTabActionButtons() {
        // If more than one tab, enable close all tabs button
        action_close_all_tabs.isEnabled = uiController.getTabModel().allTabs.count()>1
        // If we have more than one tab in our closed tabs list enable restore all pages button
        action_restore_all_pages.isEnabled = (uiController as BrowserActivity).presenter?.closedTabs?.bundleStack?.count()?:0>1
        // If we have at least one tab in our closed tabs list enable restore page button
        action_restore_page.isEnabled = (uiController as BrowserActivity).presenter?.closedTabs?.bundleStack?.count()?:0>0
    }

    override fun tabAdded() {
        displayTabs()
        tabs_list.postDelayed({ tabs_list.smoothScrollToPosition(tabsAdapter.itemCount - 1) }, 500)
        updateTabActionButtons()
    }

    override fun tabRemoved(position: Int) {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabChanged(position: Int) {
        displayTabs()
    }

    private fun displayTabs() {
        tabsAdapter.showTabs(uiController.getTabModel().allTabs.map(LightningView::asTabViewState))
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
