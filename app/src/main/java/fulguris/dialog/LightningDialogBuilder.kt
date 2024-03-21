package fulguris.dialog

import fulguris.activity.MainActivity
import fulguris.R
import fulguris.browser.WebBrowser
import fulguris.database.Bookmark
import fulguris.database.asFolder
import fulguris.database.bookmark.BookmarkRepository
import fulguris.database.downloads.DownloadsRepository
import fulguris.database.history.HistoryRepository
import fulguris.di.DatabaseScheduler
import fulguris.di.MainScheduler
import fulguris.extensions.*
import fulguris.html.bookmark.BookmarkPageFactory
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.isBookmarkUrl
import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.os.Build
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.core.net.toUri
import com.anthonycr.grant.PermissionsManager
import com.anthonycr.grant.PermissionsResultAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.Reusable
import fulguris.extensions.copyToClipboard
import fulguris.extensions.onConfigurationChange
import fulguris.extensions.onFocusGained
import fulguris.extensions.resizeAndShow
import fulguris.extensions.snackbar
import fulguris.extensions.toast
import fulguris.utils.shareUrl
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * A builder of various dialogs.
 */
@Reusable
class LightningDialogBuilder @Inject constructor(
    private val bookmarkManager: BookmarkRepository,
    private val downloadsModel: DownloadsRepository,
    private val historyModel: HistoryRepository,
    private val userPreferences: UserPreferences,
    private val downloadHandler: fulguris.download.DownloadHandler,
    private val clipboardManager: ClipboardManager,
    @DatabaseScheduler private val databaseScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler
) {

    enum class NewTab {
        FOREGROUND,
        BACKGROUND,
        INCOGNITO
    }


    /**
     * Show the appropriated dialog for the long pressed link.
     * SL: Not used since we don't have a download list anymore.
     *
     * @param activity used to show the dialog
     * @param url      the long pressed url
     */
    // TODO allow individual downloads to be deleted.
    fun showLongPressedDialogForDownloadUrl(
        activity: Activity,
        webBrowser: WebBrowser,
        url: String
    ) =
        BrowserDialog.show(activity, R.string.action_downloads,
            DialogItem(title = R.string.dialog_delete_all_downloads) {
                downloadsModel.deleteAllDownloads()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe(webBrowser::handleDownloadDeleted)
            })

    /**
     * Show the appropriated dialog for the long pressed link. It means that we try to understand
     * if the link is relative to a bookmark or is just a folder.
     *
     * @param activity used to show the dialog
     * @param url      the long pressed url
     */
    fun showLongPressedDialogForBookmarkUrl(
        activity: Activity,
        webBrowser: WebBrowser,
        url: String
    ) {
        if (url.isBookmarkUrl()) {
            // TODO hacky, make a better bookmark mechanism in the future
            val uri = url.toUri()
            val filename = requireNotNull(uri.lastPathSegment) { "Last segment should always exist for bookmark file" }
            val folderTitle = filename.substring(0, filename.length - BookmarkPageFactory.FILENAME.length - 1)
            showBookmarkFolderLongPressedDialog(activity, webBrowser, folderTitle.asFolder())
        } else {
            bookmarkManager.findBookmarkForUrl(url)
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { historyItem ->
                    // TODO: 6/14/17 figure out solution to case where slashes get appended to root urls causing the item to not exist
                    showLongPressedDialogForBookmarkUrl(activity, webBrowser, historyItem)
                }
        }
    }

    /**
     * Show bookmark context menu.
     */
    fun showLongPressedDialogForBookmarkUrl(
        activity: Activity,
        webBrowser: WebBrowser,
        entry: Bookmark.Entry
    ) =
        BrowserDialog.show(
            activity, null, "", false, DialogTab(
                show = true, icon = R.drawable.ic_bookmark, title = R.string.dialog_title_bookmark, items = arrayOf(
                    DialogItem(title = R.string.dialog_open_new_tab) {
                        webBrowser.handleNewTab(NewTab.FOREGROUND, entry.url)
                    },
                    DialogItem(title = R.string.dialog_open_background_tab) {
                        webBrowser.handleNewTab(NewTab.BACKGROUND, entry.url)
                    },
                    DialogItem(
                        title = R.string.dialog_open_incognito_tab,
                        show = activity is MainActivity
                    ) {
                        webBrowser.handleNewTab(NewTab.INCOGNITO, entry.url)
                    },
                    DialogItem(title = R.string.action_share) {
                        activity.shareUrl(entry.url, entry.title)
                    },
                    DialogItem(title = R.string.dialog_copy_link) {
                        clipboardManager.copyToClipboard(entry.url)
                    },
                    DialogItem(title = R.string.dialog_remove_bookmark) {
                        bookmarkManager.deleteBookmark(entry)
                            .subscribeOn(databaseScheduler)
                            .observeOn(mainScheduler)
                            .subscribe { success ->
                                if (success) {
                                    webBrowser.handleBookmarkDeleted(entry)
                                }
                            }
                    },
                    DialogItem(title = R.string.dialog_edit_bookmark) {
                        showEditBookmarkDialog(activity, webBrowser, entry)
                    })
            )
        )

    /**
     * Show the add bookmark dialog. Shows a dialog with the title and URL pre-populated.
     */
    fun showAddBookmarkDialog(
        activity: Activity,
        webBrowser: WebBrowser,
        entry: Bookmark.Entry
    ) {
        val editBookmarkDialog = MaterialAlertDialogBuilder(activity)
        editBookmarkDialog.setTitle(R.string.action_add_bookmark)
        val dialogLayout = View.inflate(activity, R.layout.dialog_edit_bookmark, null)
        val getTitle = dialogLayout.findViewById<EditText>(R.id.bookmark_title)
        getTitle.setText(entry.title)
        val getUrl = dialogLayout.findViewById<EditText>(R.id.bookmark_url)
        getUrl.setText(entry.url)
        val getFolder = dialogLayout.findViewById<AutoCompleteTextView>(R.id.bookmark_folder)
        getFolder.setHint(R.string.folder)
        getFolder.setText(entry.folder.title)

        val ignored = bookmarkManager.getFolderNames()
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { folders ->
                    val suggestionsAdapter = ArrayAdapter(activity,
                            android.R.layout.simple_dropdown_item_1line, folders)
                    getFolder.threshold = 1
                    getFolder.onFocusGained { getFolder.showDropDown(); mainScheduler.scheduleDirect{getFolder.selectAll()} }
                    getFolder.setAdapter(suggestionsAdapter)
                    editBookmarkDialog.setView(dialogLayout)
                    editBookmarkDialog.setPositiveButton(activity.getString(R.string.action_ok)) { _, _ ->
                        val folder = getFolder.text.toString().asFolder()
                        // We need to query bookmarks in destination folder to be able to count them and set our new bookmark position
                        bookmarkManager.getBookmarksFromFolderSorted(folder.title).subscribeBy(
                                onSuccess = {
                                    val editedItem = Bookmark.Entry(
                                            title = getTitle.text.toString(),
                                            url = getUrl.text.toString(),
                                            folder = folder,
                                            // Append new bookmark to existing ones by setting its position properly
                                            position = it.count()
                                    )
                                    bookmarkManager.addBookmarkIfNotExists(editedItem)
                                            .subscribeOn(databaseScheduler)
                                            .observeOn(mainScheduler)
                                            .subscribeBy(
                                                    onSuccess = { success ->
                                                        if (success) {
                                                            webBrowser.handleBookmarksChange()
                                                            activity.toast(R.string.message_bookmark_added)
                                                        } else {
                                                            activity.toast(R.string.message_bookmark_not_added)
                                                        }
                                                    }
                                            )
                                }
                        )
                    }
                    editBookmarkDialog.setNegativeButton(R.string.action_cancel) { _, _ -> }
                    editBookmarkDialog.resizeAndShow()
                }
    }

    private fun showEditBookmarkDialog(
        activity: Activity,
        webBrowser: WebBrowser,
        entry: Bookmark.Entry
    ) {
        val editBookmarkDialog = MaterialAlertDialogBuilder(activity)
        editBookmarkDialog.setTitle(R.string.title_edit_bookmark)
        val layout = View.inflate(activity, R.layout.dialog_edit_bookmark, null)
        val getTitle = layout.findViewById<EditText>(R.id.bookmark_title)
        getTitle.setText(entry.title)
        val getUrl = layout.findViewById<EditText>(R.id.bookmark_url)
        getUrl.setText(entry.url)
        val getFolder = layout.findViewById<AutoCompleteTextView>(R.id.bookmark_folder)
        getFolder.setHint(R.string.folder)
        getFolder.setText(entry.folder.title)

        val ignored = bookmarkManager.getFolderNames()
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { folders ->
                    val suggestionsAdapter = ArrayAdapter(activity,
                            android.R.layout.simple_dropdown_item_1line, folders)
                    getFolder.threshold = 1
                    getFolder.onFocusGained { getFolder.showDropDown(); mainScheduler.scheduleDirect{getFolder.selectAll()} }
                    getFolder.setAdapter(suggestionsAdapter)
                    editBookmarkDialog.setView(layout)
                    editBookmarkDialog.setPositiveButton(activity.getString(R.string.action_ok)) { _, _ ->
                        val folder = getFolder.text.toString().asFolder()
                        if (folder.title != entry.folder.title) {
                            // We moved to a new folder we need to adjust our position then
                            bookmarkManager.getBookmarksFromFolderSorted(folder.title).subscribeBy(
                                    onSuccess = {
                                        val editedItem = Bookmark.Entry(
                                                title = getTitle.text.toString(),
                                                url = getUrl.text.toString(),
                                                folder = folder,
                                                position = it.count()
                                        )
                                        bookmarkManager.editBookmark(entry, editedItem)
                                                .subscribeOn(databaseScheduler)
                                                .observeOn(mainScheduler)
                                                .subscribe(webBrowser::handleBookmarksChange)
                                    }
                            )
                        } else {
                            // We remain in the same folder just use existing position then
                            val editedItem = Bookmark.Entry(
                                    title = getTitle.text.toString(),
                                    url = getUrl.text.toString(),
                                    folder = folder,
                                    position = entry.position
                            )
                            bookmarkManager.editBookmark(entry, editedItem)
                                    .subscribeOn(databaseScheduler)
                                    .observeOn(mainScheduler)
                                    .subscribe(webBrowser::handleBookmarksChange)
                        }
                    }
                    val dialog = editBookmarkDialog.resizeAndShow()
                    // Discard it on screen rotation as it's broken anyway
                    layout.onConfigurationChange { dialog.dismiss() }
                }
    }

    fun showBookmarkFolderLongPressedDialog(
        activity: Activity,
        webBrowser: WebBrowser,
        folder: Bookmark.Folder
    ) =
        BrowserDialog.show(
            activity, null, "", false, DialogTab(
                show = true, icon = R.drawable.ic_folder, title = R.string.action_folder, items = arrayOf(
                    DialogItem(title = R.string.dialog_rename_folder) {
                        showRenameFolderDialog(activity, webBrowser, folder)
                    },
                    DialogItem(title = R.string.dialog_remove_folder) {
                        bookmarkManager.deleteFolder(folder.title)
                            .subscribeOn(databaseScheduler)
                            .observeOn(mainScheduler)
                            .subscribe {
                                webBrowser.handleBookmarkDeleted(folder)
                            }
                    })
            )
        )

    private fun showRenameFolderDialog(
        activity: Activity,
        webBrowser: WebBrowser,
        folder: Bookmark.Folder
    ) =
        BrowserDialog.showEditText(
            activity,
            R.string.title_rename_folder,
            R.string.hint_title,
            folder.title,
            R.string.action_ok
        ) { text ->
            if (text.isNotBlank()) {
                val oldTitle = folder.title
                bookmarkManager.renameFolder(oldTitle, text)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe(webBrowser::handleBookmarksChange)
            }
        }

    /**
     * Menu shown when doing a long press on an history list item.
     */
    fun showLongPressedHistoryLinkDialog(
        activity: Activity,
        webBrowser: WebBrowser,
        url: String
    ) =
        BrowserDialog.show(
            activity, null, "", false, DialogTab(
                show = true, icon = R.drawable.ic_history, title = R.string.action_history, items = arrayOf(
                    DialogItem(title = R.string.dialog_open_new_tab) {
                        webBrowser.handleNewTab(NewTab.FOREGROUND, url)
                    },
                    DialogItem(title = R.string.dialog_open_background_tab) {
                        webBrowser.handleNewTab(NewTab.BACKGROUND, url)
                    },
                    DialogItem(
                        title = R.string.dialog_open_incognito_tab,
                        show = activity is MainActivity
                    ) {
                        webBrowser.handleNewTab(NewTab.INCOGNITO, url)
                    },
                    DialogItem(title = R.string.action_share) {
                        activity.shareUrl(url, null)
                    },
                    DialogItem(title = R.string.dialog_copy_link) {
                        clipboardManager.copyToClipboard(url)
                    },
                    DialogItem(title = R.string.dialog_remove_from_history) {
                        historyModel.deleteHistoryEntry(url)
                            .subscribeOn(databaseScheduler)
                            .observeOn(mainScheduler)
                            .subscribe(webBrowser::handleHistoryChange)
                    })
            )
        )

    /**
     * Show a dialog allowing the user to action either a link or an image.
     */
    fun showLongPressLinkImageDialog(
        activity: Activity,
        webBrowser: WebBrowser,
        linkUrl: String,
        imageUrl: String,
        text: String?,
        userAgent: String,
        showLinkTab: Boolean,
        showImageTab: Boolean
    ) =
        BrowserDialog.show(
            activity, null, "", false,
            //Link tab
            DialogTab(show = showLinkTab, icon = R.drawable.ic_link, title = R.string.button_link, items = arrayOf(DialogItem(title = R.string.dialog_open_new_tab) {
                webBrowser.handleNewTab(NewTab.FOREGROUND, linkUrl)
            },
                DialogItem(title = R.string.dialog_open_background_tab) {
                    webBrowser.handleNewTab(NewTab.BACKGROUND, linkUrl)
                },
                DialogItem(
                    title = R.string.dialog_open_incognito_tab,
                    show = activity is MainActivity
                ) {
                    webBrowser.handleNewTab(NewTab.INCOGNITO, linkUrl)
                },
                DialogItem(title = R.string.action_share) {
                    activity.shareUrl(linkUrl, null)
                },
                // Show copy text dialog item if we have some text
                DialogItem(title = R.string.dialog_copy_text, show = !text.isNullOrEmpty()) {
                    if (!text.isNullOrEmpty()) {
                        clipboardManager.copyToClipboard(text)
                        activity.snackbar(R.string.message_text_copied)
                    }
                },
                // Show copy link URL last
                DialogItem(title = R.string.dialog_copy_link, text = linkUrl) {
                    clipboardManager.copyToClipboard(linkUrl)
                    activity.snackbar(R.string.message_link_copied)
                }
            )),
            // Image tab
            DialogTab(show = showImageTab, icon = R.drawable.ic_image, title = R.string.button_image,
                items = arrayOf(DialogItem(title = R.string.dialog_open_new_tab) {
                    webBrowser.handleNewTab(NewTab.FOREGROUND, imageUrl)
                },
                    DialogItem(title = R.string.dialog_open_background_tab) {
                        webBrowser.handleNewTab(NewTab.BACKGROUND, imageUrl)
                    },
                    DialogItem(
                        title = R.string.dialog_open_incognito_tab,
                        show = activity is MainActivity
                    ) {
                        webBrowser.handleNewTab(NewTab.INCOGNITO, imageUrl)
                    },
                    DialogItem(title = R.string.action_share) {
                        activity.shareUrl(imageUrl, null)
                    },
                    DialogItem(
                        title = R.string.action_download,
                        // Do not show download option for data URL as we don't support that for now
                        show = !URLUtil.isDataUrl(imageUrl)
                    ) {
                        Timber.d("Try download image: $imageUrl")

                        fun doDownload() {
                            Timber.d("doDownload")
                            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(imageUrl).lowercase(Locale.ROOT))
                            // Not sure why we should use PNG by default though.
                            // TODO: I think we have some code somewhere that can download something and then check its mime type from its content.
                            downloadHandler.onDownloadStart(
                                activity, userPreferences, imageUrl, userAgent, "attachment", mimeType
                                    ?: "image/png", ""
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Those permissions are not needed anymore from Android 13
                            doDownload()
                        } else {
                            // Ask for required permissions before starting our download
                            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                object :
                                    PermissionsResultAction() {
                                    override fun onGranted() {
                                        Timber.d("onGranted")
                                        doDownload()
                                    }

                                    override fun onDenied(permission: String) {
                                        Timber.d("onDenied")
                                        //TODO show message
                                    }
                                })
                        }
                    },
                    DialogItem(title = R.string.dialog_copy_link, text = imageUrl) {
                        clipboardManager.copyToClipboard(imageUrl)
                        activity.snackbar(R.string.message_link_copied)
                    }
                )),
        )
}
