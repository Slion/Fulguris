package acr.browser.lightning.browser

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.Entitlement
import acr.browser.lightning.R
import acr.browser.lightning.constant.FILE
import acr.browser.lightning.constant.INTENT_ORIGIN
import acr.browser.lightning.constant.SCHEME_BOOKMARKS
import acr.browser.lightning.constant.SCHEME_HOMEPAGE
import acr.browser.lightning.di.MainScheduler
import acr.browser.lightning.html.bookmark.BookmarkPageFactory
import acr.browser.lightning.html.homepage.HomePageFactory
import acr.browser.lightning.log.Logger
import acr.browser.lightning.preference.UserPreferences
import acr.browser.lightning.ssl.SslState
import acr.browser.lightning.utils.isSpecialUrl
import acr.browser.lightning.view.*
import acr.browser.lightning.view.find.FindResults
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.webkit.URLUtil
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy

/**
 * Presenter in charge of keeping track of the current tab and setting the current tab of the
 * browser.
 */
class BrowserPresenter(
        private val view: BrowserView,
        private val isIncognito: Boolean,
        private val userPreferences: UserPreferences,
        private val tabsModel: TabsManager,
        @MainScheduler private val mainScheduler: Scheduler,
        private val homePageFactory: HomePageFactory,
        private val bookmarkPageFactory: BookmarkPageFactory,
        public val closedTabs: RecentTabsModel,
        private val logger: Logger
) {

    private var currentTab: LightningView? = null
    private var shouldClose: Boolean = false
    private var sslStateSubscription: Disposable? = null

    init {
        tabsModel.addTabNumberChangedListener(view::updateTabNumber)
    }

    /**
     * Initializes the tab manager with the new intent that is handed in by the BrowserActivity.
     *
     * @param intent the intent to handle, may be null.
     */
    fun setupTabs(intent: Intent?) {
        tabsModel.initializeTabs(view as Activity, intent, isIncognito)
            .subscribeBy(
                onSuccess = {
                    // At this point we always have at least a tab in the tab manager
                    view.notifyTabViewInitialized()
                    view.updateTabNumber(tabsModel.size())
                    if (tabsModel.savedRecentTabsIndices.count() == tabsModel.allTabs.count()) {
                        // Switch to saved current tab if any, otherwise the last tab I guess
                        tabChanged(if (tabsModel.savedRecentTabsIndices.isNotEmpty()) tabsModel.savedRecentTabsIndices.last() else tabsModel.positionOf(it))
                    } else {
                        // Number of tabs does not match the number of recent tabs saved
                        // That means we were most certainly launched from another app opening a new tab
                        // Assuming our new tab is the last one we switch to it
                        tabChanged(tabsModel.positionOf(it))
                    }
                }
            )
    }

    /**
     * Notify the presenter that a change occurred to the current tab. Currently doesn't do anything
     * other than tell the view to notify the adapter about the change.
     *
     * @param tab the tab that changed, may be null.
     */
    fun tabChangeOccurred(tab: LightningView?) = tab?.let {
        view.notifyTabViewChanged(tabsModel.indexOfTab(it))
    }

    private fun onTabChanged(newTab: LightningView?) {
        logger.log(TAG, "On tab changed")
        view.updateSslState(newTab?.currentSslState() ?: SslState.None)

        sslStateSubscription?.dispose()
        sslStateSubscription = newTab
            ?.sslStateObservable()
            ?.observeOn(mainScheduler)
            ?.subscribe(view::updateSslState)

        val webView = newTab?.webView

        if (newTab == null) {
            view.removeTabView()
            currentTab?.let {
                it.pauseTimers()
                it.onDestroy()
            }
        } else {
            if (webView == null) {
                view.removeTabView()
                currentTab?.let {
                    it.pauseTimers()
                    it.onDestroy()
                }
            } else {
                currentTab.let {
                    // TODO: Restore this when Google fixes the bug where the WebView is
                    // blank after calling onPause followed by onResume.
                    // currentTab.onPause();
                    it?.isForegroundTab = false
                }

                newTab.resumeTimers()
                newTab.onResume()
                newTab.isForegroundTab = true

                view.updateProgress(newTab.progress)
                view.setBackButtonEnabled(newTab.canGoBack())
                view.setForwardButtonEnabled(newTab.canGoForward())
                view.updateUrl(newTab.url, false)
                view.setTabView(webView)
                val index = tabsModel.indexOfTab(newTab)
                if (index >= 0) {
                    view.notifyTabViewChanged(tabsModel.indexOfTab(newTab))
                }
            }
        }

        currentTab = newTab
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
        SCHEME_HOMEPAGE -> "$FILE${homePageFactory.createHomePage()}"
        SCHEME_BOOKMARKS -> "$FILE${bookmarkPageFactory.createBookmarkPage(null)}"
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
        /*
        // SL: That special case is apparently not needed
        // It lead to an empty tab list after removing the last tab if it was the home page
        if (tabsModel.size() == 1
            && currentTab != null
            && URLUtil.isFileUrl(currentTab.url)
            && currentTab.url == mapHomepageToCurrentUrl()) {
            view.closeActivity()
            return
        } else {
        */
            if (isShown) {
                view.removeTabView()
            }
            val currentDeleted = tabsModel.deleteTab(position)
            if (currentDeleted) {
                tabChanged(tabsModel.indexOfCurrentTab())
            }
        //}

        val afterTab = tabsModel.currentTab
        view.notifyTabViewRemoved(position)

        if (afterTab == null) {
            view.closeBrowser()
            return
        } else if (afterTab !== currentTab) {
            view.notifyTabViewChanged(tabsModel.indexOfCurrentTab())
        }

        if (shouldClose && !isIncognito) {
            this.shouldClose = false
            view.closeActivity()
        }

        view.updateTabNumber(tabsModel.size())

        logger.log(TAG, "...deleted tab")
    }

    /**
     * Handle a new intent from the the main BrowserActivity.
     *
     * @param intent the intent to handle, may be null.
     */
    fun onNewIntent(intent: Intent?) = tabsModel.doAfterInitialization {
        val url = if (intent?.action == Intent.ACTION_WEB_SEARCH) {
            tabsModel.extractSearchFromIntent(intent)
        } else {
            intent?.dataString
        }

        val tabHashCode = intent?.extras?.getInt(INTENT_ORIGIN, 0) ?: 0

        if (tabHashCode != 0 && url != null) {
            tabsModel.getTabForHashCode(tabHashCode)?.loadUrl(url)
        } else if (url != null) {
            if (URLUtil.isFileUrl(url)) {
                view.showBlockedLocalFileDialog {
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
                    newTab(FreezableBundleInitializer(it.webView?: Bundle() , it.title, it.favicon), show)
                }
            }
            view.showSnackbar(R.string.reopening_recent_tab)
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
     * Notifies the presenter that it should shut down. This should be called when the
     * BrowserActivity is destroyed so that we don't leak any memory.
     */
    fun shutdown() {
        onTabChanged(null)
        tabsModel.cancelPendingWork()
        sslStateSubscription?.dispose()
    }

    /**
     * Notifies the presenter that we wish to switch to a different tab at the specified position.
     * If the position is not in the model, this method will do nothing.
     *
     * @param position the position of the tab to switch to.
     */
    fun tabChanged(position: Int) {
        if (position < 0 || position >= tabsModel.size()) {
            logger.log(TAG, "tabChanged invalid position: $position")
            return
        }

        logger.log(TAG, "tabChanged: $position")
        onTabChanged(tabsModel.switchToTab(position))
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
            view.showSnackbar(R.string.max_tabs)
            return false
        }

        logger.log(TAG, "New tab, show: $show")

        val startingTab = tabsModel.newTab(view as Activity, tabInitializer, isIncognito, userPreferences.newTabPosition)
        if (tabsModel.size() == 1) {
            startingTab.resumeTimers()
        }

        view.notifyTabViewAdded()
        view.updateTabNumber(tabsModel.size())

        if (show) {
            onTabChanged(tabsModel.switchToTab(tabsModel.indexOfTab(startingTab)))
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

    fun findInPage(query: String): FindResults? {
        return tabsModel.currentTab?.find(query)
    }

    companion object {
        private const val TAG = "BrowserPresenter"
    }

}
