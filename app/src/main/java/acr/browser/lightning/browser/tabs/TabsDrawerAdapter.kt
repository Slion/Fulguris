package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.utils.getFilteredColor
import acr.browser.lightning.view.BackgroundDrawable
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.*

/**
 * The adapter for vertical mobile style browser tabs.
 */
class TabsDrawerAdapter(
    private val uiController: UIController
) : RecyclerView.Adapter<TabViewHolder>(), ItemTouchHelperAdapter {

    private var tabList: List<TabViewState> = emptyList()

    fun showTabs(tabs: List<TabViewState>) {
        val oldList = tabList
        tabList = tabs
        DiffUtil.calculateDiff(TabViewStateDiffCallback(oldList, tabList)).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.tab_list_item, viewGroup, false)
        view.background = BackgroundDrawable(view.context)
        return TabViewHolder(view, uiController)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.exitButton.tag = position

        val web = tabList[position]

        holder.txtTitle.text = web.title
        updateViewHolderAppearance(holder, web.favicon, web.themeColor, web.isForegroundTab)
        updateViewHolderFavicon(holder, web.favicon, web.isForegroundTab)
        updateViewHolderBackground(holder, web.isForegroundTab)
    }


/*
    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, favicon: Bitmap?, isForeground: Boolean) {
        // Remove any existing filter
        viewHolder.favicon.clearColorFilter()

        favicon?.let {
            val ba = uiController as BrowserActivity // Nasty cast, I know, who cares :)
            if (ba.isDarkTheme)
            {
                // Use white filter on darkest favicons
                // That works well enough for theregister.co.uk and github.com while not impacting bbc.c.uk
                val color = Color.BLACK or getFilteredColor(it) // OR with opaque black to remove transparency glitches
                val luminance = ColorUtils.calculateLuminance(color)
                // Only apply to darkest icons
                if (luminance==0.0) {
                    viewHolder.favicon.setColorFilter(Color.WHITE)
                }
            }
            viewHolder.favicon.setImageBitmap(it)
        } ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)
    }

 */


    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, favicon: Bitmap?, isForeground: Boolean) {
        // Remove any existing filter
        viewHolder.favicon.clearColorFilter()

        favicon?.let {bitmap ->
            val ba = uiController as BrowserActivity // Nasty cast, I know, who cares :)
            if (ba.isDarkTheme) {
                // Check if favicon is too dark
            Palette.from(bitmap).generate { palette ->
                    // OR with opaque black to remove transparency glitches
                    val filteredColor = Color.BLACK or getFilteredColor(bitmap) // OR with opaque black to remove transparency glitches
                    val filteredLuminance = ColorUtils.calculateLuminance(filteredColor)
                    //val color = Color.BLACK or (it.getVibrantColor(it.getLightVibrantColor(it.getDominantColor(Color.BLACK))))
                    val color = Color.BLACK or (palette?.let { it. getDominantColor(Color.BLACK)} ?: Color.BLACK)
                    val luminance = ColorUtils.calculateLuminance(color)
                    // Lowered threshold from 0.025 to 0.02 for it to work with bbc.com/future
                    val threshold = 0.02
                    // Use white filter on darkest favicons
                    // Filtered luminance  works well enough for theregister.co.uk and github.com while not impacting bbc.c.uk
                    // Luminance from dominant color was added to prevent toytowngermany.com from being filtered
                    if (luminance<threshold && filteredLuminance<threshold) {
                        // All black icon
                        viewHolder.favicon.setColorFilter(Color.WHITE)
                    }
                    /*
                    else if (luminance<threshold) {

                        var colorMatrix = ColorMatrix()
                        var scale = 1.0f + threshold / luminance;
                        var stf = scale.toFloat()
                        colorMatrix.set(floatArrayOf(
                                stf, 0.0f, 0.0f, 0.0f, 0.0f, //RED
                                0.0f, stf, 0.0f, 0.0f, 0.0f, // GREEN
                                0.0f, 0.0f, stf, 0.0f, 0.0f, // BLUE
                                0.0f, 0.0f, 0.0f, 1.0f,  0.0f // ALPHA
                        ))
                        //colorMatrix.setSaturation(0.0F)
                        val colorMatrixColorFilter = ColorMatrixColorFilter(colorMatrix)
                        viewHolder.favicon.setColorFilter(colorMatrixColorFilter)
                        //viewHolder.favicon.setColorFilter(Color.WHITE)
                        }

                     */
                }
            }
                viewHolder.favicon.setImageBitmap(favicon)
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

    private fun updateViewHolderAppearance(viewHolder: TabViewHolder, favicon: Bitmap?, color: Int, isForeground: Boolean) {
        if (isForeground) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
            uiController.changeToolbarBackground(favicon, color, null)
        } else {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.normalText)
        }
    }

    override fun getItemCount() = tabList.size

    // From ItemTouchHelperAdapter
    // An item was was moved through drag & drop
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

    // From ItemTouchHelperAdapter
    override fun onItemDismiss(position: Int)
    {
        uiController.tabCloseClicked(position)
    }



}
