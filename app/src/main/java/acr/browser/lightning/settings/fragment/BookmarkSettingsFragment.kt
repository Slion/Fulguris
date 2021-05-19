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
import acr.browser.lightning.extensions.fileName
import acr.browser.lightning.extensions.snackbar
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.activity.SettingsActivity
import acr.browser.lightning.utils.Utils
import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.anthonycr.grant.PermissionsManager
import com.anthonycr.grant.PermissionsResultAction
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
    private var bookmarksSortSubscription: Disposable? = null

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
        bookmarksSortSubscription?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()

        exportSubscription?.dispose()
        importSubscription?.dispose()
        bookmarksSortSubscription?.dispose()
    }

    private fun exportBookmarks() {
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS,
            object : PermissionsResultAction() {
                override fun onGranted() {
                    showExportBookmarksDialog()
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
                    showImportBookmarksDialog(null)
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
     * Start bookmarks export workflow by showing file creation dialog.
     */
    private fun showExportBookmarksDialog() {
        //TODO: specify default path
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain" // Specify type of newly created document

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                // Those magic constants were taken from: https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/refs/heads/master/src/com/android/documentsui/base/Providers.java
                val AUTHORITY_DOWNLOADS = "com.android.providers.downloads.documents";
                val ROOT_ID_DOWNLOADS = "downloads";
                val AUTHORITY_STORAGE = "com.android.externalstorage.documents";
                val ROOT_ID_DEVICE = "primary";
                val ROOT_ID_HOME = "home";

                val AUTHORITY_MEDIA = "com.android.providers.media.documents";
                val ROOT_ID_IMAGES = "images_root";
                val ROOT_ID_VIDEOS = "videos_root";
                val ROOT_ID_AUDIO = "audio_root";
                val ROOT_ID_DOCUMENTS = "documents_root";

                //content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2Fnet.slions.fulguris.full.fdroid.debug%2Ffiles%2Fbookm
                // Docs URI looks like: content://com.android.externalstorage.documents/document/primary%3AFulgurisBookmarksExport.txt

                //val uri = DocumentsContract.buildRootUri(AUTHORITY_STORAGE, ROOT_ID_DEVICE)

                //Not really working for whatever reason: content://com.android.externalstorage.documents/document/primary
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_STORAGE, ROOT_ID_DEVICE)

                // No better I guess
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_STORAGE, ROOT_ID_HOME)

                // Working: content://com.android.providers.downloads.documents/document/downloads
                // Downloads seems to be the only folder we can reliably open and write into across Android version
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_DOWNLOADS, ROOT_ID_DOWNLOADS)

                // Not working on Huawei P30 Pro which makes sense cause we could not find it
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_MEDIA, ROOT_ID_DOCUMENTS)


                // Not working:
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_DOWNLOADS,  "/FulgurisBookmarksExport.txt")

                // Not working:
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_DOWNLOADS, ROOT_ID_DOWNLOADS + ":FulgurisBookmarksExport.txt")

                // Not working: content://com.android.providers.downloads.documents/document/downloads%2FFulgurisBookmarksExport.txt
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_DOWNLOADS, ROOT_ID_DOWNLOADS + "/FulgurisBookmarksExport.txt")

                // Not working
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_DOWNLOADS, "raw:FulgurisBookmarksExport.txt")

                //DocumentsContract.createDocument()

                // URIs from downloads looks like that, note the 'raw' element
                //content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FBookmarksExport-4.txt


                //val uri = Uri.fromFile(File(context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/FulgurisBookmarksExport.txt") )

                // It's actually best not to specify the initial URI it seems as it then uses the last one used
                // Specifying anything other than download would not really work anyway
                //val uri = DocumentsContract.buildDocumentUri(AUTHORITY_DOWNLOADS, ROOT_ID_DOWNLOADS)
                //putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)

                //val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd-(HH:mm:ss)", Locale.US)
                val timeStamp: String = dateFormat.format(Date())

                putExtra(Intent.EXTRA_TITLE, "FulgurisBookmarks-$timeStamp.txt")

            }
        }
        bookmarkExportFilePicker.launch(intent)
        // See bookmarkExportFilePicker declaration below for result handler
    }

    //
    val bookmarkExportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {

            // Using content resolver to get an input stream from selected URI
            // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
            result.data?.data?.let{ uri ->
                context?.contentResolver?.openOutputStream(uri)?.let { outputStream ->
                    //val mimeType = context?.contentResolver?.getType(uri)

                    bookmarksSortSubscription = bookmarkRepository.getAllBookmarksSorted()
                        .subscribeOn(databaseScheduler)
                        .subscribe { list ->
                            if (!isAdded) {
                                return@subscribe
                            }

                            exportSubscription?.dispose()
                            exportSubscription = BookmarkExporter.exportBookmarksToFile(list, outputStream)
                                .subscribeOn(databaseScheduler)
                                .observeOn(mainScheduler)
                                .subscribeBy(
                                    onComplete = {
                                        activity?.apply {
                                            snackbar("${getString(R.string.bookmark_export_path)} ${uri.fileName}")
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
            }
        }
    }

    /**
     *
     */
    private fun showImportBookmarksDialog(path: File?) {
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
                    importSubscription?.dispose()
                    importSubscription = Single.just(inputStream)
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

        private const val SETTINGS_EXPORT = "export_bookmark"
        private const val SETTINGS_IMPORT = "import_bookmark"
        private const val SETTINGS_DELETE_BOOKMARKS = "delete_bookmarks"

        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
