package fulguris.browser

import fulguris.Entitlement
import fulguris.R
import acr.browser.lightning.browser.sessions.Session
import fulguris.constant.INTENT_ORIGIN
import fulguris.extensions.snackbar
import fulguris.search.SearchEngineProvider
import fulguris.settings.NewTabPosition
import fulguris.settings.preferences.UserPreferences
import fulguris.ssl.SslState
import fulguris.utils.*
import fulguris.view.*
import android.app.Activity
import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.webkit.URLUtil
import androidx.lifecycle.LifecycleOwner
import fulguris.Component
import fulguris.utils.QUERY_PLACE_HOLDER
import fulguris.utils.isBookmarkUrl
import fulguris.utils.isDownloadsUrl
import fulguris.utils.isHistoryUrl
import fulguris.utils.isIncognitoPageUrl
import fulguris.utils.isSpecialUrl
import fulguris.utils.isStartPageUrl
import fulguris.utils.smartUrlFilter
import fulguris.view.BookmarkPageInitializer
import fulguris.view.DownloadPageInitializer
import fulguris.view.FreezableBundleInitializer
import fulguris.view.HistoryPageInitializer
import fulguris.view.HomePageInitializer
import fulguris.view.IncognitoPageInitializer
import fulguris.view.NoOpInitializer
import fulguris.view.TabInitializer
import fulguris.view.UrlInitializer
import fulguris.view.WebPageTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

/**
 * A manager singleton that holds all the [WebPageTab] and tracks the current tab. It handles
 * creation, deletion, restoration, state saving, and switching of tabs and sessions.
 */
