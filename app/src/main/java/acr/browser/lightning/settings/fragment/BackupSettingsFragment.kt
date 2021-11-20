/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.bookmark.LegacyBookmarkImporter
import acr.browser.lightning.bookmark.NetscapeBookmarkFormatImporter
import acr.browser.lightning.browser.TabsManager
import acr.browser.lightning.browser.sessions.Session
import acr.browser.lightning.database.bookmark.BookmarkExporter
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.di.*
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.dialog.DialogItem
import acr.browser.lightning.extensions.*
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.activity.SettingsActivity
import acr.browser.lightning.utils.Utils
import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
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

class BackupSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var bookmarkRepository: BookmarkRepository
    @Inject internal lateinit var application: Application
    @Inject internal lateinit var netscapeBookmarkFormatImporter: NetscapeBookmarkFormatImporter
    @Inject internal lateinit var legacyBookmarkImporter: LegacyBookmarkImporter
    @Inject @field:DatabaseScheduler internal lateinit var databaseScheduler: Scheduler
    @Inject @field:MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject internal lateinit var logger: Logger

    // Need those to implement settings reset
    @Inject @UserPrefs lateinit var prefsUser: SharedPreferences
    @Inject @DevPrefs lateinit var prefsDev: SharedPreferences
    @Inject @PrefsLandscape lateinit var prefsLandscape: SharedPreferences
    @Inject @PrefsPortrait lateinit var prefsPortrait: SharedPreferences
    @Inject @AdBlockPrefs lateinit var prefsAdBlock: SharedPreferences
    //
    @Inject lateinit var tabsManager: TabsManager

    private var importSubscription: Disposable? = null
    private var exportSubscription: Disposable? = null
    private var bookmarksSortSubscription: Disposable? = null

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_backup
    }

    lateinit var sessionsCategory: PreferenceCategory

    override fun providePreferencesXmlResource() = R.xml.preference_backup

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        injector.inject(this)

        PermissionsManager
            .getInstance()
            .requestPermissionsIfNecessaryForResult(activity, REQUIRED_PERMISSIONS, null)

        // Bookmarks
        clickablePreference(preference = SETTINGS_EXPORT, onClick = this::exportBookmarks)
        clickablePreference(preference = SETTINGS_IMPORT, onClick = this::importBookmarks)
        clickablePreference(preference = SETTINGS_DELETE_BOOKMARKS, onClick = this::deleteAllBookmarks)
        clickablePreference(preference = SETTINGS_SETTINGS_EXPORT, onClick = this::requestSettingsExport)
        clickablePreference(preference = SETTINGS_SETTINGS_IMPORT, onClick = this::requestSettingsImport)

        // Sessions
        clickablePreference(preference = getString(R.string.pref_key_sessions_import), onClick = this::showSessionImportDialog)
        //clickablePreference(preference = getString(R.string.pref_key_sessions_reset), onClick = this::deleteAllBookmarks)

        sessionsCategory = findPreference(getString(R.string.pref_key_session_export_category))!!

        // Populate our sessions
        tabsManager.iSessions.forEach { s -> addPreferenceSessionExport(s) }

        // Handle reset settings option
        clickableDynamicPreference(
                preference = getString(R.string.pref_key_reset_settings),
                onClick = this::resetSettings
        )
    }

    /**
     * Add a preference corresponding to the give session.
     */
    private fun addPreferenceSessionExport(aSession: Session) {
        // We invite user to installer our Google Play Store release
        val pref = Preference(context)

        pref.icon = requireContext().drawable(R.drawable.ic_file_export_oval)

        // Show tab count if any
        if (aSession.tabCount>0) {
            pref.title = aSession.name + " - " +  aSession.tabCount
        } else {
            pref.title = aSession.name
        }

        //pref.summary = resources.getString(R.string.pref_summary_contribute_translations)
        //pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_translate, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open up Fulguris Crowdin project page
            //startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdin.com/project/fulguris-web-browser")))
            showSessionExportDialog(aSession.name, aSession.tabCount, tabsManager.fileFromSessionName(aSession.name))
            true
        }
        sessionsCategory.addPreference(pref)
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
    private val bookmarkImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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

    /**
     * @param summaryUpdater the command which allows the summary to be updated.
     */
    private fun resetSettings(summaryUpdater: SummaryUpdater) {
        // Show confirmation dialog and proceed if needed
        MaterialAlertDialogBuilder(requireContext())
                .setCancelable(true)
                .setTitle(R.string.reset_settings)
                .setMessage(R.string.reset_settings_confirmation)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    prefsUser.edit().clear().apply()
                    prefsDev.edit().clear().apply()
                    prefsLandscape.edit().clear().apply()
                    prefsPortrait.edit().clear().apply()
                    prefsAdBlock.edit().clear().apply()
                    // That closes our settings activity and going back to our browser activity
                    // On resume the browser activity will decide if it needs to restart
                    activity?.finish()
                    //activity?.supportFragmentManager?.popBackStackImmediate()
                }
                .resizeAndShow()

    }

    /**
     *
     */
    private fun showSessionExportDialog(aName: String, aTabCount: Int, aFile: File) {
        //TODO: specify default path
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = KSessionMimeType // Specify type of newly created document

            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = SimpleDateFormat("-yyyy-MM-dd-HH-mm-ss", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
            // Specify default file name, user can change it.
            // If that file already exists a numbered suffix is automatically generated and appended to the file name between brackets.
            // That is a neat feature as it guarantee no file will be overwritten.
            putExtra(Intent.EXTRA_TITLE, "$aName$timeStamp-[$aTabCount]")
        }
        iSessionFile = aFile
        iSessionName = aName
        sessionExportFilePicker.launch(intent)
        // TODO: Display wait dialog?
    }

    private var iSessionFile: File? = null
    private var iSessionName: String = ""
    private val sessionExportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {

            // Using content resolver to get an input stream from selected URI
            // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
            result.data?.data?.let{ uri ->
                // Copy our session file to user selected path
                context?.contentResolver?.openOutputStream(uri)?.let { outputStream ->
                    val input = FileInputStream(iSessionFile)
                    outputStream.write(input.readBytes())
                    input.close()
                    outputStream.flush()
                    outputStream.close()
                    iSessionFile = null
                }

                activity?.snackbar(getString(R.string.message_session_exported,iSessionName))

            }
        }
    }

    /**
     *
     */
    private fun showSessionImportDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // That's needed for some reason, crashes otherwise
            putExtra(
                    // List all file types you want the user to be able to select
                    Intent.EXTRA_MIME_TYPES, arrayOf(
                    KSessionMimeType
                    )
            )
        }
        sessionImportFilePicker.launch(intent)
        // See bookmarkImportFilePicker declaration below for result handler
    }

    private val sessionImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Using content resolver to get an input stream from selected URI
            // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
            result.data?.data?.let{ uri ->
                context?.contentResolver?.openInputStream(uri).let { input ->

                    // Build up our session name
                    val fileName = application.filesDir?.path + '/' + TabsManager.FILENAME_SESSION_PREFIX + uri.fileName
                    //val file = File.createTempFile(TabsManager.FILENAME_SESSION_PREFIX + uri.fileName,"",application.filesDir)

                    // Make sure our file name is unique and short
                    // TODO: move this into an utility function somewhere
                    var i = 0
                    var file = File(fileName)
                    while (file.exists()) {
                        i++
                        file = File(fileName + i.toString())
                    }
                    file.createNewFile()
                    // Write our session file
                    val output = FileOutputStream(file)
                    output.write(input?.readBytes())
                    input?.close()
                    output.flush()
                    output.close()
                    // Workout session name
                    val sessionName = file.name.substring(TabsManager.FILENAME_SESSION_PREFIX.length);
                    // Add imported session to our session collection in our tab manager
                    val session = Session(sessionName)
                    tabsManager.iSessions.add(session)
                    // Make sure we persist our imported session
                    tabsManager.saveSessions()
                    // Add imported session to our preferences list
                    addPreferenceSessionExport(session)

                    activity?.snackbar(getString(R.string.message_session_imported,sessionName))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestSettingsImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, IMPORT_SETTINGS)
    }

    @Suppress("DEPRECATION")
    private fun requestSettingsExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = java.text.SimpleDateFormat("-yyyy-MM-dd-(HH:mm)", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
            // That is a neat feature as it guarantee no file will be overwritten.
            putExtra(Intent.EXTRA_TITLE, "StyxSettings$timeStamp.txt")
        }
        startActivityForResult(intent, EXPORT_SETTINGS)
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
        private const val KSessionMimeType = "application/octet-stream"

        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        const val EXPORT_SETTINGS = 0
        const val IMPORT_SETTINGS = 1
    }
}
