/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.bookmark.LegacyBookmarkImporter
import acr.browser.lightning.bookmark.NetscapeBookmarkFormatImporter
import acr.browser.lightning.database.bookmark.BookmarkExporter
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.di.DatabaseScheduler
import acr.browser.lightning.di.MainScheduler
import acr.browser.lightning.di.injector
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.dialog.DialogItem
import acr.browser.lightning.extensions.resizeAndShow
import acr.browser.lightning.extensions.snackbar
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.activity.SettingsActivity
import acr.browser.lightning.utils.Utils
import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.anthonycr.grant.PermissionsManager
import com.anthonycr.grant.PermissionsResultAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import java.io.File
import java.util.*
import javax.inject.Inject


class BookmarkSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var bookmarkRepository: BookmarkRepository
    @Inject internal lateinit var application: Application
    @Inject internal lateinit var netscapeBookmarkFormatImporter: NetscapeBookmarkFormatImporter
    @Inject internal lateinit var legacyBookmarkImporter: LegacyBookmarkImporter
    @Inject @field:DatabaseScheduler internal lateinit var databaseScheduler: Scheduler
    @Inject @field:MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject internal lateinit var logger: Logger

    private var importSubscription: Disposable? = null
    private var exportSubscription: Disposable? = null

    override fun providePreferencesXmlResource() = R.xml.preference_bookmarks

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        PermissionsManager
            .getInstance()
            .requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS, null)

        clickablePreference(preference = SETTINGS_EXPORT, onClick = this::exportBookmarks)
        clickablePreference(preference = SETTINGS_IMPORT, onClick = this::importBookmarks)
        clickablePreference(preference = SETTINGS_DELETE_BOOKMARKS, onClick = this::deleteAllBookmarks)



    }

    override fun onDestroyView() {
        super.onDestroyView()

        exportSubscription?.dispose()
        importSubscription?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()

        exportSubscription?.dispose()
        importSubscription?.dispose()
    }

    private fun exportBookmarks() {
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS,
            object : PermissionsResultAction() {
                override fun onGranted() {
                    bookmarkRepository.getAllBookmarksSorted()
                        .subscribeOn(databaseScheduler)
                        .subscribe { list ->
                            if (!isAdded) {
                                return@subscribe
                            }

                            val exportFile = BookmarkExporter.createNewExportFile()
                            exportSubscription?.dispose()
                            exportSubscription = BookmarkExporter.exportBookmarksToFile(list, exportFile)
                                .subscribeOn(databaseScheduler)
                                .observeOn(mainScheduler)
                                .subscribeBy(
                                    onComplete = {
                                        activity?.apply {
                                            snackbar("${getString(R.string.bookmark_export_path)} ${exportFile.path}")
                                            // Open file browser to show export folder
                                            Utils.startActivityForFolder(activity,exportFile.parent)
                                        }
                                    },
                                    onError = { throwable ->
                                        logger.log(TAG, "onError: exporting bookmarks", throwable)
                                        val activity = activity
                                        if (activity != null && !activity.isFinishing && isAdded) {
                                            Utils.createInformativeDialog(activity, R.string.title_error, R.string.bookmark_export_failure)
                                        } else {
                                            application.toast(R.string.bookmark_export_failure)
                                        }
                                    }
                                )
                        }
                }

                override fun onDenied(permission: String) {
                    val activity = activity
                    if (activity != null && !activity.isFinishing && isAdded) {
                        Utils.createInformativeDialog(activity, R.string.title_error, R.string.bookmark_export_failure)
                    } else {
                        application.toast(R.string.bookmark_export_failure)
                    }
                }
            })
    }

    private fun importBookmarks() {
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS,
            object : PermissionsResultAction() {
                override fun onGranted() {
                    showImportBookmarkDialog(null)
                }

                override fun onDenied(permission: String) {
                    //TODO Show message
                }
            })
    }

    private fun deleteAllBookmarks() {
        showDeleteBookmarksDialog()
    }

    private fun showDeleteBookmarksDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            activity = activity as Activity,
            title = R.string.action_delete,
            message = R.string.action_delete_all_bookmarks,
            positiveButton = DialogItem(title = R.string.yes) {
                bookmarkRepository
                    .deleteAllBookmarks()
                    .subscribeOn(databaseScheduler)
                    .subscribe()
                // Tell browser activity bookmarks have changed
                (activity as SettingsActivity).userPreferences.bookmarksChanged = true
            },
            negativeButton = DialogItem(title = R.string.no) {},
            onCancel = {}
        )
    }

    private fun loadFileList(path: File?): Array<File> {
        val file: File = path ?: File(Environment.getExternalStorageDirectory().toString())

        try {
            file.mkdirs()
        } catch (e: SecurityException) {
            logger.log(TAG, "Unable to make directory", e)
        }

        return (if (file.exists()) {
            file.listFiles()
        } else {
            arrayOf()
        }).apply {
            sortWith(SortName())
        }
    }

    private class SortName : Comparator<File> {

        override fun compare(a: File, b: File): Int {
            return if (a.isDirectory && b.isDirectory) {
                a.name.compareTo(b.name)
            } else if (a.isDirectory) {
                -1
            } else if (b.isDirectory) {
                1
            } else if (a.isFile && b.isFile) {
                a.name.compareTo(b.name)
            } else {
                1
            }
        }
    }

    /**
     *
     */
    private fun showImportBookmarkDialog(path: File?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "text/html", // .html
                    "text/plain" // .txt
                )
            )
        }
        bookmarkImportFilePicker.launch(intent)
        // See bookmarkImportFilePicker declaration below for result handler
    }

    //
    val bookmarkImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Using content resolver to get an input stream from selected URI
            // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
            result.data?.data?.let{ uri ->
                context?.contentResolver?.openInputStream(uri).let { inputStream ->
                    val mimeType = context?.contentResolver?.getType(uri)
                Single.just(inputStream)
                    .map {
                        if (mimeType == "text/html") {
                            netscapeBookmarkFormatImporter.importBookmarks(it)
                        } else {
                            legacyBookmarkImporter.importBookmarks(it)
                        }
                    }
                    .flatMap {
                        bookmarkRepository.addBookmarkList(it).andThen(Single.just(it.size))
                    }
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy(
                        onSuccess = { count ->
                            activity?.apply {
                                snackbar("$count ${getString(R.string.message_import)}")
                                // Tell browser activity bookmarks have changed
                                (activity as SettingsActivity).userPreferences.bookmarksChanged = true
                            }
                        },
                        onError = {
                            logger.log(TAG, "onError: importing bookmarks", it)
                            val activity = activity
                            if (activity != null && !activity.isFinishing && isAdded) {
                                Utils.createInformativeDialog(activity, R.string.title_error, R.string.import_bookmark_error)
                            } else {
                                application.toast(R.string.import_bookmark_error)
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {

        private const val TAG = "BookmarkSettingsFrag"

        private const val EXTENSION_HTML = "html"

        private const val SETTINGS_EXPORT = "export_bookmark"
        private const val SETTINGS_IMPORT = "import_bookmark"
        private const val SETTINGS_DELETE_BOOKMARKS = "delete_bookmarks"

        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
