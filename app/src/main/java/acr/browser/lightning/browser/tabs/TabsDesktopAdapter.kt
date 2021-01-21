package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.*
import acr.browser.lightning.utils.ItemDragDropSwipeAdapter
import acr.browser.lightning.utils.ThemeUtils
import acr.browser.lightning.view.BackgroundDrawable
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * The adapter for horizontal desktop style browser tabs.
 */
class TabsDesktopAdapter(
    context: Context,
    private val resources: Resources,
    uiController: UIController
) : TabsAdapter(uiController), ItemDragDropSwipeAdapter {


    private var textColor = Color.TRANSPARENT

    init {
        //val backgroundColor = Utils.mixTwoColors(ThemeUtils.getPrimaryColor(context), Color.BLACK, 0.75f)
        //val foregroundColor = ThemeUtils.getPrimaryColor(context)
    }

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.tab_list_item_horizontal, viewGroup, false)
        view.background = BackgroundDrawable(view.context)
        //val tab = tabList[i]
        return TabViewHolder(view, uiController)
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
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy();
    }


    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, favicon: Bitmap?, isForeground: Boolean) {
        favicon?.let {
                viewHolder.favicon.setImageBitmap(it)
            }
        ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)
    }

    private fun updateViewHolderAppearance(viewHolder: TabViewHolder, tab: TabViewState) {

        // Just to init our default text color
        if (textColor == Color.TRANSPARENT) {
            textColor = viewHolder.txtTitle.currentTextColor
        }

        val context = viewHolder.layout.context

        if (tab.isForeground) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
            val newTextColor = (uiController as BrowserActivity).currentToolBarTextColor
            viewHolder.txtTitle.setTextColor(newTextColor)
            viewHolder.exitButton.findViewById<ImageView>(R.id.deleteButton).setColorFilter(newTextColor)
            //uiController.changeToolbarBackground(tab.favicon, tab.themeColor, viewHolder.layout.background)

            // If we just got to the foreground
            if (tab.isForeground!=viewHolder.tab.isForeground
                    // or if our theme color changed
                    || tab.themeColor!=viewHolder.tab.themeColor
                    // or if our theme color is different than our UI color, i.e. using favicon color instead of meta theme
                    || tab.themeColor!=uiController.getUiColor())
            {
                val backgroundColor = ThemeUtils.getColor(context, R.attr.selectedBackground)
                val foregroundColor = ThemeUtils.getColor(context, R.attr.colorPrimary)

                // Transition from background tab color to foreground tab color
                // That's just running a fancy animation
                viewHolder.layout.background =
                    BackgroundDrawable(context,
                            ColorDrawable(backgroundColor),
                            ColorDrawable(
                                    //If color mode activated
                                    if (uiController.isColorMode())
                                        if (tab.themeColor!=Color.TRANSPARENT)
                                            // Use meta theme color if we have one
                                            tab.themeColor
                                        else
                                            if (uiController.getUiColor()!=backgroundColor)
                                            // Use favicon extracted color if there is one
                                                uiController.getUiColor()
                                            else
                                                // Otherwise use default foreground color
                                                foregroundColor
                                    else // No color mode just use our theme default background then
                                        foregroundColor))
                            .apply { startTransition(250) }
            }

        } else {
            // Background tab
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, if (tab.isFrozen) R.style.italicText else R.style.normalText)
            viewHolder.txtTitle.setTextColor(textColor)
            viewHolder.exitButton.findViewById<ImageView>(R.id.deleteButton).setColorFilter(textColor)
            // Set background appropriate for background tab
            viewHolder.layout.background = BackgroundDrawable(viewHolder.layout.context)
        }

    }


}
