/*
 * Copyright © 2020-2021 Stéphane Lenclud
 */

package acr.browser.lightning.browser

import acr.browser.lightning.BrowserApp
import acr.browser.lightning.Entitlement
import acr.browser.lightning.R
import acr.browser.lightning.constant.FILE
import acr.browser.lightning.constant.INTENT_ORIGIN
import acr.browser.lightning.constant.Uris
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.html.bookmark.BookmarkPageFactory
import acr.browser.lightning.html.homepage.HomePageFactory
import acr.browser.lightning.html.incognito.IncognitoPageFactory
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.ssl.SslState
import acr.browser.lightning.utils.isSpecialUrl
import acr.browser.lightning.view.*
import android.app.Activity
import android.content.Intent
import android.webkit.URLUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presenter in charge of keeping track of the current tab and setting the current tab of the
 * browser.
 * SL: Should we merge this class with TabsManager?
 */
//@HiltViewModel
@Singleton
class BrowserPresenter @Inject constructor(
    private val userPreferences: UserPreferences,
    private val homePageFactory: HomePageFactory,
    private val incognitoPageFactory: IncognitoPageFactory,
    private val bookmarkPageFactory: BookmarkPageFactory,
    private val logger: Logger
): fulguris.Component() {

    private var currentTab: LightningView? = null
    private var shouldClose: Boolean = false

    lateinit var iBrowserView: BrowserView
    var isIncognito: Boolean = false
    lateinit var closedTabs: RecentTabsModel
    lateinit var tabsModel: TabsManager

    /**
     * Switch to the session with the given name
     */
    fun switchToSession(aSessionName: String) {
        // Don't do anything if given session name is already the current one or if such session does not exists
        if (!tabsModel.isInitialized
                || tabsModel.iCurrentSessionName==aSessionName
                || tabsModel.iSessions?.filter { s -> s.name == aSessionName }.isNullOrEmpty()) {
            return
        }

        tabsModel.isInitialized = false

        // Save current states
        tabsModel.saveState()
        // Change current session
        tabsModel.iCurrentSessionName = aSessionName
        // Save it again to preserve new current session name
        tabsModel.saveSessions()
        // Then reload our tabs
        setupTabs()

        // TODO: Using toast should really be avoided as they pileup
        // TODO: Doing this here is also wrong as we do not know yet if our session was loaded correctly
        // TODO: Give some user feedback yes but please do it properly
        //BrowserApp.instance.applicationContext.apply {
        //    toast(getString(R.string.session_switched,aSessionName))
        //}
    }


    /**
     * Initializes our tab manager.
     */
    fun setupTabs(aIntent: Intent? = null) {
        iScopeMainThread.launch {
            delay(1L)
            val tabs = tabsModel.initializeTabs(iBrowserView as Activity, isIncognito)
            // At this point we always have at least a tab in the tab manager
            iBrowserView.notifyTabViewInitialized()
            iBrowserView.updateTabNumber(tabsModel.size())
            // Switch to persisted current tab
            tabChanged(if (tabsModel.savedRecentTabsIndices.isNotEmpty()) tabsModel.savedRecentTabsIndices.last() else tabsModel.positionOf(tabs.last()),false, false)
            // Only then can we open tab from external app on startup otherwise it is opened in the background somehow
            aIntent?.let {onNewIntent(aIntent)}

            //logger.log(TAG,"After from coroutine")
        }

        //logger.log(TAG,"After from main")
    }

    /**
     * Notify the presenter that a change occurred to the current tab. Currently doesn't do anything
     * other than tell the view to notify the adapter about the change.
     *
     * @param tab the tab that changed, may be null.
     */
    fun tabChangeOccurred(tab: LightningView?) = tab?.let {
        iBrowserView.notifyTabViewChanged(tabsModel.indexOfTab(it))
    }

    /**
     * Called when the foreground is changing.
     *
     * [aTab] The tab we are switching to.
     * [aWasTabAdded] True if [aTab] was just created.
     * [aGoingBack] Tells in which direction we are going, this can help determine what kind of tab animation will be used.
     */
    private fun onTabChanged(aTab: LightningView, aWasTabAdded: Boolean, aPreviousTabClosed: Boolean, aGoingBack: Boolean) {
        logger.log(TAG, "On tab changed")

        currentTab?.let {
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

        iBrowserView.updateProgress(aTab.progress)
        iBrowserView.setBackButtonEnabled(aTab.canGoBack())
        iBrowserView.setForwardButtonEnabled(aTab.canGoForward())
        iBrowserView.updateUrl(aTab.url, false)
        iBrowserView.setTabView(aTab.webView!!,aWasTabAdded,aPreviousTabClosed, aGoingBack)
        val index = tabsModel.indexOfTab(aTab)
        if (index >= 0) {
            iBrowserView.notifyTabViewChanged(tabsModel.indexOfTab(aTab))
        }

        // Must come late as it needs a webview
        iBrowserView.updateSslState(aTab.currentSslState() ?: SslState.None)

        currentTab = aTab
    }

    /**
     * Closes all tabs but the current tab.
     */
    fun closeAllOtherTabs() {

        while (tabsModel.last() != tabsModel.indexOfCurrentTab()) {
            deleteTab(tabsModel.last())
        }

        while (0 != tabsModel.indexOfCurrentTab()) {
            deleteTab(0)
        }
    }

    /**
     * SL: That's not quite working for some reason.
     * Close all tabs
     */
    fun closeAllTabs() {
        // That should never be the case though
        if (tabsModel.allTabs.count()==0) return

        while (tabsModel.allTabs.count() > 1) {
            deleteTab(tabsModel.last())
        }

        //deleteTab(tabsModel.last())
    }

    private fun mapHomepageToCurrentUrl(): String = when (val homepage = userPreferences.homepage) {
        Uris.AboutHome -> "$FILE${homePageFactory.createHomePage()}"
        Uris.AboutBookmarks -> "$FILE${bookmarkPageFactory.createBookmarkPage(null)}"
        else -> homepage
    }

    private fun mapIncognitoToCurrentUrl(): String = when (val homepage = userPreferences.incognitoPage) {
        Uris.AboutIncognito -> "$FILE${incognitoPageFactory.createIncognitoPage()}"
        Uris.AboutBookmarks -> "$FILE${bookmarkPageFactory.createBookmarkPage(null)}"
        else -> homepage
    }

    /**
     * Deletes the tab at the specified position.
     *
     * @param position the position at which to delete the tab.
     */
    fun deleteTab(position: Int) {
        logger.log(TAG, "deleting tab...")
        val tabToDelete = tabsModel.getTabAtPosition(position) ?: return

        closedTabs.add(tabToDelete.saveState())

        val isShown = tabToDelete.isShown
        val shouldClose = shouldClose && isShown && tabToDelete.isNewTab
        val currentTab = tabsModel.currentTab

        val currentDeleted = tabsModel.deleteTab(position)
        if (currentDeleted) {
            tabChanged(tabsModel.indexOfCurrentTab(), isShown, false)
        }

        val afterTab = tabsModel.currentTab
        iBrowserView.notifyTabViewRemoved(position)

        if (afterTab == null) {
            iBrowserView.closeBrowser()
            return
        } else if (afterTab !== currentTab) {
            iBrowserView.notifyTabViewChanged(tabsModel.indexOfCurrentTab())
        }

        if (shouldClose && !isIncognito) {
            this.shouldClose = false
            iBrowserView.closeActivity()
        }

        iBrowserView.updateTabNumber(tabsModel.size())

        logger.log(TAG, "...deleted tab")
    }

    /**
     * Handle a new intent from the the main BrowserActivity.
     * TODO: That implementation is so ugly… try and improve that.
     * @param intent the intent to handle, may be null.
     */
    fun onNewIntent(intent: Intent?) = tabsModel.doOnceAfterInitialization {
        val url = if (intent?.action == Intent.ACTION_WEB_SEARCH) {
            tabsModel.extractSearchFromIntent(intent)
        }
        else if (intent?.action == Intent.ACTION_SEND) {
            // User shared text with our app
                if ("text/plain" == intent.type) {
                    // Get shared text
                    val clue = intent.getStringExtra(Intent.EXTRA_TEXT)
                    // Put it in the address bar if any
                    clue?.let { iBrowserView.setAddressBarText(it) }
                }
            // Cancel other operation as we won't open a tab here
            null
        } else {
            intent?.dataString
        }

        val tabHashCode = intent?.extras?.getInt(INTENT_ORIGIN, 0) ?: 0

        if (tabHashCode != 0 && url != null) {
            tabsModel.getTabForHashCode(tabHashCode)?.loadUrl(url)
        } else if (url != null) {
            if (URLUtil.isFileUrl(url)) {
                iBrowserView.showBlockedLocalFileDialog {
                    newTab(UrlInitializer(url), true)
                    shouldClose = true
                    tabsModel.lastTab()?.isNewTab = true
                }
            } else {
                newTab(UrlInitializer(url), true)
                shouldClose = true
                tabsModel.lastTab()?.isNewTab = true
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
                    newTab(tabsModel.tabInitializerForSpecialUrl(it.url), show)
                } else {
                    // That's an actual WebView bundle
                    newTab(FreezableBundleInitializer(it), show)
                }
            }
            iBrowserView.showSnackbar(R.string.reopening_recent_tab)
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
        tabsModel.currentTab?.loadUrl(url)
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
        if (position < 0 || position >= tabsModel.size()) {
            logger.log(TAG, "tabChanged invalid position: $position")
            return
        }

        logger.log(TAG, "tabChanged: $position")
        onTabChanged(tabsModel.switchToTab(position),false, aPreviousTabClosed, aGoingBack)
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
        if (tabsModel.size() >= Entitlement.maxTabCount(userPreferences.sponsorship)) {
            iBrowserView.onMaxTabReached()
            // Still allow spawning more tabs for the time being.
            // That means not having a valid subscription will only spawn that annoying message above.
            //return false
        }

        logger.log(TAG, "New tab, show: $show")

        val startingTab = tabsModel.newTab(iBrowserView as Activity, tabInitializer, isIncognito, userPreferences.newTabPosition)
        if (tabsModel.size() == 1) {
            startingTab.resumeTimers()
        }

        iBrowserView.notifyTabViewAdded()
        iBrowserView.updateTabNumber(tabsModel.size())

        if (show) {
            onTabChanged(tabsModel.switchToTab(tabsModel.indexOfTab(startingTab)),true, false, false)
        }
        else {
            // We still need to add it to our recent tabs
            // Adding at the beginning of a Set is doggy though
            val recentTabs = tabsModel.iRecentTabs.toSet()
            tabsModel.iRecentTabs.clear()
            tabsModel.iRecentTabs.add(startingTab)
            tabsModel.iRecentTabs.addAll(recentTabs)
        }

        return true
    }

    fun onAutoCompleteItemPressed() {
        tabsModel.currentTab?.requestFocus()
    }

    companion object {
        private const val TAG = "BrowserPresenter"
    }

}
