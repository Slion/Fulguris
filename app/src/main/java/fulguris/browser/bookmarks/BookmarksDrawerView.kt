package fulguris.browser.bookmarks

import fulguris.R
import fulguris.animation.AnimationUtils
import fulguris.browser.BookmarksView
import fulguris.browser.TabsManager
import fulguris.browser.WebBrowser
import fulguris.constant.FOLDER
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
                // Navigate to parent folder
                val parentFolder = getParentFolder(uiModel.currentFolder)
                setBookmarksShown(parentFolder, true)
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
        bookmarksSubscription = Single.zip(
            bookmarkModel.getSubFoldersSorted(folder),
            bookmarkModel.getBookmarksFromFolderSorted(folder)
        ) { folders, bookmarks ->
            // Combine folders first, then bookmarks
            folders + bookmarks
        }
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { bookmarksAndFolders ->
                uiModel.currentFolder = folder

                // Add parent folder navigation item at the beginning (except for root)
                val itemsWithParent = if (!folder.isNullOrBlank()) {
                    val parentFolder = createParentFolderBookmark(folder)
                    listOf(parentFolder) + bookmarksAndFolders
                } else {
                    bookmarksAndFolders
                }

                setBookmarkDataSet(itemsWithParent, animate)
                // Display only the last segment of the folder path
                val displayTitle = if (folder.isNullOrBlank()) {
                    resources.getString(R.string.action_bookmarks)
                } else {
                    folder.substringAfterLast('/', folder)
                }
                iBinding.textTitle.text = displayTitle
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
        val activity = context as? Activity
        if (activity != null) {
            when (bookmark) {
                is Bookmark.Folder -> bookmarksDialogBuilder.showBookmarkFolderLongPressedDialog(activity, webBrowser, bookmark)
                is Bookmark.Entry -> bookmarksDialogBuilder.showLongPressedDialogForBookmarkUrl(activity, webBrowser, bookmark)
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
            // Handle ".." parent folder navigation
            val targetFolder = if (bookmark.title == "..") {
                // Extract parent folder path from URL (remove FOLDER prefix)
                bookmark.url.removePrefix(FOLDER).takeIf { it.isNotEmpty() }
            } else {
                bookmark.title
            }
            setBookmarksShown(targetFolder, true)
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
            // Navigate to parent folder
            val parentFolder = getParentFolder(uiModel.currentFolder)
            setBookmarksShown(parentFolder, true)
            iBinding.listBookmarks.layoutManager?.scrollToPosition(scrollIndex)
        }
    }

    /**
     * Get the parent folder path from a folder path.
     * Returns null for root-level folders.
     */
    private fun getParentFolder(folderPath: String?): String? {
        if (folderPath.isNullOrEmpty()) return null
        val lastSlashIndex = folderPath.lastIndexOf('/')
        return if (lastSlashIndex > 0) {
            folderPath.substring(0, lastSlashIndex)
        } else {
            null // Parent is root
        }
    }

    /**
     * Create a parent folder bookmark for ".." navigation
     */
    private fun createParentFolderBookmark(currentFolder: String): Bookmark.Folder {
        val parentPath = getParentFolder(currentFolder)
        // Always use ".." as title for navigation, even when going back to root
        return Bookmark.Folder.Entry(
            url = if (parentPath.isNullOrEmpty()) "" else "$FOLDER$parentPath",
            title = ".."
        )
    }

    override fun handleUpdatedUrl(url: String) {
        updateBookmarkIndicator(url)
        val folder = uiModel.currentFolder
        setBookmarksShown(folder, false)
    }



}
