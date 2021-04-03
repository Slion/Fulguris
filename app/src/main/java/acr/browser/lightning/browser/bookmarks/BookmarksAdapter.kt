package acr.browser.lightning.browser.bookmarks

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.database.Bookmark
import acr.browser.lightning.extensions.drawable
import acr.browser.lightning.extensions.setImageForTheme
import acr.browser.lightning.favicon.FaviconModel
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import java.util.concurrent.ConcurrentHashMap

class BookmarksAdapter(
        val context: Context,
        private val faviconModel: FaviconModel,
        private val networkScheduler: Scheduler,
        private val mainScheduler: Scheduler,
        private val onItemLongClickListener: (Bookmark) -> Boolean,
        private val onItemClickListener: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkViewHolder>() {

    private var bookmarks: List<BookmarksViewModel> = listOf()
    private val faviconFetchSubscriptions = ConcurrentHashMap<String, Disposable>()
    private val folderIcon = context.drawable(R.drawable.ic_folder)
    private val webpageIcon = context.drawable(R.drawable.ic_webpage)

    fun itemAt(position: Int): BookmarksViewModel = bookmarks[position]

    fun deleteItem(item: BookmarksViewModel) {
        val newList = bookmarks - item
        updateItems(newList)
    }

    fun updateItems(newList: List<BookmarksViewModel>) {
        val oldList = bookmarks
        bookmarks = newList

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = bookmarks.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    oldList[oldItemPosition].bookmark.url == bookmarks[newItemPosition].bookmark.url

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                    oldList[oldItemPosition] == bookmarks[newItemPosition]
        })

        diffResult.dispatchUpdatesTo(this)
    }

    fun cleanupSubscriptions() {
        for (subscription in faviconFetchSubscriptions.values) {
            subscription.dispose()
        }
        faviconFetchSubscriptions.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.bookmark_list_item, parent, false)

        return BookmarkViewHolder(itemView, this, onItemLongClickListener, onItemClickListener)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.itemView.jumpDrawablesToCurrentState()

        val viewModel = bookmarks[position]
        holder.txtTitle.text = viewModel.bookmark.title

        val url = viewModel.bookmark.url
        holder.favicon.tag = url

        viewModel.icon?.let {
            holder.favicon.setImageBitmap(it)
            return
        }

        val imageDrawable = when (viewModel.bookmark) {
            is Bookmark.Folder -> folderIcon
            is Bookmark.Entry -> webpageIcon.also {
                faviconFetchSubscriptions[url]?.dispose()
                faviconFetchSubscriptions[url] = faviconModel
                        .faviconForUrl(url, viewModel.bookmark.title)
                        .subscribeOn(networkScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy(
                                onSuccess = { bitmap ->
                                    viewModel.icon = bitmap
                                    if (holder.favicon.tag == url) {
                                        val ba = context as BrowserActivity
                                        holder.favicon.setImageForTheme(bitmap, ba.useDarkTheme)
                                    }
                                }
                        )
            }
        }

        holder.favicon.setImageDrawable(imageDrawable)
    }

    override fun getItemCount() = bookmarks.size
}
