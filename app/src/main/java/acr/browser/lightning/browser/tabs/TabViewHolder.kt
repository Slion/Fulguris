package acr.browser.lightning.browser.tabs

import acr.browser.lightning.R
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.view.BackgroundDrawable
import android.graphics.Color
import android.graphics.drawable.Drawable
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
) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener, ItemTouchHelperViewHolder {

    val txtTitle: TextView = view.findViewById(R.id.textTab)
    val favicon: ImageView = view.findViewById(R.id.faviconTab)
    val exitButton: View = view.findViewById(R.id.deleteAction)
    val layout: LinearLayout = view.findViewById(R.id.tab_item_background)

    var previousBackground: BackgroundDrawable? = null

    init {
        exitButton.setOnClickListener(this)
        layout.setOnClickListener(this)
        layout.setOnLongClickListener(this)
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
    override fun onItemSelected() {
        // Do some fancy for smoother transition
        previousBackground = layout.background as BackgroundDrawable
        previousBackground?.let {
                layout.background = BackgroundDrawable(itemView.context, if (it.isSelected)  R.attr.selectedBackground else R.attr.colorPrimaryDark, R.attr.colorControlHighlight).apply{startTransition(300)}
            }
        }

    // From ItemTouchHelperViewHolder
    // Stopped dragging
    override fun onItemClear() {
        // Here sadly no transition
        layout.background = previousBackground
    }


}
