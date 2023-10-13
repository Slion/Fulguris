package fulguris.browser.bookmarks

import fulguris.R
import fulguris.animation.AnimationUtils
import fulguris.browser.BookmarksView
import fulguris.browser.TabsManager
import fulguris.browser.WebBrowser
import fulguris.database.Bookmark
import fulguris.database.bookmark.BookmarkRepository
import fulguris.databinding.BookmarkDrawerViewBinding
import fulguris.dialog.BrowserDialog
import fulguris.dialog.DialogItem
import fulguris.dialog.LightningDialogBuilder
import fulguris.extensions.drawable
import fulguris.extensions.inflater
import fulguris.favicon.FaviconModel
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.ItemDragDropSwipeHelper
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import fulguris.di.DatabaseScheduler
import fulguris.di.MainScheduler
import fulguris.di.NetworkScheduler
import fulguris.di.configPrefs
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import javax.inject.Inject

/**
 * The view that displays bookmarks in a list and some controls.
 */
@AndroidEntryPoint
class BookmarksDrawerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr),
    BookmarksView {

    @Inject internal lateinit var bookmarkModel: BookmarkRepository
    @Inject internal lateinit var bookmarksDialogBuilder: LightningDialogBuilder
    @Inject internal lateinit var faviconModel: FaviconModel
    @Inject @DatabaseScheduler
    internal lateinit var databaseScheduler: Scheduler
    @Inject @NetworkScheduler
    internal lateinit var networkScheduler: Scheduler
    @Inject @MainScheduler
    internal lateinit var mainScheduler: Scheduler
    @Inject
    lateinit var iUserPreferences: UserPreferences

    private val webBrowser: WebBrowser = context as WebBrowser

    // Adapter
    private var iAdapter: BookmarksAdapter
    // Drag & drop support
    private var iItemTouchHelper: ItemTouchHelper? = null

    // Colors
    private var scrollIndex: Int = 0

    private var bookmarksSubscription: Disposable? = null
    private var bookmarkUpdateSubscription: Disposable? = null

    private val uiModel = BookmarkUiModel()
    var iBinding: BookmarkDrawerViewBinding = BookmarkDrawerViewBinding.inflate(context.inflater,this, true)

    init {
        iBinding.uiController = webBrowser


        iBinding.bookmarkBackButton.setOnClickListener {
            if (!uiModel.isCurrentFolderRoot()) {
                setBookmarksShown(null, true)
                iBinding.listBookmarks.layoutManager?.scrollToPosition(scrollIndex)
            }
        }

        iAdapter = BookmarksAdapter(
                context,
                webBrowser,
                bookmarkModel,
                faviconModel,
                networkScheduler,
                mainScheduler,
                databaseScheduler,
                ::showBookmarkMenu,
                ::openBookmark
            )

        iBinding.listBookmarks.apply {
            // Reverse layout if using bottom tool bars
            // LinearLayoutManager.setReverseLayout is also adjusted from BrowserActivity.setupToolBar
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, context.configPrefs.toolbarsBottom)
            adapter = iAdapter
        }

        // Enable drag & drop but not swipe
        val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(iAdapter, true, false)
        iItemTouchHelper = ItemTouchHelper(callback)
        iItemTouchHelper?.attachToRecyclerView(iBinding.listBookmarks)

        setBookmarksShown(null, true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        bookmarksSubscription?.dispose()
        bookmarkUpdateSubscription?.dispose()

        iAdapter?.cleanupSubscriptions()
    }

    private fun getTabsManager(): TabsManager = webBrowser.getTabModel()

    // TODO: apply that logic to the add bookmark menu item from main pop-up menu
    // SL: I guess this is of no use here anymore since we removed the add bookmark button
    private fun updateBookmarkIndicator(url: String) {
        bookmarkUpdateSubscription?.dispose()
        bookmarkUpdateSubscription = bookmarkModel.isBookmark(url)
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { isBookmark ->
                bookmarkUpdateSubscription = null
                //addBookmarkView?.isSelected = isBookmark
                //addBookmarkView?.isEnabled = !url.isSpecialUrl()
            }
    }

    override fun handleBookmarkDeleted(bookmark: Bookmark) = when (bookmark) {
        is Bookmark.Folder -> setBookmarksShown(null, false)
        is Bookmark.Entry -> iAdapter.deleteItem(BookmarksViewModel(bookmark)) ?: Unit
    }

    /**
     *
     */
    private fun setBookmarksShown(folder: String?, animate: Boolean) {
        bookmarksSubscription?.dispose()
        bookmarksSubscription = bookmarkModel.getBookmarksFromFolderSorted(folder)
            .concatWith(Single.defer {
                if (folder == null) {
                    bookmarkModel.getFoldersSorted()
                } else {
                    Single.just(emptyList())
                }
            })
            .toList()
            .map { it.flatten() }
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { bookmarksAndFolders ->
                uiModel.currentFolder = folder
                setBookmarkDataSet(bookmarksAndFolders, animate)
                iBinding.textTitle.text = if (folder.isNullOrBlank()) resources.getString(R.string.action_bookmarks) else folder
            }
    }

    /**
     *
     */
    private fun setBookmarkDataSet(items: List<Bookmark>, animate: Boolean) {
        iAdapter.updateItems(items.map { BookmarksViewModel(it) })
        val resource = if (uiModel.isCurrentFolderRoot()) {
            R.drawable.ic_bookmarks
        } else {
            R.drawable.ic_action_back
        }

        if (animate) {
            iBinding.bookmarkBackButton.let {
                val transition = AnimationUtils.createRotationTransitionAnimation(it, resource)
                it.startAnimation(transition)
            }
        } else {
            iBinding.bookmarkBackButton.setImageResource(resource)
        }
    }

    /**
     *
     */
    private fun showBookmarkMenu(bookmark: Bookmark): Boolean {
        (context as Activity?)?.let {
            when (bookmark) {
                is Bookmark.Folder -> bookmarksDialogBuilder.showBookmarkFolderLongPressedDialog(it, webBrowser, bookmark)
                is Bookmark.Entry -> bookmarksDialogBuilder.showLongPressedDialogForBookmarkUrl(it, webBrowser, bookmark)
            }
        }
        return true
    }

    /**
     *
     */
    private fun openBookmark(bookmark: Bookmark) = when (bookmark) {
        is Bookmark.Folder -> {
            scrollIndex = (iBinding.listBookmarks.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            setBookmarksShown(bookmark.title, true)
        }
        is Bookmark.Entry -> webBrowser.bookmarkItemClicked(bookmark)
    }


    /**
     * Show the page tools dialog.
     */
    private fun showPageToolsDialog(context: Context) {
        val currentTab = getTabsManager().currentTab ?: return

        BrowserDialog.showWithIcons(context, context.getString(R.string.dialog_tools_title),
            DialogItem(
                icon = context.drawable(R.drawable.ic_action_desktop),
                title = R.string.dialog_toggle_desktop
            ) {
                getTabsManager().currentTab?.apply {
                    toggleDesktopUserAgent()
                    reload()
                    // TODO add back drawer closing
                }
            },
        )
    }

    override fun navigateBack() {
        if (uiModel.isCurrentFolderRoot()) {
            webBrowser.onBackButtonPressed()
        } else {
            setBookmarksShown(null, true)
            iBinding.listBookmarks.layoutManager?.scrollToPosition(scrollIndex)
        }
    }

    override fun handleUpdatedUrl(url: String) {
        updateBookmarkIndicator(url)
        val folder = uiModel.currentFolder
        setBookmarksShown(folder, false)
    }



}
