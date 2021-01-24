package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.utils.ItemDragDropSwipeViewHolder
import acr.browser.lightning.view.BackgroundDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * The [RecyclerView.ViewHolder] for both vertical and horizontal tabs.
 * That represents an item in our list, basically one tab.
 */
class TabViewHolder(
    view: View,
    private val uiController: UIController
) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener, ItemDragDropSwipeViewHolder {

    // Using view binding won't give us much
    val txtTitle: TextView = view.findViewById(R.id.textTab)
    val favicon: ImageView = view.findViewById(R.id.faviconTab)
    val exitButton: View = view.findViewById(R.id.deleteAction)
    val layout: LinearLayout = view.findViewById(R.id.tab_item_background)
    // Keep a copy of our tab data to be able to understand what was changed on update
    // TODO: Is that how we should do things?
    var tab: TabViewState = TabViewState()

    private var previousBackground: BackgroundDrawable? = null

    init {
        exitButton.setOnClickListener(this)
        layout.setOnClickListener(this)
        layout.setOnLongClickListener(this)
        // Is that the best way to access our preferences?
        exitButton.visibility = if ((view.context as BrowserActivity).userPreferences.showCloseTabButton) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        if (v === exitButton) {
            uiController.tabCloseClicked(adapterPosition)
        } else if (v === layout) {
            uiController.tabClicked(adapterPosition)
        }
    }

    override fun onLongClick(v: View): Boolean {
        //uiController.showCloseDialog(adapterPosition)
        //return true
        return false
    }

    // From ItemTouchHelperViewHolder
    // Start dragging
    override fun onItemOperationStart() {
        // Do some fancy for smoother transition

        previousBackground = layout.background as BackgroundDrawable
        previousBackground?.let {
                layout.background = BackgroundDrawable(itemView.context, if (it.isSelected)  R.attr.colorSurface else R.attr.colorPrimaryDark, R.attr.colorControlHighlight).apply{startTransition(300)}
            }


        }

    // From ItemTouchHelperViewHolder
    // Stopped dragging
    override fun onItemOperationStop() {
        // Here sadly no transition
        layout.background = previousBackground
    }


}
