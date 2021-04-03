package acr.browser.lightning.browser.bookmarks

import acr.browser.lightning.R
import acr.browser.lightning.database.Bookmark
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BookmarkViewHolder(
        itemView: View,
        private val adapter: BookmarksAdapter,
        private val onItemLongClickListener: (Bookmark) -> Boolean,
        private val onItemClickListener: (Bookmark) -> Unit
) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {

    val txtTitle: TextView = itemView.findViewById(R.id.textBookmark)
    val favicon: ImageView = itemView.findViewById(R.id.faviconBookmark)

    init {
        itemView.setOnLongClickListener(this)
        itemView.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        val index = adapterPosition
        if (index.toLong() != RecyclerView.NO_ID) {
            onItemClickListener(adapter.itemAt(index).bookmark)
        }
    }

    override fun onLongClick(v: View): Boolean {
        val index = adapterPosition
        return index != RecyclerView.NO_POSITION && onItemLongClickListener(adapter.itemAt(index).bookmark)
    }
}
