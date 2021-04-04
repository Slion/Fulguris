package acr.browser.lightning.browser.bookmarks

import acr.browser.lightning.R
import acr.browser.lightning.database.Bookmark
import acr.browser.lightning.utils.ItemDragDropSwipeViewHolder
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class BookmarkViewHolder(
        itemView: View,
        private val adapter: BookmarksAdapter,
        private val iShowBookmarkMenu: (Bookmark) -> Boolean,
        private val iOpenBookmark: (Bookmark) -> Unit
) : RecyclerView.ViewHolder(itemView), ItemDragDropSwipeViewHolder {

    val txtTitle: TextView = itemView.findViewById(R.id.textBookmark)
    val favicon: ImageView = itemView.findViewById(R.id.faviconBookmark)
    private val iButtonEdit: ImageButton = itemView.findViewById(R.id.button_edit)
    private val iCardView: MaterialCardView = itemView.findViewById(R.id.card_view)

    init {
        itemView.setOnClickListener{
            val index = adapterPosition
            if (index.toLong() != RecyclerView.NO_ID) {
                iOpenBookmark(adapter.itemAt(index).bookmark)
            }
        }

        iButtonEdit.setOnClickListener {
            val index = adapterPosition
            if (index.toLong() != RecyclerView.NO_ID) {
                iShowBookmarkMenu(adapter.itemAt(index).bookmark)
            }
        }
    }

    /**
     * Implements [ItemDragDropSwipeViewHolder.onItemOperationStart]
     * Start dragging
     */
    override fun onItemOperationStart() {
        iCardView.isDragged = true
    }

    /**
     * Implements [ItemDragDropSwipeViewHolder.onItemOperationStop]
     * Stop dragging
     */
    override fun onItemOperationStop() {
        iCardView.isDragged = false
    }

}
