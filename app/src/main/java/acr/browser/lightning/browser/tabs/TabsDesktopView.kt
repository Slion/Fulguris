package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.TabsView
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.list.HorizontalItemAnimator
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.databinding.TabDesktopViewBinding
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.view.LightningView
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.popup_menu_browser.view.*
import kotlinx.android.synthetic.main.tab_drawer_view.view.*
import java.lang.Exception

/**
 * A view which displays browser tabs in a horizontal [RecyclerView].
 */
class TabsDesktopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), TabsView {

    private val uiController = context as UIController
    private val tabsAdapter: TabsDesktopAdapter
    private val tabList: RecyclerView

    init {
        // Inflate our layout with binding support, provide UI controller
        TabDesktopViewBinding.inflate(context.inflater,this, true).uiController = uiController

        val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)

        val animator = HorizontalItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = 200
            changeDuration = 0
            removeDuration = 200
            moveDuration = 200
        }

        tabsAdapter = TabsDesktopAdapter(context, context.resources, uiController = uiController)

        tabList = findViewById<RecyclerView>(R.id.tabs_list).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            itemAnimator = animator
            this.layoutManager = layoutManager
            adapter = tabsAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * Enable tool bar buttons according to current state of things
     * TODO: Find a way to share that code with TabsDrawerView
     */
    private fun updateTabActionButtons() {
        // If more than one tab, enable close all tabs button
        action_close_all_tabs.isEnabled = uiController.getTabModel().allTabs.count()>1
        // If we have more than one tab in our closed tabs list enable restore all pages button
        action_restore_all_pages.isEnabled = (uiController as BrowserActivity).presenter?.closedTabs?.bundleStack?.count()?:0>1
        // If we have at least one tab in our closed tabs list enable restore page button
        action_restore_page.isEnabled = (uiController as BrowserActivity).presenter?.closedTabs?.bundleStack?.count()?:0>0
        // No sessions in incognito mode
        if (uiController.isIncognito()) {
            action_sessions.visibility = View.GONE
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
        }

    }

    private fun displayTabs() {
        tabsAdapter.showTabs(uiController.getTabModel().allTabs.map(LightningView::asTabViewState))
    }

    override fun tabsInitialized() {
        tabsAdapter.notifyDataSetChanged()
        updateTabActionButtons()
    }

    override fun setGoBackEnabled(isEnabled: Boolean) = Unit

    override fun setGoForwardEnabled(isEnabled: Boolean) = Unit

}
