package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.*
import acr.browser.lightning.utils.ThemeUtils
import acr.browser.lightning.utils.Utils
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * The adapter for horizontal desktop style browser tabs.
 */
class TabsDesktopAdapter(
    context: Context,
    private val resources: Resources,
    private val uiController: UIController
) : RecyclerView.Adapter<TabViewHolder>() {

    private val backgroundTabDrawable: Drawable?
    private val foregroundTabBitmap: Bitmap?
    private var tabList: List<TabViewState> = emptyList()
    private var textColor = Color.TRANSPARENT

    init {
        val backgroundColor = Utils.mixTwoColors(ThemeUtils.getPrimaryColor(context), Color.BLACK, 0.75f)
        val backgroundTabBitmap = Bitmap.createBitmap(
            context.dimen(R.dimen.desktop_tab_width),
            context.dimen(R.dimen.desktop_tab_height),
            Bitmap.Config.ARGB_8888
        ).also {
            Canvas(it).drawTrapezoid(backgroundColor, true)
        }
        backgroundTabDrawable = BitmapDrawable(resources, backgroundTabBitmap)

        val foregroundColor = ThemeUtils.getPrimaryColor(context)
        foregroundTabBitmap = Bitmap.createBitmap(
            context.dimen(R.dimen.desktop_tab_width),
            context.dimen(R.dimen.desktop_tab_height),
            Bitmap.Config.ARGB_8888
        ).also {
            Canvas(it).drawTrapezoid(foregroundColor, false)
        }
    }

    fun showTabs(tabs: List<TabViewState>) {
        val oldList = tabList
        tabList = tabs

        DiffUtil.calculateDiff(TabViewStateDiffCallback(oldList, tabList)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.tab_list_item_horizontal, viewGroup, false)
        return TabViewHolder(view, uiController)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.exitButton.tag = position

        val web = tabList[position]

        holder.txtTitle.text = web.title
        updateViewHolderAppearance(holder, web)
        updateViewHolderFavicon(holder, web.favicon, web.isForegroundTab)
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

        if (tab.isForegroundTab) {
            val foregroundDrawable = BitmapDrawable(resources, foregroundTabBitmap)
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
            val newTextColor = (uiController as BrowserActivity).currentToolBarTextColor
            viewHolder.txtTitle.setTextColor(newTextColor)
            viewHolder.exitButton.findViewById<ImageView>(R.id.deleteButton).setColorFilter(newTextColor)
            uiController.changeToolbarBackground(tab.favicon, tab.themeColor, foregroundDrawable)
            if (uiController.isColorMode()) {
                foregroundDrawable.tint(uiController.getUiColor())
            }
            viewHolder.layout.background = foregroundDrawable
        } else {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, if (tab.isFrozen) R.style.italicText else R.style.normalText)
            viewHolder.layout.background = backgroundTabDrawable
            // Put back the color we stashed
            viewHolder.txtTitle.setTextColor(textColor)
            viewHolder.exitButton.findViewById<ImageView>(R.id.deleteButton).setColorFilter(textColor)
        }
    }

    override fun getItemCount() = tabList.size

}
