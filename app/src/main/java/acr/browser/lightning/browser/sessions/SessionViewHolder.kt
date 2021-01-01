package acr.browser.lightning.browser.session

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.utils.ItemTouchHelperViewHolder
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
class SessionViewHolder(
        view: View,
        private val uiController: UIController
) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener, ItemTouchHelperViewHolder {


    // Using view binding won't give us much
    val textName: TextView = view.findViewById(R.id.text_name)
    val imageIcon: ImageView = view.findViewById(R.id.image_icon)
    val imageDelete: View = view.findViewById(R.id.image_delete)
    val layout: LinearLayout = view.findViewById(R.id.layout_background)

    private var previousBackground: BackgroundDrawable? = null

    init {
        imageDelete.setOnClickListener(this)
        layout.setOnClickListener(this)
        layout.setOnLongClickListener(this)
        // Is that the best way to access our preferences?
        //imageDelete.visibility = if ((view.context as BrowserActivity).userPreferences.showCloseTabButton) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        if (v === imageDelete) {
            //uiController.tabCloseClicked(adapterPosition)
        } else if (v === layout) {
            // User wants to switch session
            uiController.getTabModel().apply {
                // Save current states
                saveState()
                // Change current session
                iCurrentSessionName = textName.text.toString()
                // Save it again
                saveSessions()
            }
            // Then reload our tabs
            (v.context as BrowserActivity).presenter?.setupTabs(null)
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
