/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */
package fulguris.settings.fragment


import fulguris.R
import fulguris.bookmark.LegacyBookmarkImporter
import fulguris.bookmark.NetscapeBookmarkFormatImporter
import fulguris.browser.TabsManager
import acr.browser.lightning.browser.sessions.Session
import fulguris.database.bookmark.BookmarkExporter
import fulguris.database.bookmark.BookmarkRepository
import fulguris.di.*
import fulguris.dialog.BrowserDialog
import fulguris.dialog.DialogItem
import fulguris.extensions.*
import fulguris.activity.SettingsActivity
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.Utils
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.di.AdBlockPrefs
import fulguris.di.DatabaseScheduler
import fulguris.di.DevPrefs
import fulguris.di.MainScheduler
import fulguris.di.PrefsLandscape
import fulguris.di.PrefsPortrait
import fulguris.di.UserPrefs
import fulguris.extensions.drawable
import fulguris.extensions.fileName
import fulguris.extensions.resizeAndShow
import fulguris.extensions.snackbar
import fulguris.extensions.toast
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.*
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BackupSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var bookmarkRepository: BookmarkRepository
    @Inject internal lateinit var application: Application
    @Inject internal lateinit var netscapeBookmarkFormatImporter: NetscapeBookmarkFormatImporter
    @Inject internal lateinit var legacyBookmarkImporter: LegacyBookmarkImporter
    @Inject @DatabaseScheduler
    internal lateinit var databaseScheduler: Scheduler
    @Inject @MainScheduler
    internal lateinit var mainScheduler: Scheduler

    // Need those to implement settings reset
    @Inject @UserPrefs
    lateinit var prefsUser: SharedPreferences
    @Inject @DevPrefs
    lateinit var prefsDev: SharedPreferences
    @Inject @PrefsLandscape
    lateinit var prefsLandscape: SharedPreferences
    @Inject @PrefsPortrait
    lateinit var prefsPortrait: SharedPreferences
    @Inject @AdBlockPrefs
    lateinit var prefsAdBlock: SharedPreferences

    //
    @Inject
    lateinit var userPreferences: UserPreferences


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
        //injector.inject(this)

        sessionsCategory = findPreference(getString(R.string.pref_key_session_export_category))!!




        clickablePreference(preference = SETTINGS_EXPORT, onClick = this::showExportBookmarksDialog)
        clickablePreference(preference = SETTINGS_IMPORT, onClick = this::showImportBookmarksDialog)
        clickablePreference(preference = SETTINGS_DELETE_BOOKMARKS, onClick = this::deleteAllBookmarks)
        clickablePreference(preference = SETTINGS_SETTINGS_EXPORT, onClick = this::requestSettingsExport)
        clickablePreference(preference = SETTINGS_SETTINGS_IMPORT, onClick = this::requestSettingsImport)

        // Sessions
        clickablePreference(preference = getString(R.string.pref_key_sessions_import), onClick = this::showSessionImportDialog)
        //clickablePreference(preference = getString(R.string.pref_key_sessions_reset), onClick = this::deleteAllBookmarks)


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
        val pref = Preference(requireContext())
        pref.isSingleLineTitle = false
        pref.icon = requireContext().drawable(R.drawable.ic_cloud_upload)

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

    private fun deleteAllBookmarks() : Boolean {
        showDeleteBookmarksDialog()
        return true
    }

    private fun showDeleteBookmarksDialog() {
        BrowserDialog.showPositiveNegativeDialog(
            aContext = activity as Activity,
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
            Timber.e(e, "Unable to make directory")
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
    private fun showExportBookmarksDialog() : Boolean {
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
        return true
    }

    //
    private val bookmarkExportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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
                                        Timber.e(throwable,"onError: exporting bookmarks")
                                        val activity = activity
                                        if (activity != null && !activity.isFinishing && isAdded) {
                                            fulguris.utils.Utils.createInformativeDialog(activity, R.string.title_error, R.string.bookmark_export_failure)
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
    private fun showImportBookmarksDialog() : Boolean {
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

        return true
    }

    //
    @OptIn(DelicateCoroutinesApi::class)
    private val bookmarkImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Using content resolver to get an input stream from selected URI
            // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
            result.data?.data?.let{ uri ->
                context?.contentResolver?.openInputStream(uri).let { inputStream ->
                    // Launch a coroutine using main dispatcher as we want to be able to display error message if needed
                    // We don't mind using global scope as it is a fast process anyway and does not need to be cancelled if user exits the fragment or activity really quick
                    GlobalScope.launch(Dispatchers.Main) {
                        try {
                            // Do our processing using IO dispatcher not to block the main thread
                            val count = withContext(Dispatchers.IO) {
                                val mimeType = context?.contentResolver?.getType(uri)
                                inputStream?.let {
                                    if (mimeType == "text/html") {
                                        netscapeBookmarkFormatImporter.importBookmarks(it)
                                    } else {
                                        legacyBookmarkImporter.importBookmarks(it)
                                    }
                                }?.let {
                                    bookmarkRepository.addBookmarkList(it)
                                    // Return the number of bookmarks we imported
                                    it.count()
                                }
                            }

                            // Back on the main thread we tell our user what we did
                            requireActivity().snackbar("$count ${getString(R.string.message_import)}")
                            // Tell browser activity bookmarks have changed
                            userPreferences.bookmarksChanged = true

                        } catch (ex: Exception) {
                            // Our import failed and we are back on the main thread
                            Timber.d("Error importing bookmarks: ", ex)
                            // TODO: Could just put a snackbar, though that was useful to test our coroutines as it would crash if not on the main thread
                            fulguris.utils.Utils.createInformativeDialog(requireActivity(), R.string.title_error, R.string.import_bookmark_error)
                        }
                    }
                }
            }
        }
    }

    /**
     * @param summaryUpdater the command which allows the summary to be updated.
     */
    private fun resetSettings(summaryUpdater: SummaryUpdater): Boolean {
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

        return true
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
    private fun showSessionImportDialog() : Boolean {
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
        return true
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
    private fun requestSettingsImport() : Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, IMPORT_SETTINGS)
        return true
    }

    @Suppress("DEPRECATION")
    private fun requestSettingsExport() : Boolean {
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

        return true
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

        private const val SETTINGS_EXPORT = "export_bookmark"
        private const val SETTINGS_IMPORT = "import_bookmark"
        private const val SETTINGS_DELETE_BOOKMARKS = "delete_bookmarks"
        private const val SETTINGS_SETTINGS_EXPORT = "export_settings"
        private const val SETTINGS_SETTINGS_IMPORT = "import_settings"
        private const val KSessionMimeType = "application/octet-stream"    
	const val EXPORT_SETTINGS = 0
	const val IMPORT_SETTINGS = 1
    }
}
