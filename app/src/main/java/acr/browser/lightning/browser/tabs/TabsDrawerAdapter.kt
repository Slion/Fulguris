package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.extensions.setImageForTheme
import acr.browser.lightning.view.BackgroundDrawable
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * The adapter for vertical mobile style browser tabs.
 */
class TabsDrawerAdapter(
    uiController: UIController
) : TabsAdapter(uiController) {

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.tab_list_item, viewGroup, false)
        view.background = BackgroundDrawable(view.context)
        return TabViewHolder(view, uiController) //.apply { setIsRecyclable(false) }
    }

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.exitButton.tag = position

        val tab = tabList[position]

        holder.txtTitle.text = tab.title
        updateViewHolderAppearance(holder, tab)
        updateViewHolderFavicon(holder, tab.favicon, tab.isForeground)
        updateViewHolderBackground(holder, tab.isForeground)
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy();
    }

    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, favicon: Bitmap?, isForeground: Boolean) {
        // Apply filter to favicon if needed
        favicon?.let {
                val ba = uiController as BrowserActivity
                viewHolder.favicon.setImageForTheme(it,ba.useDarkTheme)
        } ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)
    }

    private fun updateViewHolderBackground(viewHolder: TabViewHolder, isForeground: Boolean) {
        val verticalBackground = viewHolder.layout.background as BackgroundDrawable
        verticalBackground.isCrossFadeEnabled = false
        if (isForeground) {
            verticalBackground.startTransition(200)
        } else {
            verticalBackground.reverseTransition(200)
        }
    }

    private fun updateViewHolderAppearance(viewHolder: TabViewHolder, tab: TabViewState) {
        if (tab.isForeground) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
            uiController.changeToolbarBackground(tab.favicon, tab.themeColor, null)
        } else if (tab.isFrozen) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.italicText)
        }
        else {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.normalText)
        }
    }

}
