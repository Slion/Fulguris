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
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.activity.SettingsActivity
import acr.browser.lightning.utils.Utils
import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.anthonycr.grant.PermissionsManager
import com.anthonycr.grant.PermissionsResultAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.*
import javax.inject.Inject

class ImportExportSettingsFragment : AbstractSettingsFragment() {

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

    override fun providePreferencesXmlResource() = R.xml.preference_import_export

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS, null)

        clickablePreference(preference = SETTINGS_EXPORT, onClick = this::exportBookmarks)
        clickablePreference(preference = SETTINGS_IMPORT, onClick = this::importBookmarks)
        clickablePreference(preference = SETTINGS_DELETE_BOOKMARKS, onClick = this::deleteAllBookmarks)
        clickablePreference(preference = SETTINGS_SETTINGS_EXPORT, onClick = this::requestSettingsExport)
        clickablePreference(preference = SETTINGS_SETTINGS_IMPORT, onClick = this::requestSettingsImport)
        clickablePreference(preference = SETTINGS_DELETE_SETTINGS, onClick = this::clearSettings)

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

    private fun requestSettingsImport(){
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, IMPORT_SETTINGS)
    }

    private fun requestSettingsExport(){
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = SimpleDateFormat("-yyyy-MM-dd-(HH:mm:ss)", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
            // That is a neat feature as it guarantee no file will be overwritten.
            putExtra(Intent.EXTRA_TITLE, "FulgurisSettings$timeStamp.txt")
        }
        startActivityForResult(intent, EXPORT_SETTINGS)
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
                        activity?.snackbar(R.string.bookmark_export_failure)
                    }
                }
            })
    }

    private fun importBookmarks() {
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS,
            object : PermissionsResultAction() {
                override fun onGranted() {
                    showImportBookmarksDialog()
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
            }

            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = SimpleDateFormat("-yyyy-MM-dd-(HH:mm:ss)", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
        // Specify default file name, user can change it.
        // If that file already exists a numbered suffix is automatically generated and appended to the file name between brackets.
        // That is a neat feature as it guarantee no file will be overwritten.
        putExtra(Intent.EXTRA_TITLE, "FulgurisBookmarks$timeStamp.txt")
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
                                            activity?.snackbar(R.string.bookmark_export_failure)
                                        }
                                    }
                                )
                        }
                }
            }
        }
    }

    /**
     * Starts bookmarks import workflow by showing file selection dialog.
     */
    private fun showImportBookmarksDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // That's needed for some reason, crashes otherwise
            putExtra(
                // List all file types you want the user to be able to select
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
                                activity?.snackbar(R.string.import_bookmark_error)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun importSettings(uri: Uri) {
        val input: InputStream? = requireActivity().contentResolver.openInputStream(uri)

        val bufferSize = 1024
        val buffer = CharArray(bufferSize)
        val out = StringBuilder()
        val `in`: Reader = InputStreamReader(input, "UTF-8")
        while (true) {
            val rsz = `in`.read(buffer, 0, buffer.size)
            if (rsz < 0) break
            out.append(buffer, 0, rsz)
        }

        val content = out.toString()

        val answer = JSONObject(content)
        val keys: JSONArray? = answer.names()
        val userPref = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)
        for (i in 0 until keys!!.length()) {
            val key: String = keys.getString(i)
            val value: String = answer.getString(key)
            with (userPref.edit()) {
                if(value.matches("-?\\d+".toRegex())){
                    putInt(key, value.toInt())
                }
                else if(value == "true" || value == "false"){
                    putBoolean(key, value.toBoolean())
                }
                else{
                    putString(key, value)
                }
                apply()
            }

        }
        activity?.snackbar(R.string.settings_reseted)
    }

    private fun exportSettings(uri: Uri) {
        val userPref = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)
        val allEntries: Map<String, *> = userPref!!.all
        var string = "{"
        for (entry in allEntries.entries) {
            string += "\"${entry.key}\"=\"${entry.value}\","
        }

        string = string.substring(0, string.length - 1) + "}"

        try {
            val output: OutputStream? = requireActivity().contentResolver.openOutputStream(uri)

            output?.write(string.toByteArray())
            output?.flush()
            output?.close()
            activity?.snackbar("${getString(R.string.settings_exported)} ${uri.fileName}")
        } catch (e: IOException) {
            activity?.snackbar(R.string.settings_export_failure)
        }
    }

    private fun clearSettings() {
        val builder = MaterialAlertDialogBuilder(activity as Activity)
        builder.setTitle(getString(R.string.action_delete))
        builder.setMessage(getString(R.string.clean_settings))


        builder.setPositiveButton(resources.getString(R.string.action_ok)){ _, _ ->
            activity?.snackbar(R.string.settings_reseted)

            val handler = Handler()
            handler.postDelayed({
                (activity?.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                    .clearApplicationUserData()
            }, 500)
        }
        builder.setNegativeButton(resources.getString(R.string.action_cancel)){ _, _ ->

        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri: Uri? = data?.data
        if(requestCode == EXPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if(uri != null){
                exportSettings(uri)
            }
        }
        else if(requestCode == IMPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if(uri != null){
                importSettings(uri)
            }
        }
    }

    companion object {

        private const val TAG = "BookmarkSettingsFrag"

        private const val SETTINGS_EXPORT = "export_bookmark"
        private const val SETTINGS_IMPORT = "import_bookmark"
        private const val SETTINGS_DELETE_BOOKMARKS = "delete_bookmarks"
        private const val SETTINGS_SETTINGS_EXPORT = "export_settings"
        private const val SETTINGS_SETTINGS_IMPORT = "import_settings"
        private const val SETTINGS_DELETE_SETTINGS = "clear_settings"

        const val EXPORT_SETTINGS = 0
        const val IMPORT_SETTINGS = 1

        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
