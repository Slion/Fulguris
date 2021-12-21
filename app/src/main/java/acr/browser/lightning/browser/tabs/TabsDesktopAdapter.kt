package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.*
import acr.browser.lightning.utils.ItemDragDropSwipeAdapter
import acr.browser.lightning.utils.ThemeUtils
import acr.browser.lightning.view.BackgroundDrawable
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
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
    private var foregroundTabColor: Int = Color.TRANSPARENT

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
        updateViewHolderFavicon(holder, tab)
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy();
    }


    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, tab: TabViewState) {
        // Apply filter to favicon if needed
        tab.favicon?.let {
            val ba = uiController as BrowserActivity
            if (tab.isForeground) {
                // Make sure that on light theme with dark tab background because color mode we still inverse favicon color if needed, see github.com
                viewHolder.favicon.setImageForTheme(it, ColorUtils.calculateLuminance(foregroundTabColor)<0.2)
            }
            else {
                viewHolder.favicon.setImageForTheme(it, ba.useDarkTheme)
            }
        } ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)
    }

    private fun updateViewHolderAppearance(viewHolder: TabViewHolder, tab: TabViewState) {

        // Just to init our default text color
        if (textColor == Color.TRANSPARENT) {
            textColor = viewHolder.txtTitle.currentTextColor
        }
	
        if (tab.isForeground) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
            val newTextColor = (uiController as BrowserActivity).currentToolBarTextColor
            viewHolder.txtTitle.setTextColor(newTextColor)
            viewHolder.exitButton.findViewById<ImageView>(R.id.deleteButton).setColorFilter(newTextColor)
            // If we just got to the foreground
            if (tab.isForeground!=viewHolder.tab.isForeground
                    // or if our theme color changed
                    || tab.themeColor!=viewHolder.tab.themeColor
                    // or if our theme color is different than our UI color, i.e. using favicon color instead of meta theme
                    || tab.themeColor!=uiController.getUiColor()) {

                val backgroundColor = ThemeUtils.getColor(viewHolder.iCardView.context, R.attr.colorSurface)

                // Pick our color according to settings and states
                foregroundTabColor = if (uiController.isColorMode())
                    if (tab.themeColor!=Color.TRANSPARENT)
                    // Use meta theme color if we have one
                        tab.themeColor
                    else
                        if (uiController.getUiColor()!=backgroundColor)
                        // Use favicon extracted color if there is one
                            uiController.getUiColor()
                        else
                        // Otherwise use default theme color
                            backgroundColor
                else // No color mode just use our theme default background then
                    backgroundColor

                // Apply proper color then
                viewHolder.iCardView.backgroundTintList = ColorStateList.valueOf(foregroundTabColor)

                if (foregroundTabColor==backgroundColor) {
                    // Make sure we can tell which tab is the current one when not using color mode
                    viewHolder.iCardView.isCheckable = true
                    viewHolder.iCardView.isChecked = true
                } else {
                    viewHolder.iCardView.isChecked = false
                    viewHolder.iCardView.isCheckable = false
                }

            }
        }
        else {
            // Reset background color, we did not have to make a backup of it since it's null anyway
            viewHolder.iCardView.backgroundTintList = null
            viewHolder.iCardView.isChecked = false
            viewHolder.iCardView.isCheckable = false
            // Background tab
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, if (tab.isFrozen) R.style.italicText else R.style.normalText)
            viewHolder.txtTitle.setTextColor(textColor)
            viewHolder.exitButton.findViewById<ImageView>(R.id.deleteButton).setColorFilter(textColor)
        }
    }
}