//@HiltViewModel
@Singleton
class TabsManager @Inject constructor(
    private val application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val homePageInitializer: HomePageInitializer,
    private val incognitoPageInitializer: IncognitoPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
    private val historyPageInitializer: HistoryPageInitializer,
    private val downloadPageInitializer: DownloadPageInitializer,
    private val noOpPageInitializer: NoOpInitializer,
    private val userPreferences: UserPreferences
): Component() {

    private val tabList = arrayListOf<WebPageTab>()
    var iRecentTabs = mutableSetOf<WebPageTab>()
    // This is just used when loading and saving sessions.
    // TODO: Ideally it should not be a data member.
    val savedRecentTabsIndices = mutableSetOf<Int>()
    private var iIsIncognito = false;

    // Our persisted list of sessions
    // TODO: Consider using a map instead of an array
    var iSessions: ArrayList<Session> = arrayListOf<Session>()
    var iCurrentSessionName: String = ""
        set(value) {
            // Most unoptimized way to maintain our current item but that should do for now
            iSessions.forEach { s -> s.isCurrent = false }
            iSessions.filter { s -> s.name == value}.apply {if (isNotEmpty()) get(0).isCurrent = true }
            field = value
        }

    /**
     * Return the current [WebPageTab] or null if no current tab has been set.
     *
     * @return a [WebPageTab] or null if there is no current tab.
     */
    var currentTab: WebPageTab? = null
        private set

    private var tabNumberListeners = emptySet<(Int) -> Unit>()

    var isInitialized = false
    private var postInitializationWorkList = mutableListOf<InitializationListener>()

    init {

        addTabNumberChangedListener {
            // Update current session tab count
            //TODO: Have a getCurrentSession function
            //TODO: during shutdown initiated by session switch we get stray events here not matching the proper session since it current session name was changed
            //TODO: it's no big deal and does no harm at all but still not consistent, we may want to fix it at some point
            //TODO: after shutdown our tab counts are fixed by [loadSessions]
            val session=iSessions.filter { s -> s.name == iCurrentSessionName }
            if (session.isNotEmpty()) {
                session[0].tabCount = it
            }
        }
    }

    /*
    override fun onCleared() {
        super.onCleared()
        shutdown()
        app.tabsManager = null;
    }
    */

    /**
     * From [DefaultLifecycleObserver.onStop]
     *
     * This is called once our activity is not visible anymore.
     * That's where we should save our data according to the docs.
     * https://developer.android.com/guide/components/activities/activity-lifecycle#onstop
     * Saving data can't wait for onDestroy as there is no guarantee onDestroy will ever be called.
     * In fact even when user closes our Task from recent Task list our activity is just terminated without getting any notifications.
     */
    override fun onStop(owner: LifecycleOwner) {
        // Once we go background make sure the current tab is not new anymore
        currentTab?.isNewTab = false
        saveIfNeeded()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        //shutdown()
    }


    /**
     */
    fun currentSessionIndex() : Int {
        return iSessions.indexOfFirst { s -> s.name == iCurrentSessionName }
    }

    /**
     */
    fun currentSession() : Session {
        return session(iCurrentSessionName)
    }

    /**
     * Provide the session matching the given name
     * TODO: have a better implementation
     */
    fun session(aName: String) : Session {
        if (iSessions.isNullOrEmpty()) {
            // TODO: Return session with Default name
            return Session()
        }

        val list = iSessions.filter { s -> s.name == aName }
        if (list.isNullOrEmpty()) {
            // TODO: Return session with Default name
            return Session()
        }

        // Should only be one session item in that list
        return list[0]
    }


    /**
     * Adds a listener to be notified when the number of tabs changes.
     */
    fun addTabNumberChangedListener(listener: ((Int) -> Unit)) {
        tabNumberListeners += listener
    }

    /**
     * Cancels any pending work that was scheduled to run after initialization.
     */
    fun cancelPendingWork() {
        postInitializationWorkList.clear()
    }


    /**
     * Executes the [runnable] once after the next time this manager has been initialized.
     */
    fun doOnceAfterInitialization(runnable: () -> Unit) {
        if (isInitialized) {
            runnable()
        } else {
            postInitializationWorkList.add(object :
                InitializationListener {
                override fun onInitializationComplete() {
                    runnable()
                    postInitializationWorkList.remove(this)
                }
            })
        }
    }

    /**
     * Executes the [runnable] every time after this manager has been initialized.
     */
    fun doAfterInitialization(runnable: () -> Unit) {
        if (isInitialized) {
            runnable()
        } else {
            postInitializationWorkList.add(object :
                InitializationListener {
                override fun onInitializationComplete() {
                    runnable()
                }
            })
        }
    }

    /**
     *
     */
    private fun finishInitialization() {

        try {
            if (allTabs.size == savedRecentTabsIndices.size) { // Defensive
                // Populate our recent tab list from our persisted indices
                iRecentTabs.clear()
                // Looks like we can somehow persist -1 as a tab index
                // TODO: That should never be the case. We ought to find out what's causing this.
                // See: https://console.firebase.google.com/u/0/project/fulguris-b1f69/crashlytics/app/android:net.slions.fulguris.full.playstore/issues/d70a65025a98104878bf2da4aa06287e?time=last-seven-days&sessionEventKey=650AE750014800016260FF77850BA317_1859332239793370743
                savedRecentTabsIndices.forEach { iRecentTabs.add(allTabs.elementAt(it))}
            } else {
                // Defensive, if we have missing tabs in our recent tab list just reset it
                resetRecentTabsList()
            }
        }
        catch (ex: Exception) {
            Timber.d("Failed to load recent tab list")
            resetRecentTabsList()
        }

        isInitialized = true

        // Iterate through our collection while allowing item to be removed and avoid ConcurrentModificationException
        // To do that we need to make a copy of our list
        val listCopy = postInitializationWorkList.toList()
        for (listener in listCopy) {
            listener.onInitializationComplete()
        }
    }

    /**
     *
     */
    private fun resetRecentTabsList()
    {
        Timber.d("resetRecentTabsList")
        // Reset recent tabs list to arbitrary order
        iRecentTabs.clear()
        iRecentTabs.addAll(allTabs)

        // Put back current tab on top
        currentTab?.let {
            iRecentTabs.apply {
                remove(it)
                add(it)
            }
        }
    }

    /**
     * Initialize the state of the [TabsManager] based on previous state of the browser.
     *
     * TODO: See how you can offload IO to a background thread
     */
    fun initializeTabs(activity: Activity, incognito: Boolean) : MutableList<WebPageTab> {
        Timber.d("initializeTabs")
        iIsIncognito = incognito

        shutdown()

        val list = mutableListOf<WebPageTab>()

        if (incognito) {
            list.add(newTab(activity, incognitoPageInitializer, incognito, NewTabPosition.END_OF_TAB_LIST))
        } else {
            tryRestorePreviousTabs(activity).forEach {
                try {
                    list.add(newTab(activity, it, incognito, NewTabPosition.END_OF_TAB_LIST))
                } catch (ex: Throwable) {
                    // That's a corrupted session file, can happen when importing garbage.
                    activity.snackbar(R.string.error_session_file_corrupted)
                }
            }

            // Make sure we have one tab
            if (list.isEmpty()) {
                list.add(newTab(activity, homePageInitializer, incognito, NewTabPosition.END_OF_TAB_LIST))
            }
        }

        finishInitialization()

        return list
    }

    /**
     * Returns the URL for a search [Intent]. If the query is empty, then a null URL will be
     * returned.
     */
    fun extractSearchFromIntent(intent: Intent): String? {
        val query = intent.getStringExtra(SearchManager.QUERY)
        val searchUrl = "${searchEngineProvider.provideSearchEngine().queryUrl}$QUERY_PLACE_HOLDER"

        return if (query?.isNotBlank() == true) {
            smartUrlFilter(query, true, searchUrl).first
        } else {
            null
        }
    }


    /**
     * Load tabs from the given file
     */
    private fun loadSession(aFilename: String): MutableList<TabInitializer>
    {
        val bundle = fulguris.utils.FileUtils.readBundleFromStorage(application, aFilename)

        // Defensive. should have happened in the shutdown already
        savedRecentTabsIndices.clear()
        // Read saved current tab index if any
        bundle?.let{
            it.getIntArray(RECENT_TAB_INDICES)?.toList()?.let { it1 -> savedRecentTabsIndices.addAll(it1) }
        }

        val list = mutableListOf<TabInitializer>()
        readSavedStateFromDisk(bundle).forEach {
            list.add(if (it.url.isSpecialUrl()) {
                tabInitializerForSpecialUrl(it.url)
            } else {
                FreezableBundleInitializer(it)
            })
        }

        // Make sure we have at least one tab
        if (list.isEmpty()) {
            list.add(homePageInitializer)
        }
        return list
    }

    /**
     * Create a recovery session
     */
    private fun loadRecoverySession(): MutableList<TabInitializer>
    {
        // Defensive. should have happened in the shutdown already
        savedRecentTabsIndices.clear()
        val list = mutableListOf<TabInitializer>()

        // Make sure we have at least one tab
        if (list.isEmpty()) {
            list.add(noOpPageInitializer)
        }
        return list
    }


    /**
     * Rename the session [aOldName] to [aNewName].
     * Takes care of checking parameters validity before proceeding.
     * Changes current session name if needed.
     * Rename matching session data file too.
     * Commit session list changes to persistent storage.
     *
     * @param [aOldName] Name of the session to rename in our session list.
     * @param [aNewName] New name to be assumed by specified session.
     */
    fun renameSession(aOldName: String, aNewName: String) {

        Timber.d("Try rename session $aOldName to $aNewName")

        val index = iSessions.indexOf(session(aOldName))
        Timber.d("Session index $index")

        // Check if we can indeed rename that session
        if (iSessions.isEmpty() // Check if we have sessions at all
                or !isValidSessionName(aNewName) // Check if new session name is valid
                or !(index>=0 && index<iSessions.count())) { // Check if index is in range
            Timber.d("Session rename aborted")
            return
        }

        // Proceed with rename then
        val oldName = iSessions[index].name
        // Change session name
        iSessions[index].name = aNewName
        // Renamed session is the current session
        if (iCurrentSessionName == oldName) {
            iCurrentSessionName = aNewName
        }

        Timber.d("Rename session files $oldName to $aNewName")
        // Rename our session file
        fulguris.utils.FileUtils.renameBundleInStorage(application, fileNameFromSessionName(oldName), fileNameFromSessionName(aNewName))

        // I guess it makes sense to persist our changes
        saveSessions()
    }

    /**
     * Check if the given string is a valid session name
     */
    fun isValidSessionName(aName: String): Boolean {
        // Empty strings are not valid names
        if (aName.isNullOrBlank()) {
            return false
        }

        if (iSessions.isNullOrEmpty()) {
            // Null or empty session list so that name is valid
            return true
        } else {
            // That name is valid if not already in use
            return iSessions.filter { s -> s.name == aName }.isNullOrEmpty()
        }
    }


    /**
     * Returns an observable that emits the [TabInitializer] for each previously opened tab as
     * saved on disk. Can potentially be empty.
     */
    private fun restorePreviousTabs(): MutableList<TabInitializer>
    {
        //throw Exception("Hi There!")
        // First load our sessions
        loadSessions()
        // Check if we have a current session
        if (iCurrentSessionName.isBlank()) {
            // No current session name meaning first load with version support
            // Add our default session
            iCurrentSessionName = application.getString(R.string.session_default)
            // At this stage we must have at least an empty list
            iSessions.add(Session(iCurrentSessionName))
            // Than load legacy session file to make sure tabs from earlier version are preserved
            return loadSession(FILENAME_SESSION_DEFAULT)
            // TODO: delete legacy session file at some point
        } else {
            // Load current session then
            return loadSession(fileNameFromSessionName(iCurrentSessionName))
        }
    }

    /**
     * Safely restore previous tabs
     */
    private fun tryRestorePreviousTabs(activity: Activity): MutableList<TabInitializer>
    {
        return try {
            restorePreviousTabs()
        } catch (ex: Throwable) {
            // TODO: report this using firebase or local crash logs
            Timber.e(ex,"restorePreviousTabs failed")
            activity.snackbar(R.string.error_recovery_session)
            createRecoverySession()
        }
    }


    /**
     * Called whenever we fail to load a session properly.
     * The idea is that it should enable the app to start even when it's pointing to a corrupted session.
     */
    private fun createRecoverySession(): MutableList<TabInitializer>
    {
        recoverSessions()
        // Add our recovery session using timestamp
        iCurrentSessionName = application.getString(R.string.session_recovery) + "-" + Date().time
        iSessions.add(Session(iCurrentSessionName,1, true))

        return loadRecoverySession()
    }




    /**
     * Provide a tab initializer for the given special URL
     */
    fun tabInitializerForSpecialUrl(url: String): TabInitializer {
        return when {
            url.isBookmarkUrl() -> bookmarkPageInitializer
            url.isDownloadsUrl() -> downloadPageInitializer
            url.isStartPageUrl() -> homePageInitializer
            url.isIncognitoPageUrl() -> incognitoPageInitializer
            url.isHistoryUrl() -> historyPageInitializer
            else -> homePageInitializer
        }
    }

    /**
     * Method used to resume all the tabs in the browser. This is necessary because we cannot pause
     * the WebView when the application is open currently due to a bug in the WebView, where calling
     * onResume doesn't consistently resume it.
     */
    fun resumeAll() {
        currentTab?.resumeTimers()
        for (tab in tabList) {
            tab.onResume()
            tab.initializePreferences()
        }
    }

    /**
     * Method used to pause all the tabs in the browser. This is necessary because we cannot pause
     * the WebView when the application is open currently due to a bug in the WebView, where calling
     * onResume doesn't consistently resume it.
     */
    fun pauseAll() {
        currentTab?.pauseTimers()
        tabList.forEach(WebPageTab::onPause)
    }

    /**
     * Return the tab at the given position in tabs list, or null if position is not in tabs list
     * range.
     *
     * @param position the index in tabs list
     * @return the corespondent [WebPageTab], or null if the index is invalid
     */
    fun getTabAtPosition(position: Int): WebPageTab? =
        if (position < 0 || position >= tabList.size) {
            null
        } else {
            tabList[position]
        }

    val allTabs: List<WebPageTab>
        get() = tabList

    /**
     * Shutdown the manager. This destroys all tabs and clears the references to those tabs.
     * Current tab is also released for garbage collection.
     */
    fun shutdown() {
        Timber.d("shutdown")
        repeat(tabList.size) { doDeleteTab(0) }
        savedRecentTabsIndices.clear()
        isInitialized = false
        currentTab = null
    }

    /**
     * The current number of tabs in the manager.
     *
     * @return the number of tabs in the list.
     */
    fun size(): Int = tabList.size

    /**
     * The index of the last tab in the manager.
     *
     * @return the last tab in the list or -1 if there are no tabs.
     */
    fun last(): Int = tabList.size - 1


    /**
     * The last tab in the tab manager.
     *
     * @return the last tab, or null if there are no tabs.
     */
    fun lastTab(): WebPageTab? = tabList.lastOrNull()

    /**
     * Create and return a new tab. The tab is automatically added to the tabs list.
     *
     * @param activity the activity needed to create the tab.
     * @param tabInitializer the initializer to run on the tab after it's been created.
     * @param isIncognito whether the tab is an incognito tab or not.
     * @return a valid initialized tab.
     */
    fun newTab(
        activity: Activity,
        tabInitializer: TabInitializer,
        isIncognito: Boolean,
        newTabPosition: NewTabPosition
    ): WebPageTab {
        Timber.i("New tab")
        val tab = WebPageTab(
                activity,
                tabInitializer,
                isIncognito,
                homePageInitializer,
                incognitoPageInitializer,
                bookmarkPageInitializer,
                downloadPageInitializer,
                historyPageInitializer
        )

        // Add our new tab at the specified position
        when(newTabPosition){
            NewTabPosition.BEFORE_CURRENT_TAB -> tabList.add(indexOfCurrentTab(), tab)
            NewTabPosition.AFTER_CURRENT_TAB -> tabList.add(indexOfCurrentTab() + 1, tab)
            NewTabPosition.START_OF_TAB_LIST -> tabList.add(0, tab)
            NewTabPosition.END_OF_TAB_LIST -> tabList.add(tab)
        }

        tabNumberListeners.forEach { it(size()) }
        return tab
    }

    /**
     * Removes a tab from the list and destroys the tab. If the tab removed is the current tab, the
     * reference to the current tab will be nullified.
     *
     * @param position The position of the tab to remove.
     */
    private fun removeTab(position: Int) {
        if (position >= tabList.size) {
            return
        }

        val tab = tabList.removeAt(position)
        iRecentTabs.remove(tab)
        if (currentTab == tab) {
            currentTab = null
        }
        tab.destroy()
    }

    /**
     * Deletes a tab from the manager. If the tab being deleted is the current tab, this method will
     * switch the current tab to a new valid tab.
     *
     * @param position the position of the tab to delete.
     * @return returns true if the current tab was deleted, false otherwise.
     */
    private fun doDeleteTab(position: Int): Boolean {
        Timber.i("doDeleteTab: $position")
        val currentTab = currentTab
        val current = positionOf(currentTab)

        if (current == position) {
            when {
                size() == 1 -> this.currentTab = null
                // Switch to previous tab
                else -> switchToTab(indexOfTab(iRecentTabs.elementAt(iRecentTabs.size - 2)))
            }
        }

        removeTab(position)
        tabNumberListeners.forEach { it(size()) }
        return current == position
    }

    /**
     * Return the position of the given tab.
     *
     * @param tab the tab to look for.
     * @return the position of the tab or -1 if the tab is not in the list.
     */
    fun positionOf(tab: WebPageTab?): Int = tabList.indexOf(tab)


    /**
     * Save our states if needed.
     */
    private fun saveIfNeeded() {
        if (iIsIncognito) {
            // We don't persist anything when browsing incognito
            return
        }

        if (userPreferences.restoreTabsOnStartup) {
            saveState()
        }
        else {
            clearSavedState()
        }
    }

    /**
     * Saves the state of the current WebViews, to a bundle which is then stored in persistent
     * storage and can be unparceled.
     */
    fun saveState() {
        Timber.d("saveState")

        // Fix bug where all tabs would get lost
        // See: https://github.com/Slion/Fulguris/issues/193
        if (!isInitialized) {
            Timber.d("saveState - Don't do that")
            return
        }

        // Save sessions info
        saveSessions()
        // Save our session        
        saveCurrentSession(iCurrentSessionName)
    }

    /**
     * Save current session including WebView tab states and recent tab list in the specified file.
     */
    private fun saveCurrentSession(aName: String) {
        Timber.d("saveCurrentSession - $aName")
        val outState = Bundle(ClassLoader.getSystemClassLoader())
        tabList
            .withIndex()
            .forEach { (index, tab) ->
                    // Index padding with zero to make sure they are restored in the correct order
                    // That gives us proper sorting up to 99999 tabs which should be more than enough :)
                    outState.putBundle(TAB_KEY_PREFIX + String.format("%05d", index), tab.saveState())
                }

        //Now save our recent tabs
        // Create an array of tab indices from our recent tab list to be persisted
        savedRecentTabsIndices.clear()
        iRecentTabs.forEach { savedRecentTabsIndices.add(indexOfTab(it))}
        outState.putIntArray(RECENT_TAB_INDICES, savedRecentTabsIndices.toIntArray())

        // Write our bundle to disk
        iScopeThreadPool.launch {
            // Guessing delay is not needed since we do not use the main thread scope anymore
            //delay(1L)
            val temp = FILENAME_TEMP_PREFIX + aName
            val backup = FILENAME_BACKUP_PREFIX + aName
            val session = FILENAME_SESSION_PREFIX + aName

            // Save to temporary session file
            fulguris.utils.FileUtils.writeBundleToStorage(application, outState, temp)
            // Defensively delete session backup, that should never be needed really
            fulguris.utils.FileUtils.deleteBundleInStorage(application, backup)
            // Rename our session file as backup
            fulguris.utils.FileUtils.renameBundleInStorage(application, session, backup)
            // Rename our temporary session to actual session
            fulguris.utils.FileUtils.renameBundleInStorage(application, temp, session)
            // Delete session backup
            fulguris.utils.FileUtils.deleteBundleInStorage(application, backup)

            // We used that loop to test that our jobs are completed no matter what when the app is closed.
            // However long running tasks could run into race condition I guess if we queue it multiple times.
            // I really don't understand what's going on exactly when we close the app twice and we have two instances of that job running.
            // It looks like the process was not terminated when exiting the app the first time and both jobs are running in different thread on the same process.
            // Though even when waiting for the end of that job before restarting the app Android can reuse that process anyway…
            // Log example:
            // date time PID-TID/package priority/tag: message
            // 2022-01-11 11:32:59.939 23094-23207/net.slions.fulguris.full.download.debug D/TabsManager: Tick: 28
            // 2022-01-11 11:33:00.224 23094-23208/net.slions.fulguris.full.download.debug D/TabsManager: Tick: 20
//            repeat(30) {
//                delay(1000L)
//                logger.log(TAG, "Tick: $it")
//            }
        }
    }

    /**
     * Provide session file name from session name
     */
    private fun fileNameFromSessionName(aSessionName: String) : String {
        return FILENAME_SESSION_PREFIX + aSessionName
    }

    /**
     * Provide session file from session name
     */
    fun fileFromSessionName(aName: String) : File {
        return  File(application.filesDir, fileNameFromSessionName(aName))
    }

    /**
     * Use this method to clear the saved state if you do not wish it to be restored when the
     * browser next starts.
     */
    fun clearSavedState() = fulguris.utils.FileUtils.deleteBundleInStorage(application, FILENAME_SESSION_DEFAULT)

    /**
     *
     */
    fun deleteSession(aSessionName: String) {

        // TODO: handle case where we delete current session
        if (aSessionName == iCurrentSessionName) {
            // Can't do that for now
            return
        }

        val index = iSessions.indexOf(session(aSessionName))
        // Delete session file
        fulguris.utils.FileUtils.deleteBundleInStorage(application, fileNameFromSessionName(iSessions[index].name))
        // Remove session from our list
        iSessions.removeAt(index)
    }


    /**
     * Save our session list and current session name to disk.
     */
    fun saveSessions() {
        Timber.d("saveState")
        val bundle = Bundle(javaClass.classLoader)
        bundle.putString(KEY_CURRENT_SESSION, iCurrentSessionName)
        bundle.putParcelableArrayList(KEY_SESSIONS, iSessions)
        // Write our bundle to disk
        iScopeThreadPool.launch {
            // Guessing delay is not needed since we do not use the main thread scope anymore
            //delay(1L)
            FileUtils.writeBundleToStorage(application, bundle, FILENAME_SESSIONS)
        }
    }

    /**
     * Just the sessions list really
     */
    fun deleteSessions() {
        FileUtils.deleteBundleInStorage(application, FILENAME_SESSIONS)
    }

    /**
     * Load our session list and current session name from disk.
     */
    private fun loadSessions() {
        val bundle = FileUtils.readBundleFromStorage(application, FILENAME_SESSIONS)

        bundle?.apply{
            getParcelableArrayList<Session>(KEY_SESSIONS)?.let { iSessions = it}
            // Sessions must have been loaded when we load that guys
            getString(KEY_CURRENT_SESSION)?.let {iCurrentSessionName = it}
        }

        // Somehow we lost that file again :)
        // That crazy bug we keep chasing after
        // TODO: consider running recovery even when our session list was loaded
        if (iSessions.isEmpty()) {
            recoverSessions()
            // Set the first one as current one
            if (iSessions.isNotEmpty()) {
                iCurrentSessionName = iSessions[0].name
            }
        }
    }

    /**
     * Reset our session collection and repopulate by searching the file system for session files.
     */
    private fun recoverSessions() {
        // TODO: report this in firebase or local logs
        Timber.i("recoverSessions")
        //
        iSessions.clear() // Defensive, should already be empty if we get there
        // Search for session files
        val files = application.filesDir?.let{it.listFiles { d, name -> name.startsWith(FILENAME_SESSION_PREFIX) }}
        // Add recovered sessions to our collection
        files?.forEach { f -> iSessions.add(Session(f.name.substring(FILENAME_SESSION_PREFIX.length), -1)) }
    }

    /**
     *
     */
    private fun readSavedStateFromDisk(aBundle: Bundle?): MutableList<TabModel> {

        val list = mutableListOf<TabModel>()
        aBundle?.keySet()
                ?.filter { it.startsWith(TAB_KEY_PREFIX) }
                ?.mapNotNull { bundleKey ->
                    aBundle.getBundle(bundleKey)?.let { list.add(TabModelFromBundle(it))}
                }

        return list;
    }


    /**
     * Returns the index of the current tab.
     *
     * @return Return the index of the current tab, or -1 if the current tab is null.
     */
    fun indexOfCurrentTab(): Int = tabList.indexOf(currentTab)

    /**
     * Returns the index of the tab.
     *
     * @return Return the index of the tab, or -1 if the tab isn't in the list.
     */
    fun indexOfTab(tab: WebPageTab): Int = tabList.indexOf(tab)

    /**
     * Returns the [WebPageTab] with the provided hash, or null if there is no tab with the hash.
     *
     * @param hashCode the hashcode.
     * @return the tab with an identical hash, or null.
     */
    fun getTabForHashCode(hashCode: Int): WebPageTab? =
        tabList.firstOrNull { webPageTab -> webPageTab.webView?.let { it.hashCode() == hashCode } == true }

    /**
     * Switch from the current tab to the one at the given [aPosition].
     *
     * @param aPosition Index of the tab we want to switch to.
     * @exception IndexOutOfBoundsException if the provided index is out of range.
     * @return The selected tab we just switched to.
     */
    fun switchToTab(aPosition: Int): WebPageTab {
        Timber.i("switch to tab: $aPosition")
        return tabList[aPosition].also {
                currentTab = it
                // Put that tab at the top of our recent tab list
                iRecentTabs.apply{
                    remove(it)
                    add(it)
                    }
            //logger.log(TAG, "Recent indices: $recentTabsIndices")
            }
        }

    /**
     * Was needed instead of simple runnable to be able to implement run once after init function
     */
    interface InitializationListener {
        fun onInitializationComplete()
    }

    ///////////////////
    // From here we have the former browser presenter stuff
    ///////////////////

    private var currentTabFromPresenter: WebPageTab? = null
    private var shouldClose: Boolean = false

    lateinit var iWebBrowser: WebBrowser
    var isIncognito: Boolean = false
    lateinit var closedTabs: RecentTabsModel

    /**
     * Switch to the session with the given name
     */
    fun switchToSession(aSessionName: String) {
        // Don't do anything if given session name is already the current one or if such session does not exists
        if (!isInitialized
            || iCurrentSessionName==aSessionName
            || iSessions.none { s -> s.name == aSessionName }
        ) {
            return
        }

        // Save current states
        saveState()
        //
        isInitialized = false
        // Change current session
        iCurrentSessionName = aSessionName
        // Save it again to preserve new current session name
        saveSessions()
        // Then reload our tabs
        setupTabs()

        // TODO: Using toast should really be avoided as they pileup
        // TODO: Doing this here is also wrong as we do not know yet if our session was loaded correctly
        // TODO: Give some user feedback yes but please do it properly
        //app.apply {
        //    toast(getString(R.string.session_switched,aSessionName))
        //}
    }


    /**
     * Initializes our tab manager.
     */
    fun setupTabs(aIntent: Intent? = null) {
        Timber.d("setupTabs")
        iScopeMainThread.launch {
            delay(1L)
            val tabs = initializeTabs(iWebBrowser as Activity, isIncognito)
            // At this point we always have at least a tab in the tab manager
            iWebBrowser.notifyTabViewInitialized()
            iWebBrowser.updateTabNumber(size())
            // Switch to persisted current tab
            tabChanged(if (savedRecentTabsIndices.isNotEmpty()) savedRecentTabsIndices.last() else positionOf(tabs.last()),false, false)
            // Only then can we open tab from external app on startup otherwise it is opened in the background somehow
            aIntent?.let {onNewIntent(aIntent)}

            //logger.log(TAG,"After from coroutine")
        }

        //logger.log(TAG,"After from main")
    }

    /**
     * Called when the foreground is changing.
     *
     * [aTab] The tab we are switching to.
     * [aWasTabAdded] True if [aTab] was just created.
     * [aGoingBack] Tells in which direction we are going, this can help determine what kind of tab animation will be used.
     */
    private fun onTabChanged(aTab: WebPageTab, aWasTabAdded: Boolean, aPreviousTabClosed: Boolean, aGoingBack: Boolean) {
        Timber.d("onTabChanged")

        currentTabFromPresenter?.let {
            // TODO: Restore this when Google fixes the bug where the WebView is
            // blank after calling onPause followed by onResume.
            // it.onPause();
            it.isForeground = false
        }

        // Must come first so that frozen tabs are unfrozen
        // This will create frozen tab WebView, before that WebView is not available
        aTab.isForeground = true

        aTab.resumeTimers()
        aTab.onResume()

        iWebBrowser.setBackButtonEnabled(aTab.canGoBack())
        iWebBrowser.setForwardButtonEnabled(aTab.canGoForward())
        iWebBrowser.updateUrl(aTab.url, false)
        iWebBrowser.setTabView(aTab.webView!!,aWasTabAdded,aPreviousTabClosed, aGoingBack)
        val index = indexOfTab(aTab)
        if (index >= 0) {
            iWebBrowser.notifyTabViewChanged(indexOfTab(aTab))
        }

        // Must come late as it needs a webview
        iWebBrowser.updateSslState(aTab.currentSslState() ?: SslState.None)

        currentTabFromPresenter = aTab
    }

    /**
     * Closes all tabs but the current tab.
     */
    fun closeAllOtherTabs() {
        Timber.d("closeAllOtherTabs")
        while (last() != indexOfCurrentTab()) {
            deleteTab(last())
        }

        while (0 != indexOfCurrentTab()) {
            deleteTab(0)
        }
    }

    /**
     * SL: That's not quite working for some reason.
     * Close all tabs
     */
    fun closeAllTabs() {
        // That should never be the case though
        if (allTabs.count()==0) return

        while (allTabs.count() > 1) {
            deleteTab(last())
        }

        //deleteTab(last())
    }

    /**
     * Deletes the tab at the specified position.
     *
     * @param position the position at which to delete the tab.
     */
    fun deleteTab(position: Int) {
        Timber.d("deleteTab")
        val tabToDelete = getTabAtPosition(position) ?: return

        closedTabs.add(tabToDelete.saveState())

        val isShown = tabToDelete.isShown
        val shouldClose = shouldClose && isShown && tabToDelete.isNewTab
        val beforeTab = currentTab

        val currentDeleted = doDeleteTab(position)
        if (currentDeleted) {
            tabChanged(indexOfCurrentTab(), isShown, false)
        }

        val afterTab = currentTab
        iWebBrowser.notifyTabViewRemoved(position)

        if (afterTab == null) {
            iWebBrowser.closeBrowser()
            return
        } else if (afterTab !== beforeTab) {
            iWebBrowser.notifyTabViewChanged(indexOfCurrentTab())
        }

        if (shouldClose && !isIncognito) {
            this.shouldClose = false
            iWebBrowser.closeActivity()
        }

        iWebBrowser.updateTabNumber(size())

        Timber.d("deleteTab - end")
    }

    /**
     * Handle a new intent from the the main BrowserActivity.
     * TODO: That implementation is so ugly… try and improve that.
     * @param intent the intent to handle, may be null.
     */
    fun onNewIntent(intent: Intent?) = doOnceAfterInitialization {
        val url = if (intent?.action == Intent.ACTION_WEB_SEARCH) {
            extractSearchFromIntent(intent)
        }
        else if (intent?.action == Intent.ACTION_SEND) {
            // User shared text with our app
            if ("text/plain" == intent.type) {
                // Get shared text
                val clue = intent.getStringExtra(Intent.EXTRA_TEXT)
                // Put it in the address bar if any
                clue?.let { iWebBrowser.setAddressBarText(it) }
            }
            // Cancel other operation as we won't open a tab here
            null
        } else {
            intent?.dataString
        }

        val tabHashCode = intent?.extras?.getInt(INTENT_ORIGIN, 0) ?: 0

        if (tabHashCode != 0 && url != null) {
            getTabForHashCode(tabHashCode)?.loadUrl(url)
        } else if (url != null) {
            if (URLUtil.isFileUrl(url)) {
                iWebBrowser.showBlockedLocalFileDialog {
                    newTab(UrlInitializer(url), true)
                    shouldClose = true
                    lastTab()?.isNewTab = true
                }
            } else {
                newTab(UrlInitializer(url), true)
                shouldClose = true
                lastTab()?.isNewTab = true
            }
        }
    }

    /**
     * Recover last closed tab.
     */
    fun recoverClosedTab(show: Boolean = true) {
        closedTabs.popLast()?.let { bundle ->
            TabModelFromBundle(bundle).let {
                if (it.url.isSpecialUrl()) {
                    // That's a special URL
                    newTab(tabInitializerForSpecialUrl(it.url), show)
                } else {
                    // That's an actual WebView bundle
                    newTab(FreezableBundleInitializer(it), show)
                }
            }
            iWebBrowser.showSnackbar(R.string.reopening_recent_tab)
        }
    }

    /**
     * Recover all closed tabs
     */
    fun recoverAllClosedTabs() {
        while (closedTabs.bundleStack.count()>0) {
            recoverClosedTab(false)
        }
    }

    /**
     * Loads a URL in the current tab.
     *
     * @param url the URL to load, must not be null.
     */
    fun loadUrlInCurrentView(url: String) {
        currentTab?.loadUrl(url)
    }

    /**
     * Notifies the presenter that we wish to switch to a different tab at the specified position.
     * If the position is not in the model, this method will do nothing.
     *
     * [position] the position of the tab to switch to.
     * [aPreviousTabClosed] Tells if the previous tab was closed, this can help determine what kind of tab animation will be used.
     * [aGoingBack] Tells in which direction we are going, this can help determine what kind of tab animation will be used.
     */
    fun tabChanged(position: Int, aPreviousTabClosed: Boolean, aGoingBack: Boolean) {
        if (position < 0 || position >= size()) {
            Timber.d("tabChanged invalid position: $position")
            return
        }

        Timber.d("tabChanged: $position")
        onTabChanged(switchToTab(position),false, aPreviousTabClosed, aGoingBack)
    }




    /**
     * Open a new tab with the specified URL. You can choose to show the tab or load it in the
     * background.
     *
     * @param tabInitializer the tab initializer to run after the tab as been created.
     * @param show whether or not to switch to this tab after opening it.
     * @return true if we successfully created the tab, false if we have hit max tabs.
     */
    fun newTab(tabInitializer: TabInitializer, show: Boolean): Boolean {
        // Limit number of tabs according to sponsorship level
        if (size() >= Entitlement.maxTabCount(userPreferences.sponsorship)) {
            iWebBrowser.onMaxTabReached()
            // Still allow spawning more tabs for the time being.
            // That means not having a valid subscription will only spawn that annoying message above.
            //return false
        }

        Timber.d("New tab, show: $show")

        val startingTab = newTab(iWebBrowser as Activity, tabInitializer, isIncognito, userPreferences.newTabPosition)
        if (size() == 1) {
            startingTab.resumeTimers()
        }

        iWebBrowser.notifyTabViewAdded()
        iWebBrowser.updateTabNumber(size())

        if (show) {
            onTabChanged(switchToTab(indexOfTab(startingTab)),true, false, false)
        }
        else {
            // We still need to add it to our recent tabs
            // Adding at the beginning of a Set is doggy though
            val recentTabs = iRecentTabs.toSet()
            iRecentTabs.clear()
            iRecentTabs.add(startingTab)
            iRecentTabs.addAll(recentTabs)
        }

        return true
    }

    companion object {

        private const val TAB_KEY_PREFIX = "TAB_"
        // Preserve this file name for compatibility
        private const val FILENAME_SESSION_DEFAULT = "SAVED_TABS.parcel"
        private const val KEY_CURRENT_SESSION = "KEY_CURRENT_SESSION"
        private const val KEY_SESSIONS = "KEY_SESSIONS"
        private const val FILENAME_SESSIONS = "SESSIONS"
        const val FILENAME_SESSION_PREFIX = "SESSION_"
        const val FILENAME_TEMP_PREFIX = "TEMP_SESSION_"
        const val FILENAME_BACKUP_PREFIX = "BACKUP_SESSION_"

        private const val RECENT_TAB_INDICES = "RECENT_TAB_INDICES"

    }

}
