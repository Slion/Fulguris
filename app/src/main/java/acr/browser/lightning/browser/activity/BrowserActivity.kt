/*
 * Copyright © 2020 Stéphane Lenclud. All Rights Reserved.
 * Copyright 2015 Anthony Restaino
 */

package acr.browser.lightning.browser.activity

import acr.browser.lightning.*
import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.adblock.AbpUserRules
import acr.browser.lightning.browser.*
import acr.browser.lightning.browser.bookmarks.BookmarksDrawerView
import acr.browser.lightning.browser.cleanup.ExitCleanup
import acr.browser.lightning.browser.sessions.SessionsPopupWindow
import acr.browser.lightning.browser.tabs.TabsDesktopView
import acr.browser.lightning.browser.tabs.TabsDrawerView
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.database.Bookmark
import acr.browser.lightning.database.HistoryEntry
import acr.browser.lightning.database.SearchSuggestion
import acr.browser.lightning.database.WebPage
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.database.history.HistoryRepository
import acr.browser.lightning.databinding.ActivityMainBinding
import acr.browser.lightning.databinding.ToolbarContentBinding
import acr.browser.lightning.di.*
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.dialog.DialogItem
import acr.browser.lightning.dialog.LightningDialogBuilder
import acr.browser.lightning.extensions.*
import acr.browser.lightning.html.bookmark.BookmarkPageFactory
import acr.browser.lightning.html.history.HistoryPageFactory
import acr.browser.lightning.html.homepage.HomePageFactory
import acr.browser.lightning.log.Logger
import acr.browser.lightning.notifications.IncognitoNotification
import acr.browser.lightning.reading.ReadingActivity
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.search.SuggestionsAdapter
import acr.browser.lightning.settings.NewTabPosition
import acr.browser.lightning.settings.activity.SETTINGS_CLASS_NAME
import acr.browser.lightning.settings.activity.SettingsActivity
import acr.browser.lightning.settings.fragment.DisplaySettingsFragment.Companion.MAX_BROWSER_TEXT_SIZE
import acr.browser.lightning.settings.fragment.DisplaySettingsFragment.Companion.MIN_BROWSER_TEXT_SIZE
import acr.browser.lightning.settings.fragment.SponsorshipSettingsFragment
import acr.browser.lightning.ssl.SslState
import acr.browser.lightning.ssl.createSslDrawableForState
import acr.browser.lightning.ssl.showSslDialog
import acr.browser.lightning.utils.*
import acr.browser.lightning.view.*
import acr.browser.lightning.view.SearchView

import android.animation.*
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.view.View.*
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.CustomViewCallback
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.FrameLayout
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.customview.widget.ViewDragHelper
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.anthonycr.grant.PermissionsManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import junit.framework.Assert.assertNull
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import kotlin.system.exitProcess
import android.text.Editable

import android.text.TextWatcher
import android.webkit.CookieManager
import acr.browser.lightning.html.incognito.IncognitoPageFactory
import acr.browser.lightning.locale.LocaleUtils
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import android.view.KeyboardShortcutGroup

import androidx.annotation.RequiresApi

import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import dagger.hilt.android.AndroidEntryPoint

/**
 *
 */
@AndroidEntryPoint
abstract class BrowserActivity : ThemedBrowserActivity(), BrowserView, UIController, OnClickListener {

    // Notifications
    lateinit var CHANNEL_ID: String

    // Current tab view being displayed
    // Should always be a [WebViewEx] but that could change
    private var currentTabView: View? = null
    // Our tab view back and front containers
    // We swap them as needed to make sure our view animations are performed smoothly and without flicker
    private lateinit var iTabViewContainerBack: PullRefreshLayout
    private lateinit var iTabViewContainerFront: PullRefreshLayout
    // Points to current tab animator or null if no tab animation is running
    private var iTabAnimator: ViewPropertyAnimator? = null

    // Full Screen Video Views
    private var fullscreenContainerView: FrameLayout? = null
    private var videoView: VideoView? = null
    private var customView: View? = null

    // Adapter
    private var suggestionsAdapter: SuggestionsAdapter? = null

    // Callback
    private var customViewCallback: CustomViewCallback? = null
    private var uploadMessageCallback: ValueCallback<Uri>? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Primitives
    private var isFullScreen: Boolean = false
    private var hideStatusBar: Boolean = false

    private var isImmersiveMode = false
    private var verticalTabBar: Boolean = false
    private var tabBarInDrawer: Boolean = false
    private var swapBookmarksAndTabs: Boolean = false

    private var originalOrientation: Int = 0
    private var currentUiColor = Color.BLACK
    var currentToolBarTextColor = Color.BLACK
    private var keyDownStartTime: Long = 0
    private var searchText: String? = null
    private var cameraPhotoPath: String? = null


    // The singleton BookmarkManager
    @Inject lateinit var bookmarkManager: BookmarkRepository
    @Inject lateinit var historyModel: HistoryRepository
    @Inject lateinit var searchBoxModel: SearchBoxModel
    @Inject lateinit var searchEngineProvider: SearchEngineProvider
    @Inject lateinit var inputMethodManager: InputMethodManager
    @Inject lateinit var clipboardManager: ClipboardManager
    @Inject lateinit var notificationManager: NotificationManager
    @Inject @field:DiskScheduler lateinit var diskScheduler: Scheduler
    @Inject @field:DatabaseScheduler lateinit var databaseScheduler: Scheduler
    @Inject @field:MainScheduler lateinit var mainScheduler: Scheduler
    @Inject lateinit var homePageFactory: HomePageFactory
    @Inject lateinit var incognitoPageFactory: IncognitoPageFactory
    @Inject lateinit var incognitoPageInitializer: IncognitoPageInitializer
    @Inject lateinit var bookmarkPageFactory: BookmarkPageFactory
    @Inject lateinit var historyPageFactory: HistoryPageFactory
    @Inject lateinit var historyPageInitializer: HistoryPageInitializer
    @Inject lateinit var downloadPageInitializer: DownloadPageInitializer
    @Inject lateinit var homePageInitializer: HomePageInitializer
    @Inject lateinit var bookmarkPageInitializer: BookmarkPageInitializer
    @Inject @field:MainHandler lateinit var mainHandler: Handler
    @Inject lateinit var proxyUtils: ProxyUtils
    @Inject lateinit var logger: Logger
    @Inject lateinit var bookmarksDialogBuilder: LightningDialogBuilder
    @Inject lateinit var exitCleanup: ExitCleanup
    @Inject lateinit var abpUserRules: AbpUserRules
    //
    @Inject lateinit var tabsManager: TabsManager
    @Inject lateinit var presenter: BrowserPresenter

    // HTTP
    private lateinit var queue: RequestQueue

    // Image
    private var webPageBitmap: Bitmap? = null
    private val backgroundDrawable = ColorDrawable()
    private var incognitoNotification: IncognitoNotification? = null

    private var tabsView: TabsView? = null
    private var bookmarksView: BookmarksDrawerView? = null

    // Menus
    private lateinit var iMenuMain: MenuMain
    private lateinit var iMenuWebPage: MenuWebPage
    lateinit var iMenuSessions: SessionsPopupWindow
    //TODO: put that in settings
    private lateinit var tabsDialog: BottomSheetDialog
    private lateinit var bookmarksDialog: BottomSheetDialog

    // Binding
    lateinit var iBinding: ActivityMainBinding
    lateinit var iBindingToolbarContent: ToolbarContentBinding

    // Toolbar Views
    private lateinit var searchView: SearchView
    private lateinit var buttonSessions: ImageButton

    // Settings
    private var crashReport = true
    private var analytics = true
    private var showCloseTabButton = false

    private val longPressBackRunnable = Runnable {
        // Disable this for now as it is popping up when exiting full screen video mode.
        // See: https://github.com/Slion/Fulguris/issues/81
        //showCloseDialog(tabsManager.positionOf(tabsManager.currentTab))
    }

    /**
     * Determines if the current browser instance is in incognito mode or not.
     */
    public abstract fun isIncognito(): Boolean

    /**
     * Choose the behavior when the controller closes the view.
     */
    abstract override fun closeActivity()

    /**
     * Choose what to do when the browser visits a website.
     *
     * @param title the title of the site visited.
     * @param url the url of the site visited.
     */
    abstract override fun updateHistory(title: String?, url: String)

    /**
     * An observable which asynchronously updates the user's cookie preferences.
     */
    protected abstract fun updateCookiePreference(): Completable

    override fun onCreate(savedInstanceState: Bundle?) {
        // Need to go first to inject our components
        super.onCreate(savedInstanceState)

        // Register lifecycle observers
        lifecycle.addObserver(tabsManager)
        lifecycle.addObserver(presenter)
        //
        createKeyboardShortcuts()

        if (BrowserApp.instance.justStarted) {
            BrowserApp.instance.justStarted = false
            // Since amazingly on Android you can't tell when your app is closed we do exit cleanup on start-up, go figure
            // See: https://github.com/Slion/Fulguris/issues/106
            performExitCleanUp()
        }

        iBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        iTabViewContainerBack = iBinding.tabViewContainerOne
        iTabViewContainerFront = iBinding.tabViewContainerTwo

        // Setup our find in page bindings
        iBinding.findInPageInclude.searchQuery.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    // Trigger on setText
                }
                override fun afterTextChanged(s: Editable) {
                    if (!iSkipNextSearchQueryUpdate) {
                        tabsManager.currentTab?.find(s.toString())
                    }
                    iSkipNextSearchQueryUpdate = false
                }
            })
        iBinding.findInPageInclude.buttonNext.setOnClickListener(this)
        iBinding.findInPageInclude.buttonBack.setOnClickListener(this)
        iBinding.findInPageInclude.buttonQuit.setOnClickListener(this)

        queue = Volley.newRequestQueue(this)
        createMenuMain()
        createMenuWebPage()
        createMenuSessions()
        tabsDialog = BottomSheetDialog(this)
        bookmarksDialog = BottomSheetDialog(this)


        if (isIncognito()) {
            incognitoNotification = IncognitoNotification(this, notificationManager)
        }
        tabsManager.addTabNumberChangedListener {
            if (isIncognito()) {
                if (it == 0) {
                    incognitoNotification?.hide()
                } else {
                    incognitoNotification?.show(it)
                }
            }
        }
        tabsManager.addTabNumberChangedListener(::updateTabNumber)

        // Setup our presenter
        presenter.iBrowserView = this
        presenter.closedTabs = RecentTabsModel()
        presenter.isIncognito = isIncognito()
        presenter.tabsModel = tabsManager


        initialize(savedInstanceState)

        if (BuildConfig.FLAVOR.contains("slionsFullDownload")) {
            tabsManager.doOnceAfterInitialization {
                // Check for update after a short delay, hoping user engagement is better and message more visible
                mainHandler.postDelayed({ checkForUpdates() }, 3000)
            }
        }

        // Welcome new users or notify of updates
        tabsManager.doOnceAfterInitialization {
            // If our version code was changed
            if (userPreferences.versionCode != BuildConfig.VERSION_CODE) {
                if (userPreferences.versionCode==0
                        // Added this check to avoid show welcome message to existing installation
                        // TODO: Remove that a few versions down the road
                        && tabsManager.iSessions.count()==1 && tabsManager.allTabs.count()==1) {
                    // First run
                    welcomeToFulguris()
                } else {
                    // Version was updated
                    notifyVersionUpdate()
                }
                // Persist our current version so that we don't kick in next time
                userPreferences.versionCode = BuildConfig.VERSION_CODE
            }
        }

        // This callback is trigger after we switch session, Could be useful at some point
        //tabsManager.doAfterInitialization {}

        // Hook in buttons with onClick handler
        iBindingToolbarContent.buttonReload.setOnClickListener(this)
    }

    /**
     *
     */
    private fun createMenuSessions() {
        iMenuSessions = SessionsPopupWindow(layoutInflater)
        // Make it full screen gesture friendly
        iMenuSessions.setOnDismissListener { justClosedMenuCountdown() }
    }

    // Used to avoid running that too many times, by keeping a reference to it we can cancel that runnable
    //  That works around graphical glitches happening when run too many times
    var onSizeChangeRunnable : Runnable = Runnable {};
    // Used to cancel that runnable as needed
    private var resetBackgroundColorRunnable : Runnable = Runnable {};

    /**
     * Used for both tabs and bookmarks.
     */
    private fun createBottomSheetDialog(aContentView: View) : BottomSheetDialog {
        val dialog = BottomSheetDialog(this)

        // Set up BottomSheetDialog
        dialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        dialog.window?.setFlags(window.attributes.flags, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        //dialog.window?.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        //dialog.window?.setFlags(dialog.window?.attributes!!.flags, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        //dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        // Needed to make sure our bottom sheet shows below our session pop-up
        // TODO: that breaks status bar icon color with our light theme somehow
        //dialog.window?.attributes?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        //

        // We need to set private data member edgeToEdgeEnabled to true to get full screen effect
        // That won't be needed past material:1.4.0-alpha02 as it is read from our theme definition from then on
        //val field = BottomSheetDialog::class.java.getDeclaredField("edgeToEdgeEnabled")
        //field.isAccessible = true
        //field.setBoolean(dialog, true)

        //
        aContentView.removeFromParent()
        dialog.setContentView(aContentView)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.isDraggable = !userPreferences.lockedDrawers
        // Fix for https://github.com/Slion/Fulguris/issues/226
        dialog.behavior.maxWidth = -1 // We want fullscreen width

        // Make sure dialog top padding and status bar icons color are updated whenever our dialog is resized
        // Since we keep recreating our dialogs every time we open them we should not accumulate observers here
        (aContentView.parent as View).onSizeChange {
            // This is designed so that callbacks are cancelled unless our timeout expires
            // That avoids spamming adjustBottomSheet while our view is animated or dragged
            mainHandler.removeCallbacks(onSizeChangeRunnable)
            onSizeChangeRunnable = Runnable {
                // Catch and ignore exceptions as adjustBottomSheet is using reflection to call private methods.
                // Jamal was reporting this was not working on his device for some reason.
                try {
                    // Also I'm not sure now why we needed that, maybe it has since been fixed in the material components library.
                    // Though the GitHub issue specified in that function description is still open.
                    adjustBottomSheet(dialog)
                } catch (ex: java.lang.Exception) {
                    logger.log(TAG, "adjustBottomSheet: $ex")
                }
            }
            mainHandler.postDelayed(onSizeChangeRunnable, 100)
        }

        return dialog;
    }


    /**
     *
     */
    private fun createTabsDialog()
    {
        tabsDialog.dismiss() // Defensive
        // Workaround issue with black icons during transition after first use
        // See: https://github.com/material-components/material-components-android/issues/2168
        tabsDialog = createBottomSheetDialog(tabsView as View)
        // Once our bottom sheet is open we want it to scroll to current tab
        tabsDialog.setOnShowListener { scrollToCurrentTab() }
        /*
        tabsDialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // State change is not called if we don't recreate our dialog or actually change the state, which makes sense
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    mainHandler.postDelayed({scrollToCurrentTab()},1000)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        }
        )*/

    }

    /**
     *
     */
    private fun createBookmarksDialog()
    {
        bookmarksDialog.dismiss() // Defensive
        // Workaround issue with black icons during transition after first use.
        // See: https://github.com/material-components/material-components-android/issues/2168
        bookmarksDialog = createBottomSheetDialog(bookmarksView as View)

    }

    /**
     * Open our sessions pop-up menu.
     */
    private fun showSessions() {
        // If using horizontal tab bar or if our tab drawer is open
        if ((!verticalTabBar && tabBarInDrawer) || tabsDialog.isShowing) {
            // Use sessions button as anchor
            buttonSessions.let { iMenuSessions.show(it) }
        } else if (!tabBarInDrawer) {
            // Vertical tab bar desktop style
            (tabsView as? TabsDrawerView)?.iBinding?.actionSessions?.let {iMenuSessions.show(it)}
        } else {
            // Otherwise use main menu button as anchor
            iBindingToolbarContent.buttonMore.let { iMenuSessions.show(it) }
        }
    }

    // Set whenever a menu was just closed
    private var iJustClosedMenu = false

    /**
     * Used to avoid processing back commands when we just closed a menu.
     * That's done to improve user experience when user is using full screen gesture.
     * As user does its full screen back gesture it in fact dismisses the menu just by touching the screen outside the menu.
     * That meant once the back gesture was completed it reached the activity which was processing it as if the menu were not there.
     * In the end it looked like two back keys were processed.
     */
    private fun justClosedMenuCountdown() {
        iJustClosedMenu = true; mainHandler.postDelayed({iJustClosedMenu = false},250)
    }

    /**
     *
     */
    private fun createMenuMain() {
        iMenuMain = MenuMain(layoutInflater)
        // TODO: could use data binding instead
        iMenuMain.apply {
            // Menu
            onMenuItemClicked(iBinding.menuItemWebPage) { dismiss(); showMenuWebPage() }
            // Bind our actions
            onMenuItemClicked(iBinding.menuItemSessions) { dismiss(); executeAction(R.id.action_sessions) }
            onMenuItemClicked(iBinding.menuItemNewTab) { dismiss(); executeAction(R.id.action_new_tab) }
            onMenuItemClicked(iBinding.menuItemIncognito) { dismiss(); executeAction(R.id.action_incognito) }
            onMenuItemClicked(iBinding.menuItemHistory) { dismiss(); executeAction(R.id.action_history) }
            onMenuItemClicked(iBinding.menuItemDownloads) { dismiss(); executeAction(R.id.action_downloads) }
            onMenuItemClicked(iBinding.menuItemBookmarks) { dismiss(); executeAction(R.id.action_bookmarks) }
            onMenuItemClicked(iBinding.menuItemExit) { dismiss(); executeAction(R.id.action_exit) }
            //
            onMenuItemClicked(iBinding.menuItemSettings) { dismiss(); executeAction(R.id.action_settings) }

            // Popup menu action shortcut icons
            onMenuItemClicked(iBinding.menuShortcutRefresh) { dismiss(); executeAction(R.id.action_reload) }
            onMenuItemClicked(iBinding.menuShortcutHome) { dismiss(); executeAction(R.id.action_show_homepage) }
            onMenuItemClicked(iBinding.menuShortcutBookmarks) { dismiss(); executeAction(R.id.action_bookmarks) }
            // Back and forward do not dismiss the menu to make it easier for users to navigate tab history
            onMenuItemClicked(iBinding.menuShortcutForward) { iBinding.layoutMenuItemsContainer.isVisible=false; executeAction(R.id.action_forward) }
            onMenuItemClicked(iBinding.menuShortcutBack) { iBinding.layoutMenuItemsContainer.isVisible=false; executeAction(R.id.action_back) }

            // Make it full screen gesture friendly
            setOnDismissListener { justClosedMenuCountdown() }
        }
    }

    /**
     *
     */
    private fun showMenuMain() {
        // Make sure back and forward buttons are in correct state
        setForwardButtonEnabled(tabsManager.currentTab?.canGoForward()?:false)
        setBackButtonEnabled(tabsManager.currentTab?.canGoBack()?:false)
        // Open our menu
        iMenuMain.show(iBindingToolbarContent.buttonMore)
    }

    /**
     *
     */
    private fun createMenuWebPage() {
        iMenuWebPage = MenuWebPage(layoutInflater)
        // TODO: could use data binding instead
        iMenuWebPage.apply {
            onMenuItemClicked(iBinding.menuItemMainMenu) { dismiss(); showMenuMain() }
            // Web page actions
            onMenuItemClicked(iBinding.menuItemShare) { dismiss(); executeAction(R.id.action_share) }
            onMenuItemClicked(iBinding.menuItemAddBookmark) { dismiss(); executeAction(R.id.action_add_bookmark) }
            onMenuItemClicked(iBinding.menuItemFind) { dismiss(); executeAction(R.id.action_find) }
            onMenuItemClicked(iBinding.menuItemPrint) { dismiss(); executeAction(R.id.action_print) }
            onMenuItemClicked(iBinding.menuItemAddToHome) { dismiss(); executeAction(R.id.action_add_to_homescreen) }
            onMenuItemClicked(iBinding.menuItemReaderMode) { dismiss(); executeAction(R.id.action_reading_mode) }
            onMenuItemClicked(iBinding.menuItemDesktopMode) { dismiss(); executeAction(R.id.action_toggle_desktop_mode) }
            onMenuItemClicked(iBinding.menuItemDarkMode) { dismiss(); executeAction(R.id.action_toggle_dark_mode) }
            onMenuItemClicked(iBinding.menuItemAdBlock) { dismiss(); executeAction(R.id.action_block) }
            onMenuItemClicked(iBinding.menuItemTranslate) { dismiss(); executeAction(R.id.action_translate) }
            // Popup menu action shortcut icons
            onMenuItemClicked(iBinding.menuShortcutRefresh) { dismiss(); executeAction(R.id.action_reload) }
            onMenuItemClicked(iBinding.menuShortcutHome) { dismiss(); executeAction(R.id.action_show_homepage) }
            // Back and forward do not dismiss the menu to make it easier for users to navigate tab history
            onMenuItemClicked(iBinding.menuShortcutForward) { iBinding.layoutMenuItemsContainer.isVisible=false; executeAction(R.id.action_forward) }
            onMenuItemClicked(iBinding.menuShortcutBack) { iBinding.layoutMenuItemsContainer.isVisible=false; executeAction(R.id.action_back) }
            //onMenuItemClicked(iBinding.menuShortcutBookmarks) { executeAction(R.id.action_bookmarks) }

            // Make it full screen gesture friendly
            setOnDismissListener { justClosedMenuCountdown() }
        }
    }

    /**
     *
     */
    private fun showMenuWebPage() {
        iMenuWebPage.show(iBindingToolbarContent.buttonMore)
    }


    /**
     * Needed to be able to display system notifications
     */
    private fun createNotificationChannel() {
        // Is that string visible in system UI somehow?
        CHANNEL_ID = "Fulguris Channel ID"
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.downloads)
            val descriptionText = getString(R.string.downloads_notification_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Provide primary color, typically used as default toolbar color.
     */
    private val primaryColor: Int
        get() {
            // If current tab is using forced dark mode and we do not use a dark theme…
            return if (tabsManager.currentTab?.darkMode == true && !useDarkTheme) {
                // …then override primary color…
                Color.BLACK
            } else {
                // …otherwise just use current theme surface color.
                ThemeUtils.getSurfaceColor(this)
            }
        }


    /**
     * See below.
     */
    private val iDisableFabs : Runnable = Runnable {
        iBinding.fabContainer.isVisible = false
        tabSwitchStop()
    }

    // Used to manage our Easy Tab Switcher
    private var iTabsButtonLongPressed = false
    private var iEasyTabSwitcherWasUsed = false

    /**
     * Will disable floating action buttons once our countdown expires
     */
    private fun restartDisableFabsCountdown() {
        if (!iTabsButtonLongPressed) {
            // Cancel any pending action if any
            cancelDisableFabsCountdown()
            // Restart our countdown
            // TODO: make that delay a settings option?
            mainHandler.postDelayed(iDisableFabs, 5000)
        }
    }

    /**
     *
     */
    private fun cancelDisableFabsCountdown() {
        mainHandler.removeCallbacks(iDisableFabs)
    }
    /**
     *
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initialize(savedInstanceState: Bundle?) {

        createNotificationChannel()

        // Create our toolbar and hook it to its parent
        iBindingToolbarContent = ToolbarContentBinding.inflate(layoutInflater, iBinding.toolbarInclude.toolbar, true)

        // TODO: disable those for incognito mode?
        analytics = userPreferences.analytics
        crashReport = userPreferences.crashReport
        showCloseTabButton = userPreferences.showCloseTabButton

        if (!isIncognito()) {
            // For some reason that was crashing when incognito
            // I'm guessing somehow that's already disabled when incognito
            setAnalyticsCollectionEnabled(this, userPreferences.analytics)
            setCrashlyticsCollectionEnabled(userPreferences.crashReport)
        }

        swapBookmarksAndTabs = userPreferences.bookmarksAndTabsSwapped

        // initialize background ColorDrawable
        backgroundDrawable.color = primaryColor

        // Drawer stutters otherwise
        //left_drawer.setLayerType(LAYER_TYPE_NONE, null)
        //right_drawer.setLayerType(LAYER_TYPE_NONE, null)


        iBinding.drawerLayout.addDrawerListener(DrawerLocker())

        webPageBitmap = drawable(R.drawable.ic_webpage).toBitmap()

        // Show incognito icon in more menu button
        if (isIncognito()) {
            iBindingToolbarContent.buttonMore.setImageResource(R.drawable.ic_incognito)
        }

        // Is that still needed
        val customView = iBinding.toolbarInclude.toolbar
        customView.layoutParams = customView.layoutParams.apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }

        // Define tabs button clicks handlers
        iBindingToolbarContent.tabsButton.setOnClickListener(this)
        iBindingToolbarContent.tabsButton.setOnLongClickListener { view ->
            iBinding.fabContainer.isVisible = true
            iTabsButtonLongPressed = true
            iEasyTabSwitcherWasUsed = false
            cancelDisableFabsCountdown()
            tabSwitchStart()
            // We still want tooltip to show so return false here
            false
        }

        // Handle release of tabs button after long press
        iBindingToolbarContent.tabsButton.setOnTouchListener{ v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {}
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (iTabsButtonLongPressed) {
                        iTabsButtonLongPressed = false
                        if (iEasyTabSwitcherWasUsed) {
                            // Tabs button was released after using tab switcher
                            // User was using multiple fingers
                            // Hide fabs on the spot to emulate CTRL+TAB best
                            iDisableFabs.run()
                        } else {
                            // Tabs button was released without using tab switcher
                            // Give a chance to the user to use it with a single finger
                            // Only hide fabs after countdown
                            restartDisableFabsCountdown()
                        }
                    }
                }
            }
            false
        }

        // Close current tab during tab switch
        // TODO: What if a tab is opened during tab switch?
        iBinding.fabTabClose.setOnClickListener {
            iEasyTabSwitcherWasUsed = true
            restartDisableFabsCountdown()
            tabsManager.let { presenter.deleteTab(it.indexOfCurrentTab()) }
            tabSwitchReset()
        }

        // Switch back in our tab list
        iBinding.fabBack.setOnClickListener {
            iEasyTabSwitcherWasUsed = true
            restartDisableFabsCountdown()
            tabSwitchBack()
            tabSwitchApply(true)
        }

        // Switch forward in our tab list
        iBinding.fabForward.setOnClickListener{
            iEasyTabSwitcherWasUsed = true
            restartDisableFabsCountdown()
            tabSwitchForward()
            tabSwitchApply(false)
        }


        iBindingToolbarContent.homeButton.setOnClickListener(this)
        iBindingToolbarContent.buttonActionBack.setOnClickListener{executeAction(R.id.action_back)}
        iBindingToolbarContent.buttonActionForward.setOnClickListener{executeAction(R.id.action_forward)}

        //setFullscreenIfNeeded(resources.configuration) // As that's needed before bottom sheets creation
        createTabsView(resources.configuration)
        //createTabsDialog()
        bookmarksView = BookmarksDrawerView(this)
        //createBookmarksDialog()

        // create the search EditText in the ToolBar
        searchView = iBindingToolbarContent.addressBarInclude.search.apply {
            iBindingToolbarContent.addressBarInclude.searchSslStatus.setOnClickListener {
                tabsManager.currentTab?.let { tab ->
                    tab.sslCertificate?.let { showSslDialog(it, tab.currentSslState()) }
                }
            }
            iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
            //setMenuItemIcon(R.id.action_reload, R.drawable.ic_action_refresh)
            //toolbar?.menu?.findItem(R.id.action_reload)?.let { it.icon = ContextCompat.getDrawable(this@BrowserActivity, R.drawable.ic_action_refresh) }

            val searchListener = SearchListenerClass()
            setOnKeyListener(searchListener)
            onFocusChangeListener = searchListener
            setOnEditorActionListener(searchListener)
            onPreFocusListener = searchListener
            addTextChangedListener(StyleRemovingTextWatcher())

            initializeSearchSuggestions(this)
        }

        // initialize search background color
        setSearchBarColors(primaryColor)

        var intent: Intent? = if (savedInstanceState == null) {
            intent
        } else {
            null
        }

        val launchedFromHistory = intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

        if (intent?.action == INTENT_PANIC_TRIGGER) {
            setIntent(null)
            panicClean()
        } else {
            if (launchedFromHistory) {
                intent = null
            }
            // Load our tabs
            // TODO: Consider not reloading our session if it is already loaded.
            // That could be the case notably when the activity is restarted after theme change in settings
            // However that would require we careful setup our UI anew from an already loaded session
            presenter.setupTabs(intent)
            setIntent(null)
            proxyUtils.checkForProxy(this)
        }

        // Enable swipe to refresh
        iTabViewContainerFront.setOnRefreshListener {
            tabsManager.currentTab?.reload()
            mainHandler.postDelayed({ iTabViewContainerFront.isRefreshing = false }, 1000)   // Stop the loading spinner after one second
        }

        iTabViewContainerBack.setOnRefreshListener {
            tabsManager.currentTab?.reload()
            // Assuming this guys will be in front when refreshing
            mainHandler.postDelayed({ iTabViewContainerFront.isRefreshing = false }, 1000)   // Stop the loading spinner after one second
        }
        // TODO: define custom transitions to make flying in and out of the tool bar nicer
        //ui_layout.layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, ui_layout.layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING))
        // Disabling animations which are not so nice
        iBinding.uiLayout.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        iBinding.uiLayout.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)


        iBindingToolbarContent.buttonMore.setOnClickListener(OnClickListener {
            // Web page is loosing focus as we open our menu
            // Should notably hide the virtual keyboard
            currentTabView?.clearFocus()
            searchView.clearFocus()
            // Set focus to menu button
            it.requestFocus()
            // Show popup menu once our virtual keyboard is hidden
            showPopupMenuWhenReady()
        })
    }

    // Make sure we will show our popup menu at some point
    private var iPopupMenuTries: Int = 0
    private val kMaxPopupMenuTries: Int = 5

    /**
     * Show popup menu once our virtual keyboard is hidden.
     * This was designed so that popup menu does not remain in the middle of the screen once virtual keyboard is hidden,
     * notably when using toolbars at the bottom option.
     */
    private fun showPopupMenuWhenReady() {
        // Check if virtual keyboard is showing and if we have another try to wait for it to close
        if (inputMethodManager.isVirtualKeyboardVisible() && iPopupMenuTries<kMaxPopupMenuTries) {
            // Increment our tries counter
            iPopupMenuTries++
            // Open our menu with a slight delay giving enough time for our virtual keyboard to close
            mainHandler.postDelayed({ showPopupMenuWhenReady() }, 100)
        } else {
            //Display our popup menu instantly
            showMenuMain()
            // Reset tries counter for the next time around
            iPopupMenuTries = 0
        }
    }

    /**
     *
     */
    private fun closePanelTabs() {
        iBinding.drawerLayout.closeDrawer(getTabDrawer())
        tabsDialog.dismiss()
    }

    /**
     *
     */
    private fun closePanelBookmarks() {
        iBinding.drawerLayout.closeDrawer(getBookmarkDrawer())
        bookmarksDialog.dismiss()
    }

    /**
     * Used to create or recreate our tabs view according to current settings.
     */
    private fun createTabsView(configuration: Configuration) {

        verticalTabBar = configPrefs(configuration).verticalTabBar
        tabBarInDrawer = configPrefs(configuration).tabBarInDrawer

        // Was needed when resizing on Windows 11 and changing from horizontal to vertical tab bar
        mainHandler.postDelayed( {closePanels(null)},100)

        // Remove existing tab view if any
        (tabsView as View?)?.removeFromParent()
        // Instantiate our view
        tabsView = if (verticalTabBar) {
            TabsDrawerView(this)
        } else {
            TabsDesktopView(this)
        }
        createTabsDialog()
        // Add it to proper parent
        addTabsViewToParent()

        buttonSessions = (tabsView as View).findViewById(R.id.action_sessions)

        if (verticalTabBar) {
            iBindingToolbarContent.tabsButton.isVisible = true
            iBindingToolbarContent.homeButton.isVisible = false
            iBinding.toolbarInclude.tabBarContainer.isVisible = false
            iBinding.layoutTabsLeft.isVisible = !tabBarInDrawer && !swapBookmarksAndTabs
            iBinding.layoutTabsRight.isVisible = !tabBarInDrawer && swapBookmarksAndTabs
            //iBinding.leftDrawer.isVisible = tabBarInDrawer
            //iBinding.rightDrawer.isVisible = tabBarInDrawer
        } else {
            iBindingToolbarContent.tabsButton.isVisible = false
            iBindingToolbarContent.homeButton.isVisible = true
            iBinding.toolbarInclude.tabBarContainer.isVisible = true
            iBinding.layoutTabsLeft.isVisible = false
            iBinding.layoutTabsRight.isVisible = false
            //iBinding.leftDrawer.isVisible = true
            //iBinding.rightDrawer.isVisible = true
        }
    }

    /**
     * Add our tabs view to proper parent according to current configuration and settings.
     * Parent can be the bottom sheet dialog, one of our side drawers, or the tab bar container.
     * We are also taking care of checking which parent is currently the current to make sure we do not take action if not needed.
     */
    private fun addTabsViewToParent() {
        val v = (tabsView as View)
        if (verticalTabBar && userPreferences.useBottomSheets) {
            // Check if our tabs list already belongs to our bottom sheet
            if (tabsDialog.findViewById<ViewGroup>(R.id.tabs_list) != v.findViewById<ViewGroup>(R.id.tabs_list)) {
                // It was not found, just put it there then
                v.removeFromParent()
                tabsDialog.setContentView(v)
            }
        } else {
            // Check if our tab view is already in place
            if (v.parent != getTabBarContainer()) {
                // It was not, lets put it there then
                v.removeFromParent()
                getTabBarContainer().addView(v)
            }
        }
    }

    private fun getBookmarksContainer(): ViewGroup = if (swapBookmarksAndTabs) {
        iBinding.leftDrawer
    } else {
        iBinding.rightDrawer
    }

    /**
     *
     */
    private fun getTabBarContainer(): ViewGroup =
            if (verticalTabBar) {
                if (swapBookmarksAndTabs) {
                    if (tabBarInDrawer) {
                        iBinding.rightDrawer
                    } else {
                        iBinding.layoutTabsRight
                    }
                } else {
                    if (tabBarInDrawer) {
                        iBinding.leftDrawer
                    } else {
                        iBinding.layoutTabsLeft
                    }
                }
            } else {
                iBinding.toolbarInclude.tabBarContainer
            }

    private fun getBookmarkDrawer(): View = if (swapBookmarksAndTabs) {
        iBinding.leftDrawer
    } else {
        iBinding.rightDrawer
    }

    private fun getTabDrawer(): View = if (swapBookmarksAndTabs) {
        iBinding.rightDrawer
    } else {
        iBinding.leftDrawer
    }

    protected fun panicClean() {
        logger.log(TAG, "Closing browser")
        tabsManager.newTab(this, NoOpInitializer(), false, NewTabPosition.END_OF_TAB_LIST)
        tabsManager.switchToTab(0)
        tabsManager.clearSavedState()

        historyPageFactory.deleteHistoryPage().subscribe()
        closeBrowser()
        // System exit needed in the case of receiving
        // the panic intent since finish() isn't completely
        // closing the browser
        exitProcess(1)
    }

    private inner class SearchListenerClass : OnKeyListener,
            OnEditorActionListener,
            OnFocusChangeListener,
            SearchView.PreFocusListener {

        override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    searchView.let {
                        if (it.listSelection == ListView.INVALID_POSITION) {
                            // No suggestion pop up item selected, just trigger a search then
                            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                            searchTheWeb(it.text.toString())
                        } else {
                            // An item in our selection pop up is selected, just action it
                            doSearchSuggestionAction(it, it.listSelection)
                        }
                    }
                    tabsManager.currentTab?.requestFocus()
                    return true
                }
                else -> {
                }
            }
            return false
        }

        override fun onEditorAction(arg0: TextView, actionId: Int, arg2: KeyEvent?): Boolean {
            // hide the keyboard and search the web when the enter key
            // button is pressed
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT
                    || actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || arg2?.action == KeyEvent.KEYCODE_ENTER) {
                searchView.let {
                    inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                    searchTheWeb(it.text.toString())
                }

                tabsManager.currentTab?.requestFocus()
                return true
            }
            return false
        }

        override fun onFocusChange(v: View, hasFocus: Boolean) {
            val currentView = tabsManager.currentTab

            if (currentView != null) {
                setIsLoading(currentView.progress < 100)

                if (!hasFocus) {
                    updateUrl(currentView.url, false)
                } else if (hasFocus) {
                    showUrl()
                    // Select all text so that user conveniently start typing or copy current URL
                    (v as SearchView).selectAll()
                    iBindingToolbarContent.addressBarInclude.searchSslStatus.visibility = GONE
                }
            }

            if (!hasFocus) {
                iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
                searchView.let {
                    inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                }
            }
        }

        override fun onPreFocus() {
            // SL: hopefully not needed anymore
            // That was never working with keyboard
            //val currentView = tabsManager.currentTab ?: return
            //val url = currentView.url
            //if (!url.isSpecialUrl()) {
            //    if (searchView.hasFocus() == false) {
            //        searchView.setText(url)
            //    }
            //}
        }
    }

    /**
     * Called when search view gains focus
     */
    private fun showUrl() {
        val currentView = tabsManager.currentTab ?: return
        val url = currentView.url
        if (!url.isSpecialUrl()) {
            searchView.setText(url)
        }
        else {
            // Special URLs like home page and history just show search field then
            searchView.setText("")
        }
    }

    var drawerOpened : Boolean = false
    var drawerOpening : Boolean = false
    var drawerClosing : Boolean = false

    private inner class DrawerLocker : DrawerLayout.DrawerListener {

        override fun onDrawerClosed(v: View) {
            drawerOpened = false
            drawerClosing = false
            drawerOpening = false

            // Trying to sort out our issue with touch input reaching through drawer into address bar
            //toolbar_layout.isEnabled = true

            // This was causing focus problems when switching directly from tabs drawer to bookmarks drawer
            //currentTabView?.requestFocus()

            if (userPreferences.lockedDrawers) return; // Drawers remain locked
            val tabsDrawer = getTabDrawer()
            val bookmarksDrawer = getBookmarkDrawer()

            if (v === tabsDrawer) {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, bookmarksDrawer)
            } else if (verticalTabBar) {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, tabsDrawer)
            }

        }

        override fun onDrawerOpened(v: View) {

            drawerOpened = true
            drawerClosing = false
            drawerOpening = false

            // Trying to sort out our issue with touch input reaching through drawer into address bar
            //toolbar_layout.isEnabled = false

            if (userPreferences.lockedDrawers) return; // Drawers remain locked

            val tabsDrawer = getTabDrawer()
            val bookmarksDrawer = getBookmarkDrawer()

            if (v === tabsDrawer) {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, bookmarksDrawer)
            } else {
                iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, tabsDrawer)
            }
        }

        override fun onDrawerSlide(v: View, arg: Float) = Unit

        override fun onDrawerStateChanged(arg: Int) {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return;
            }

            // Make sure status bar icons have the proper color set when we start opening and closing a drawer
            // We set status bar icon color according to current theme
            if (arg == ViewDragHelper.STATE_SETTLING) {
                if (!drawerOpened) {
                    drawerOpening = true
                    // Make sure icons on status bar remain visible
                    // We should really check the primary theme color and work out its luminance but that should do for now
                    window.setStatusBarIconsColor(!useDarkTheme && !userPreferences.useBlackStatusBar)
                }
                else {
                    drawerClosing = true
                    // Restore previous system UI visibility flag
                    setToolbarColor()
                }
            }
        }
    }


    private fun lockDrawers()
    {
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getTabDrawer())
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getBookmarkDrawer())
    }

    private fun unlockDrawers()
    {
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, getTabDrawer())
        iBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, getBookmarkDrawer())
    }


    /**
     * Set toolbar color corresponding to the current tab
     */
    private fun setToolbarColor()
    {
        val currentView = tabsManager.currentTab
        if (isColorMode() && currentView != null && currentView.htmlMetaThemeColor!=Color.TRANSPARENT && !currentView.darkMode) {
            // Web page does specify theme color, use it much like Google Chrome does
            mainHandler.post {applyToolbarColor(currentView.htmlMetaThemeColor)}
        }
        else if (isColorMode() && currentView?.favicon != null && !currentView.darkMode) {
            // Web page has favicon, use it to extract page theme color
            changeToolbarBackground(currentView.favicon, Color.TRANSPARENT, null)
        } else {
            // That should be the primary color from current theme
            mainHandler.post {applyToolbarColor(primaryColor)}
        }
    }

    /**
     *
     */
    private fun initFullScreen(configuration: Configuration) {
        isFullScreen = configPrefs(configuration).hideToolBar
    }


    private var wasToolbarsBottom = false;

    /**
     * Setup our tool bar as collapsible or always-on according to orientation and user preferences.
     * Also manipulate our layout according to toolbars-at-bottom user preferences.
     */
    private fun setupToolBar(configuration: Configuration) {
        initFullScreen(configuration)
        initializeToolbarHeight(configuration)
        showActionBar()
        setToolbarColor()
        setFullscreenIfNeeded(configuration)

        // Put our toolbar where it belongs, top or bottom according to user preferences
        iBinding.toolbarInclude.apply {
            if (configPrefs.toolbarsBottom) {

                // Move search bar to the bottom
                iBinding.findInPageInclude.root.let {
                    it.removeFromParent()?.addView(it)
                }

                // Move toolbar to the bottom
                root.removeFromParent()?.addView(root)

                // Rearrange it so that it is upside down
                // Put tab bar at the bottom
                tabBarContainer.removeFromParent()?.addView(tabBarContainer)
                // Put progress bar at the top
                progressView.removeFromParent()?.addView(progressView, 0)
                // Take care of tab drawer if any
                (tabsView as? TabsDrawerView)?.apply {
                    // Put our tab list on top then to push toolbar to the bottom
                    iBinding.tabsList.removeFromParent()?.addView(iBinding.tabsList, 0)
                    // Use reversed layout from bottom to top
                    (iBinding.tabsList.layoutManager as? LinearLayoutManager)?.apply {
                        reverseLayout = true
                        // Fix broken scroll to item
                        //stackFromEnd = true
                    }
                }

                // Take care of bookmarks drawer
                (bookmarksView as? BookmarksDrawerView)?.apply {
                    // Put our list on top then to push toolbar to the bottom
                    iBinding.listBookmarks.removeFromParent()?.addView(iBinding.listBookmarks, 0)
                    // Use reversed layout from bottom to top
                    (iBinding.listBookmarks.layoutManager as? LinearLayoutManager)?.reverseLayout = true
                }

                // Deal with session menu
                if (configPrefs.verticalTabBar && !configPrefs.tabBarInDrawer) {
                    iMenuSessions.animationStyle = R.style.AnimationMenuDesktopBottom
                } else {
                    iMenuSessions.animationStyle = R.style.AnimationMenuBottom
                }
                (iMenuSessions.iBinding.recyclerViewSessions.layoutManager as? LinearLayoutManager)?.apply {
                    reverseLayout = true
                    stackFromEnd = true
                }
                // Move sessions menu toolbar to the bottom
                iMenuSessions.iBinding.toolbar.apply{removeFromParent()?.addView(this)}

                // Set popup menus animations
                iMenuMain.animationStyle = R.style.AnimationMenuBottom
                // Move popup menu toolbar to the bottom
                iMenuMain.iBinding.header.apply{removeFromParent()?.addView(this)}
                // Move items above our toolbar separator
                iMenuMain.iBinding.scrollViewItems.apply{removeFromParent()?.addView(this, 0)}
                // Reverse menu items if needed
                if (!wasToolbarsBottom) {
                    val children = iMenuMain.iBinding.layoutMenuItems.children.toList()
                    children.reversed().forEach { item -> item.removeFromParent()?.addView(item) }
                }

                // Set popup menus animations
                iMenuWebPage.animationStyle = R.style.AnimationMenuBottom
                // Move popup menu toolbar to the bottom
                iMenuWebPage.iBinding.header.apply{removeFromParent()?.addView(this)}
                // Move items above our toolbar separator
                iMenuWebPage.iBinding.scrollViewItems.apply{removeFromParent()?.addView(this, 0)}
                // Reverse menu items if needed
                if (!wasToolbarsBottom) {
                    val children = iMenuWebPage.iBinding.layoutMenuItems.children.toList()
                    children.reversed().forEach { item -> item.removeFromParent()?.addView(item) }
                }

                // Set search dropdown anchor to avoid gap
                searchView.dropDownAnchor = R.id.address_bar_include

                // Floating Action Buttons at the bottom
                iBinding.fabContainer.apply {setGravityBottom(layoutParams as CoordinatorLayout.LayoutParams)}

                // FAB tab close button at the bottom
                iBinding.fabTabClose.apply {setGravityBottom(layoutParams as LinearLayout.LayoutParams)}

                // ctrlTabBack at the top
                iBinding.fabBack.apply{removeFromParent()?.addView(this, 0)}
            } else {
                // Move search in page to top
                iBinding.findInPageInclude.root.let {
                    it.removeFromParent()?.addView(it, 0)
                }
                // Move toolbar to the top
                root.removeFromParent()?.addView(root, 0)
                //iBinding.uiLayout.addView(root, 0)
                // Rearrange it so that it is the right way up
                // Put tab bar at the bottom
                tabBarContainer.removeFromParent()?.addView(tabBarContainer, 0)
                // Put progress bar at the top
                progressView.removeFromParent()?.addView(progressView)
                // Take care of tab drawer if any
                (tabsView as? TabsDrawerView)?.apply {
                    // Put our tab list at the bottom
                    iBinding.tabsList.removeFromParent()?.addView(iBinding.tabsList)
                    // Use straight layout from top to bottom
                    (iBinding.tabsList.layoutManager as? LinearLayoutManager)?.apply {
                        reverseLayout = false
                        //stackFromEnd = false
                    }
                    // We don't need that spacer now
                    //iBinding.tabsListSpacer.isVisible = false
                }

                // Take care of bookmarks drawer
                (bookmarksView as? BookmarksDrawerView)?.apply {
                    // Put our list at the bottom
                    iBinding.listBookmarks.removeFromParent()?.addView(iBinding.listBookmarks)
                    // Use reversed layout from bottom to top
                    (iBinding.listBookmarks.layoutManager as? LinearLayoutManager)?.reverseLayout = false
                }

                // Deal with session menu
                if (configPrefs.verticalTabBar && !configPrefs.tabBarInDrawer) {
                    iMenuSessions.animationStyle = R.style.AnimationMenuDesktopTop
                } else {
                    iMenuSessions.animationStyle = R.style.AnimationMenu
                }
                (iMenuSessions.iBinding.recyclerViewSessions.layoutManager as? LinearLayoutManager)?.apply {
                    reverseLayout = false
                    stackFromEnd = false
                }
                // Move sessions menu toolbar to the top
                iMenuSessions.iBinding.toolbar.apply{removeFromParent()?.addView(this, 0)}

                // Set popup menus animations
                iMenuMain.animationStyle = R.style.AnimationMenu
                // Move popup menu toolbar to the top
                iMenuMain.iBinding.header.apply{removeFromParent()?.addView(this, 0)}
                // Move items below our toolbar separator
                iMenuMain.iBinding.scrollViewItems.apply{removeFromParent()?.addView(this)}
                // Reverse menu items if needed
                if (wasToolbarsBottom) {
                    val children = iMenuMain.iBinding.layoutMenuItems.children.toList()
                    children.reversed().forEach { item -> item.removeFromParent()?.addView(item) }
                }

                // Set popup menus animations
                iMenuWebPage.animationStyle = R.style.AnimationMenu
                // Move popup menu toolbar to the top
                iMenuWebPage.iBinding.header.apply{removeFromParent()?.addView(this, 0)}
                // Move items below our toolbar separator
                iMenuWebPage.iBinding.scrollViewItems.apply{removeFromParent()?.addView(this)}
                // Reverse menu items if needed
                if (wasToolbarsBottom) {
                    val children = iMenuWebPage.iBinding.layoutMenuItems.children.toList()
                    children.reversed().forEach { item -> item.removeFromParent()?.addView(item) }
                }

                // Set search dropdown anchor to avoid gap
                searchView.dropDownAnchor = R.id.toolbar_include

                // Floating Action Buttons at the top
                iBinding.fabContainer.apply {setGravityTop(layoutParams as CoordinatorLayout.LayoutParams)}

                // FAB tab close button at the bottom
                iBinding.fabTabClose.apply {setGravityTop(layoutParams as LinearLayout.LayoutParams)}

                // ctrlTabBack at the bottom
                iBinding.fabBack.apply{removeFromParent()?.addView(this)}
            }
        }

        wasToolbarsBottom = configPrefs.toolbarsBottom
    }

    /**
     *
     */
    private fun setupToolBar() {
        // Check if our tool bar is long enough to display extra buttons
        val threshold = (iBindingToolbarContent.buttonActionBack.width?:3840)*10
        // If our tool bar is longer than 10 action buttons then we show extra buttons
        (iBinding.toolbarInclude.toolbar.width>threshold).let{
            iBindingToolbarContent.buttonActionBack.isVisible = it
            iBindingToolbarContent.buttonActionForward.isVisible = it
            // Hide tab bar action buttons if no room for them
            if (tabsView is TabsDesktopView) {
                (tabsView as TabsDesktopView).iBinding.actionButtons.isVisible = it
            }
        }

    }

    private fun initializePreferences() {

        // TODO layout transition causing memory leak
        //        iBinding.contentFrame.setLayoutTransition(new LayoutTransition());

        val currentSearchEngine = searchEngineProvider.provideSearchEngine()
        searchText = currentSearchEngine.queryUrl

        updateCookiePreference().subscribeOn(diskScheduler).subscribe()
        proxyUtils.updateProxySettings(this)
    }

    public override fun onWindowVisibleToUserAfterResume() {
        super.onWindowVisibleToUserAfterResume()
    }

    fun actionFocusTextField() {
        if (!isToolBarVisible()) {
            showActionBar()
        }
        searchView.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (searchView.hasFocus() == true) {
                searchView.let { searchTheWeb(it.text.toString()) }
            }
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyDownStartTime = System.currentTimeMillis()
            mainHandler.postDelayed(longPressBackRunnable, ViewConfiguration.getLongPressTimeout().toLong())
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mainHandler.removeCallbacks(longPressBackRunnable)
            if (System.currentTimeMillis() - keyDownStartTime > ViewConfiguration.getLongPressTimeout()) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    // For CTRL+TAB implementation
    var iRecentTabIndex = -1;
    var iCapturedRecentTabsIndices : Set<LightningView>? = null


    private fun copyRecentTabsList()
    {
        // Fetch snapshot of our recent tab list
        iCapturedRecentTabsIndices = tabsManager.iRecentTabs.toSet()
        iRecentTabIndex = iCapturedRecentTabsIndices?.size?.minus(1) ?: -1
        //logger.log(TAG, "Recent indices snapshot: iCapturedRecentTabsIndices")
    }

    /**
     * Initiate Ctrl + Tab session if one is not already started.
     */
    private fun tabSwitchStart()
    {
        if (iCapturedRecentTabsIndices==null)
        {
            copyRecentTabsList()
        }
    }

    /**
     * Reset ctrl + tab session if one was started.
     * Typically used when creating or deleting tabs.
     */
    private fun tabSwitchReset()
    {
        if (iCapturedRecentTabsIndices!=null)
        {
            copyRecentTabsList()
        }
    }

    /**
     * Stop ctrl + tab session.
     * Typically when the ctrl key is released.
     */
    private fun tabSwitchStop()
    {
        iCapturedRecentTabsIndices?.let {
            // Replace our recent tabs list by putting our captured one back in place making sure the selected tab is going back on top
            // See: https://github.com/Slion/Fulguris/issues/56
            tabsManager.iRecentTabs = it.toMutableSet()
            val tab = tabsManager.iRecentTabs.elementAt(iRecentTabIndex)
            tabsManager.iRecentTabs.remove(tab)
            tabsManager.iRecentTabs.add(tab)
        }

        iRecentTabIndex = -1;
        iCapturedRecentTabsIndices = null;
        //logger.log(TAG,"CTRL+TAB: Reset")
    }

    /**
     * Apply pending tab switch
     */
    private fun tabSwitchApply(aGoingBack: Boolean) {
        iCapturedRecentTabsIndices?.let {
            if (iRecentTabIndex >= 0) {
                // We worked out which tab to switch to, just do it now
                presenter.tabChanged(tabsManager.indexOfTab(it.elementAt(iRecentTabIndex)), false, aGoingBack)
                //mainHandler.postDelayed({presenter.tabChanged(tabsManager.indexOfTab(it.elementAt(iRecentTabIndex)))}, 300)
            }
        }
    }

    /**
     * Switch back to previous tab
     */
    private fun tabSwitchBack() {
        iCapturedRecentTabsIndices?.let {
            iRecentTabIndex--
            if (iRecentTabIndex<0) iRecentTabIndex=it.size-1
        }
    }

    /**
     * Switch forward to previous tab
     */
    private fun tabSwitchForward() {
        iCapturedRecentTabsIndices?.let {
            iRecentTabIndex++
            if (iRecentTabIndex >= it.size) iRecentTabIndex = 0
        }
    }

    /**
     * Manage our key events.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {

        if (event.action == KeyEvent.ACTION_UP && (event.keyCode==KeyEvent.KEYCODE_CTRL_LEFT||event.keyCode==KeyEvent.KEYCODE_CTRL_RIGHT)) {
            // Exiting CTRL+TAB mode
            tabSwitchStop()
        }

        // Keyboard shortcuts
        if (event.action == KeyEvent.ACTION_DOWN) {

            // Used this to debug control usage on emulator as both ctrl and alt just don't work on emulator
            //val isCtrlOnly  = if (Build.PRODUCT.contains("sdk")) { true } else KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON)
            val isCtrlOnly  = KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON)
            val isCtrlShiftOnly  = KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

            when (event.keyCode) {
                // Toggle status bar visibility
                KeyEvent.KEYCODE_F10 -> {
                    setFullscreen(!statusBarHidden, false)
                    return true
                }
                // Toggle tool bar visibility
                KeyEvent.KEYCODE_F11 -> {
                    toggleToolBar()
                    return true
                }
                // Reload current tab
                KeyEvent.KEYCODE_F5 -> {
                    // Refresh current tab
                    tabsManager.currentTab?.reload()
                    return true
                }

                // Shortcut to focus text field
                KeyEvent.KEYCODE_F6 -> {
                    actionFocusTextField()
                    return true
                }

                // Move forward if WebView has focus
                KeyEvent.KEYCODE_FORWARD -> {
                    if (tabsManager.currentTab?.webView?.hasFocus() == true && tabsManager.currentTab?.canGoForward() == true) {
                        currentTabGoForward()
                        return true
                    }
                }

                // This is actually being done in onBackPressed and doBackAction
                //KeyEvent.KEYCODE_BACK -> {
                //    if (tabsManager.currentTab?.webView?.hasFocus() == true && tabsManager.currentTab?.canGoBack() == true) {
                //        tabsManager.currentTab?.goBack()
                //        return true
                //    }
                //}
            }

            if (isCtrlOnly) {
                // Ctrl + tab number for direct tab access
                tabsManager.let {
                    if (KeyEvent.KEYCODE_0 <= event.keyCode && event.keyCode <= KeyEvent.KEYCODE_9) {
                        val nextIndex = if (event.keyCode > it.last() + KeyEvent.KEYCODE_1 || event.keyCode == KeyEvent.KEYCODE_0) {
                            // Go to the last tab for 0 or if not enough tabs
                            it.last()
                        } else {
                            // Otherwise access any of the first nine tabs
                            event.keyCode - KeyEvent.KEYCODE_1
                        }
                        presenter.tabChanged(nextIndex,false, false)
                        return true
                    }
                }
            }

            if (isCtrlShiftOnly) {
                // Ctrl + Shift + session number for direct session access
                tabsManager.let {
                    if (KeyEvent.KEYCODE_0 <= event.keyCode && event.keyCode <= KeyEvent.KEYCODE_9) {
                        val nextIndex = if (event.keyCode > it.iSessions.count() + KeyEvent.KEYCODE_1 || event.keyCode == KeyEvent.KEYCODE_0) {
                            // Go to the last session if not enough sessions or KEYCODE_0
                            it.iSessions.count()-1
                        } else {
                            // Otherwise access any of the first nine sessions
                            event.keyCode - KeyEvent.KEYCODE_1
                        }
                        presenter.switchToSession(it.iSessions[nextIndex].name)
                        return true
                    }
                }
            }

            // CTRL+TAB for tab cycling logic
            if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_TAB) {

                // Entering CTRL+TAB mode
                tabSwitchStart()

                iCapturedRecentTabsIndices?.let{

                    // Reversing can be done with those three modifiers notably to make it easier with two thumbs on F(x)tec Pro1
                    if (event.isShiftPressed or event.isAltPressed or event.isFunctionPressed) {
                        // Go forward one tab
                        tabSwitchForward()
                        tabSwitchApply(false)
                    } else {
                        // Go back one tab
                        tabSwitchBack()
                        tabSwitchApply(true)
                    }

                    //logger.log(TAG, "Switching to $iRecentTabIndex : $iCapturedRecentTabsIndices")



                }

                //logger.log(TAG,"Tab: down discarded")
                return true
            }

            when {
                isCtrlOnly -> when (event.keyCode) {
                    KeyEvent.KEYCODE_F -> {
                        // Search in page
                        findInPage()
                        return true
                    }
                    KeyEvent.KEYCODE_T -> {
                        // Open new tab
                        if (isIncognito()) {
                            presenter.newTab(
                                incognitoPageInitializer,
                                true
                            )
                        } else{
                            presenter.newTab(
                                homePageInitializer,
                                true
                            )
                        }
                        tabSwitchReset()
                        return true
                    }
                    KeyEvent.KEYCODE_W -> {
                        // Close current tab
                        tabsManager.let { presenter.deleteTab(it.indexOfCurrentTab()) }
                        tabSwitchReset()
                        return true
                    }
                    KeyEvent.KEYCODE_Q -> {
                        // Close browser
                        closeBrowser()
                        return true
                    }
                    // Mostly there because on F(x)tec Pro1 F5 switches off keyboard backlight
                    KeyEvent.KEYCODE_R -> {
                        // Refresh current tab
                        tabsManager.currentTab?.reload()
                        return true
                    }
                    // Show tab drawer displaying all pages
                    KeyEvent.KEYCODE_P -> {
                        toggleTabs()
                        return true
                    }
                    // Meritless shortcut matching Chrome's default
                    KeyEvent.KEYCODE_L -> {
                        actionFocusTextField()
                        return true
                    }
                    KeyEvent.KEYCODE_B -> {
                        executeAction(R.id.action_add_bookmark)
                        return true
                    }
                    // Text zoom in and out
                    // TODO: persist that setting per tab?
                    KeyEvent.KEYCODE_MINUS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = Math.max(settings.textZoom - 5, MIN_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                        return true
                    }
                    KeyEvent.KEYCODE_EQUALS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = Math.min(settings.textZoom + 5, MAX_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                        return true
                    }
                }

                isCtrlShiftOnly -> when (event.keyCode) {
                    KeyEvent.KEYCODE_T -> {
                        toggleTabs()
                        return true
                    }
                    KeyEvent.KEYCODE_S -> {
                        toggleSessions()
                        return true
                    }
                    KeyEvent.KEYCODE_B -> {
                        toggleBookmarks()
                        return true
                    }
                    // Text zoom in and out
                    // TODO: persist that setting per tab?
                    KeyEvent.KEYCODE_MINUS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = Math.max(settings.textZoom - 1, MIN_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                    }
                    KeyEvent.KEYCODE_EQUALS -> tabsManager.currentTab?.webView?.apply {
                        settings.textZoom = Math.min(settings.textZoom + 1, MAX_BROWSER_TEXT_SIZE)
                        application.toast(getText(R.string.size).toString() + ": " + settings.textZoom + "%")
                    }
                }

                event.keyCode == KeyEvent.KEYCODE_SEARCH -> {
                    // Highlight search field
                    searchView.requestFocus()
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Used to close empty tab after opening link.
     */
    fun closeCurrentTabIfEmpty() {
        // Had to delay that otherwise we could get there too early on the url still contains the download link
        // URL is later on reset to null by WebView internal mechanics.
        mainHandler.postDelayed({
            if ((currentTabView as? WebViewEx)?.url.isNullOrBlank()) {
                tabsManager.let { presenter.deleteTab(it.indexOfCurrentTab()) }
            }
        }, 500);
    }


    /**
     *
     */
    override fun executeAction(@IdRes id: Int): Boolean {

        val currentView = tabsManager.currentTab
        val currentUrl = currentView?.url

        when (id) {
            android.R.id.home -> {
                if (showingBookmarks()) {
                    closePanelBookmarks()
                }
                return true
            }
            R.id.action_back -> {
                if (currentView?.canGoBack() == true) {
                    currentTabGoBack()
                }
                return true
            }
            R.id.action_forward -> {
                if (currentView?.canGoForward() == true) {
                    currentTabGoForward()
                }
                return true
            }
            R.id.action_add_to_homescreen -> {
                if (currentView != null
                        && currentView.url.isNotBlank()
                        && !currentView.url.isSpecialUrl()) {
                    HistoryEntry(currentView.url, currentView.title).also {
                        Utils.createShortcut(this, it, currentView.favicon ?: webPageBitmap!!)
                        logger.log(TAG, "Creating shortcut: ${it.title} ${it.url}")
                    }
                }
                return true
            }
            R.id.action_new_tab -> {
                if (isIncognito()) {
                    presenter.newTab(
                        incognitoPageInitializer,
                        true
                    )
                } else {
                    presenter.newTab(
                        homePageInitializer,
                        true
                    )
                }
                return true
            }
            R.id.action_reload -> {
                if (searchView.hasFocus() == true) {
                    // SL: Not sure why?
                    searchView.setText("")
                } else {
                    refreshOrStop()
                }
                return true
            }
            R.id.action_incognito -> {
                startActivity(IncognitoActivity.createIntent(this))
                overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)
                return true
            }
            R.id.action_share -> {
                IntentUtils(this).shareUrl(currentUrl, currentView?.title)
                return true
            }
            R.id.action_bookmarks -> {
                openBookmarks()
                return true
            }
            R.id.action_exit -> {
                closeBrowser()
                return true
            }
            R.id.action_copy -> {
                if (currentUrl != null && !currentUrl.isSpecialUrl()) {
                    clipboardManager.copyToClipboard(currentUrl)
                    snackbar(R.string.message_link_copied)
                }
                return true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                // Was there just for testing it
                //onMaxTabReached()
                return true
            }
            R.id.action_history -> {
                openHistory()
                return true
            }
            R.id.action_downloads -> {
                openDownloads()
                return true
            }
            R.id.action_add_bookmark -> {
                if (currentUrl != null && !currentUrl.isSpecialUrl()) {
                    addBookmark(currentView.title, currentUrl)
                }
                return true
            }
            R.id.action_find -> {
                findInPage()
                return true
            }

            R.id.action_translate -> {
                // Get our local
                val locale = LocaleUtils.requestedLocale(userPreferences.locale)
                // For most languages Google just wants the two letters code
                // Using the full language tag such as fr-FR will actually prevent Google translate…
                // …to display the target language name even though the translation is actually working
                var languageCode = locale.language
                val languageTag = locale.toLanguageTag()
                // For chinese however, Google translate expects the full language tag
                if (languageCode == "zh") {
                    languageCode = languageTag
                }

                // TODO: Have a settings option to translate in new tab
                presenter.loadUrlInCurrentView("https://translate.google.com/translate?sl=auto&tl=$languageCode&u=$currentUrl")
                // TODO: support other translation providers?
                //presenter.loadUrlInCurrentView("https://www.translatetheweb.com/?from=&to=$locale&dl=$locale&a=$currentUrl")
                return true
            }

            R.id.action_print -> {
                (currentTabView as WebViewEx).print()
                return true
            }
            R.id.action_reading_mode -> {
                if (currentUrl != null) {
                    ReadingActivity.launch(this, currentUrl, false)
                }
                return true
            }
            R.id.action_restore_page -> {
                presenter.recoverClosedTab()
                return true
            }
            R.id.action_restore_all_pages -> {
                presenter.recoverAllClosedTabs()
                return true
            }

            R.id.action_close_all_tabs -> {
                // TODO: consider just closing all tabs
                // TODO: Confirmation dialog
                //closeBrowser()
                //presenter.closeAllTabs()
                presenter.closeAllOtherTabs()
                return true
            }

            R.id.action_show_homepage -> {
                if (userPreferences.homepageInNewTab) {
                    if (isIncognito()) {
                        presenter.newTab(incognitoPageInitializer, true)
                    } else {
                        presenter.newTab(homePageInitializer, true)
                    }
                } else {
                    // Why not through presenter We need some serious refactoring at some point
                    tabsManager.currentTab?.loadHomePage()
                }
                closePanels(null)
                return true
            }

            R.id.action_toggle_desktop_mode -> {
                tabsManager.currentTab?.apply {
                    toggleDesktopUserAgent()
                    reload()
                }
                return true
            }

            R.id.action_toggle_dark_mode -> {
                tabsManager.currentTab?.apply {
                    toggleDarkMode()
                    // Calling setToolbarColor directly from here causes that old bug with WebView not resizing when hiding toolbar and not showing newly loaded WebView to resurface.
                    // Even doing a post does not fix it. However doing a long enough postDelayed does the trick.
                    mainHandler.postDelayed({ setToolbarColor() }, 100)
                }
                return true
            }

            R.id.action_block -> {

                abpUserRules.allowPage(Uri.parse(tabsManager.currentTab?.url), !iMenuWebPage.iBinding.menuItemAdBlock.isChecked)
                tabsManager.currentTab?.reload()
                return true
            }

            R.id.action_sessions -> {
                // Show sessions menu
                showSessions()
                return true
            }

            else -> return false
        }
    }

    // Legacy from menu framework. Since we are using custom popup window as menu we don't need this anymore.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (executeAction(item.itemId)) true else super.onOptionsItemSelected(item)
    }


    // By using a manager, adds a bookmark and notifies third parties about that
    private fun addBookmark(title: String, url: String) {
        val bookmark = Bookmark.Entry(url, title, 0, Bookmark.Folder.Root)
        bookmarksDialogBuilder.showAddBookmarkDialog(this, this, bookmark)
    }

    private fun deleteBookmark(title: String, url: String) {
        bookmarkManager.deleteBookmark(Bookmark.Entry(url, title, 0, Bookmark.Folder.Root))
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { boolean ->
                    if (boolean) {
                        handleBookmarksChange()
                    }
                }
    }


    /**
     *
     */
    private fun findInPage()  {
        iBinding.findInPageInclude.searchQuery.let{
            it.doOnLayout {
                it.requestFocus()
                // Crazy workaround to get the virtual keyboard to show, Android FFS
                // See: https://stackoverflow.com/a/7784904/3969362
                mainHandler.postDelayed({
                    // Emulate tap to open up soft keyboard if needed
                    it.simulateTap()
                    // That will trigger our search, see addTextChangedListener
                    it.setText(tabsManager.currentTab?.searchQuery)
                    // Move cursor to the end of our text
                    it.setSelection(it.length())
                }, 100)
            }
        }

        iBinding.findInPageInclude.root.isVisible = true
    }



    private fun isLoading() : Boolean = tabsManager.currentTab?.let{it.progress < 100} ?: false

    /**
     * Enable or disable pull-to-refresh according to user preferences and state
     */
    private fun setupPullToRefresh(configuration: Configuration) {
        if (!configPrefs(configuration).pullToRefresh) {
            // User does not want to use pull to refresh
            iTabViewContainerFront.isEnabled = false
            iBindingToolbarContent.buttonReload.visibility = View.VISIBLE
            return
        }

        // Disable pull to refresh if no vertical scroll as it bugs with frame internal scroll
        // See: https://github.com/Slion/Lightning-Browser/projects/1
        iTabViewContainerFront.isEnabled = currentTabView?.canScrollVertically()?:false
        // Don't show reload button if pull-to-refresh is enabled and once we are not loading
        iBindingToolbarContent.buttonReload.visibility = if (iTabViewContainerFront.isEnabled && !isLoading()) View.GONE else View.VISIBLE
    }

    /**
     * Reset our tab bar if needed.
     * Notably used after configuration change.
     */
    private fun setupTabBar(configuration: Configuration): Boolean {
        // Check if our tab bar style changed
        if (verticalTabBar!=configPrefs(configuration).verticalTabBar
                || tabBarInDrawer!=configPrefs(configuration).tabBarInDrawer
                // Our bottom sheets dialog needs to be recreated with proper window decor state, with or without status bar that is.
                // Looks like that was also needed for the bottom sheets top padding to be in sync? Go figure…
                || userPreferences.useBottomSheets) {
            // We either coming or going to desktop like horizontal tab bar, tabs panel should be closed then
            mainHandler.post {closePanelTabs()}
            // Tab bar style changed recreate our tab bar then
            createTabsView(configuration)
            tabsView?.tabsInitialized()
            mainHandler.postDelayed({ scrollToCurrentTab() }, 1000)
            return true
        }

        return false
    }

    /**
     * Tells if web page color should be applied to tool and status bar
     */
    override fun isColorMode(): Boolean = userPreferences.colorModeEnabled

    override fun getTabModel(): TabsManager = tabsManager

    // TODO: That's not being used anymore
    override fun showCloseDialog(position: Int) {
        if (position < 0) {
            return
        }
        BrowserDialog.show(this, R.string.dialog_title_close_browser,
                DialogItem(title = R.string.close_tab) {
                    presenter.deleteTab(position)
                },
                DialogItem(title = R.string.close_other_tabs) {
                    presenter.closeAllOtherTabs()
                },
                DialogItem(title = R.string.close_all_tabs, onClick = this::closeBrowser))
    }

    /**
     * From [BrowserView].
     */
    override fun notifyTabViewRemoved(position: Int) {
        logger.log(TAG, "Notify Tab Removed: $position")
        tabsView?.tabRemoved(position)

        if (userPreferences.onTabCloseShowSnackbar) {
            // Notify user a tab was closed with an option to recover it
            makeSnackbar(
                    getString(R.string.notify_tab_closed), Snackbar.LENGTH_SHORT, if (configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                    .setAction(R.string.button_undo) {
                        presenter.recoverClosedTab()
                    }.show()
        }
    }

    /**
     * From [BrowserView].
     */
    override fun notifyTabViewAdded() {
        logger.log(TAG, "Notify Tab Added")
        tabsView?.tabAdded()
    }

    /**
     * From [BrowserView].
     */
    override fun notifyTabViewChanged(position: Int) {
        logger.log(TAG, "Notify Tab Changed: $position")
        tabsView?.tabChanged(position)
        setToolbarColor()
        setupPullToRefresh(resources.configuration)
    }

    /**
     * From [BrowserView].
     */
    override fun notifyTabViewInitialized() {
        logger.log(TAG, "Notify Tabs Initialized")
        tabsView?.tabsInitialized()
    }

    /**
     * TODO: Defined both in [BrowserView] and [UIController]
     * Sort out that mess.
     */
    override fun updateSslState(sslState: SslState) {
        iBindingToolbarContent.addressBarInclude.searchSslStatus.setImageDrawable(createSslDrawableForState(sslState))

        if (searchView.hasFocus() == false) {
            iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
        }
    }

    private fun ImageView.updateVisibilityForContent() {
        drawable?.let { visibility = VISIBLE } ?: run { visibility = GONE }
    }

    override fun tabChanged(tab: LightningView) {
        // SL: Is this being called way too many times?
        presenter.tabChangeOccurred(tab)
        // SL: Putting this here to update toolbar background color was a bad idea
        // That somehow freezes the WebView after switching between a few tabs on F(x)tec Pro1 at least (Android 9)
        //initializePreferences()
    }

    /**
     *
     */
    private fun setupToolBarButtons() {
        // Manage back and forward buttons state
        tabsManager.currentTab?.apply {
            iBindingToolbarContent.buttonActionBack.apply {
                isEnabled = canGoBack()
                // Since we set buttons color ourselves we need this to show it is disabled
                alpha = if (isEnabled) 1.0f else 0.25f
            }

            iBindingToolbarContent.buttonActionForward.apply {
                isEnabled = canGoForward()
                // Since we set buttons color ourselves we need this to show it is disabled
                alpha = if (isEnabled) 1.0f else 0.25f
            }
        }
    }

    /**
     *
     */
    fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) { // Vibrator availability checking
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(EFFECT_DOUBLE_CLICK))
                //
            } else*/ if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(50,50,50), intArrayOf(10,0,10), -1))
                //vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            else {
                vibrator.vibrate(200) // Vibrate method for below API Level 26
            }
        }
    }

    /**
     *
     */
    private fun swapTabViewsFrontToBack() {
        // Actually move our back frame below our front frame
        iTabViewContainerBack.removeFromParent()?.addView(iTabViewContainerBack,0)
    }


    var iSkipNextSearchQueryUpdate = false

    /**
     * From [BrowserView].
     * This function is central to browser tab switching.
     * It swaps our previous WebView with our new WebView.
     *
     * [aView] Input is in fact a [WebViewEx].
     */
    override fun setTabView(aView: View, aWasTabAdded: Boolean, aPreviousTabClosed: Boolean, aGoingBack: Boolean) {

        if (currentTabView == aView) {
            return
        }

        logger.log(TAG, "setTabView")

        aView.removeFromParent() // Just to be safe

        // Skip tab animation if user does not want it…
        val skipAnimation = !userPreferences.onTabChangeShowAnimation
                // …or if we already have a tab animation running
                || iTabAnimator!=null


        // If we have not swapped our views yet
        if (skipAnimation) {
            // Just perform our layout changes in our front view container then
            // We need to specify the layout params otherwise WebView fails in the strangest way.
            // In fact Web pages using popup and side menu will be broken as those elements background won't render, it'd look just transparent.
            // Issue could be reproduced on firebase console side menu and some BBC consent pop-up
            iTabViewContainerFront.addView(aView,0, MATCH_PARENT)
            currentTabView?.removeFromParent()
            iTabViewContainerFront.resetTarget() // Needed to make it work together with swipe to refresh
            aView.requestFocus()
        } else {
            // Prepare our back view container then
            iTabViewContainerBack.resetTarget() // Needed to make it work together with swipe to refresh
            // Same as above, make sure you specify layout params when adding you web view
            iTabViewContainerBack.addView(aView, MATCH_PARENT)
            aView.requestFocus()
        }

        // Remove existing focus change observer before we change our tab
        currentTabView?.onFocusChangeListener = null
        // Change our tab
        currentTabView = aView
        // Close virtual keyboard if we loose focus
        currentTabView.onFocusLost { inputMethodManager.hideSoftInputFromWindow(iBinding.uiLayout.windowToken, 0) }
        // Now everything is ready below our image snapshot of current view
        // Last perform our transitions
        if (!skipAnimation) {
            // Swap our variables but not the views yet
            val front = iTabViewContainerBack
            iTabViewContainerBack = iTabViewContainerFront
            iTabViewContainerFront = front
            //
            if (aWasTabAdded) {
                animateTabInScaleUp(iTabViewContainerFront)
            } else if (aPreviousTabClosed) {
                animateTabOutScaleDown(iTabViewContainerBack)
                if (userPreferences.onTabCloseVibrate) {
                    vibrate()
                }
            }
            else {
                //iBinding.imageBelow.isVisible = false // Won't be needed in this case
                if (aGoingBack) {
                    animateTabOutRight(iTabViewContainerBack)
                    animateTabInRight(iTabViewContainerFront)
                } else {
                    animateTabOutLeft(iTabViewContainerBack)
                    animateTabInLeft(iTabViewContainerFront)
                }
            }
        }
        showActionBar()
        // Make sure current tab is visible in tab list
        scrollToCurrentTab()
        //mainHandler.postDelayed({ scrollToCurrentTab() }, 0)

        // Current tab was already set by the time we get here
        tabsManager.currentTab?.let {
            // Update our find in page UI as needed
            iSkipNextSearchQueryUpdate = true // Make sure we don't redo a search as our UI text is changed
            iBinding.findInPageInclude.searchQuery.setText(it.searchQuery)
            // Set find in page UI visibility
            iBinding.findInPageInclude.root.isVisible = it.searchActive
        }
    }

    private val iTabAnimationDuration: Long = 300

    /**
     * That's intended to show the user a new tab was created
     */
    private fun animateTabInScaleUp(aTab: View?) {
        assertNull(iTabAnimator)
        aTab?.let{
            //iBinding.webViewFrame.addView(it, MATCH_PARENT)
            // Set our properties
            it.scaleX = 0f
            it.scaleY = 0f
            // Put our incoming frame on top
            // This replaces swapTabViewsFrontToBack for this special case
            it.removeFromParent()?.addView(it)
            // Animate it
            iTabAnimator = it.animate()
                    .scaleY(1f)
                    .scaleX(1f)
                    .setDuration(iTabAnimationDuration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            it.scaleX = 1f
                            it.scaleY = 1f
                            iTabViewContainerBack.findViewById<WebViewEx>(R.id.web_view)?.apply{
                                removeFromParent()
                                //destroyIfNeeded()
                            }
                            //
                            iTabAnimator = null;
                        }
                    })
        }
    }

    /**
     * Intended to show user a tab was closed.
     */
    private fun animateTabOutScaleDown(aTab: View?) {
        assertNull(iTabAnimator)
        aTab?.let{
            iTabAnimator = it.animate()
                    .scaleY(0f)
                    .scaleX(0f)
                    .setDuration(iTabAnimationDuration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // Time to swap our frames
                            swapTabViewsFrontToBack()
                            // Now do the clean-up
                            iTabViewContainerBack.findViewById<WebViewEx>(R.id.web_view)?.apply{
                                removeFromParent()
                                destroyIfNeeded()
                            }
                            // Reset our properties
                            it.scaleX = 1.0f
                            it.scaleY = 1.0f
                            //
                            iTabAnimator = null;
                        }
                    })
        }
    }


    /**
     * Intended to show user a tab was sent to the background.
     * Animate a tab that's being sent to the background.
     * Designed to work together with [animateTabInRight].
     */
    private fun animateTabOutRight(aTab: View?) {
        assertNull(iTabAnimator)
        aTab?.let{
                // Move our tab to a frame were we can animate it on top of our new foreground tab
            iTabAnimator = it.animate()
                        // Move our tab outside of the screen to the right
                        .translationX(it.width.toFloat())
                        .setDuration(iTabAnimationDuration)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                            // Put outgoing frame in the back
                            swapTabViewsFrontToBack()
                                // Animation is complete unhook that tab then
                            it.findViewById<WebViewEx>(R.id.web_view)?.apply{
                                removeFromParent()
                                //destroyIfNeeded()
                            }
                            // Reset our properties
                            it.translationX = 0f
                            //
                            iTabAnimator = null;
                            }
                        })
        }
    }

    /**
     * Intended to show user a tab was sent to the background.
     * Animate a tab that's being sent to the background.
     * Designed to work together with [animateTabInLeft].
     */
    private fun animateTabOutLeft(aTab: View?) {
        assertNull(iTabAnimator)
        aTab?.let{
            // Move our tab to a frame were we can animate it on top of our new foreground tab
            iTabAnimator = it.animate()
                    // Move our tab outside of the screen to the left
                    .translationX(-it.width.toFloat())
                    .setDuration(iTabAnimationDuration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // Put outgoing frame in the back
                            swapTabViewsFrontToBack()
                            // Animation is complete unhook that tab then
                            it.findViewById<WebViewEx>(R.id.web_view)?.apply{
                                removeFromParent()
                                //destroyIfNeeded()
                            }
                            // Reset our properties
                            it.translationX = 0f
                            //
                            iTabAnimator = null;
                        }
                    })
        }
    }

    /**
     * Animate an incoming tab from the left to the right.
     * Designed to work together with [animateTabOutRight].
     */
    private fun animateTabInRight(aTab: View?) {
        aTab?.let{
            it.translationX = -it.width.toFloat()
            // Move our tab to a frame were we can animate it on top of our new foreground tab
            it.animate()
                    // Move our tab outside of the screen to the right
                    .translationX(0f)
                    .setDuration(iTabAnimationDuration)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // Animation is complete
                            // Reset our properties
                            it.translationX = 0f
                        }
                    })
        }
    }

    /**
     * Animate an incoming tab from the right to the left.
     * Designed to work together with [animateTabOutLeft].
     */
    private fun animateTabInLeft(aTab: View?) {
        aTab?.let{
            // Initial tab position in offset to the right outside the screen
            it.translationX = it.width.toFloat()
            it.animate()
                // Move our tab to its default layout position on the screen
                .translationX(0f)
                .setDuration(iTabAnimationDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Animation is complete
                        // Reset our properties
                        it.translationX = 0f
                    }
                })
        }
    }


    /**
     * Used when going forward in tab history
     */
    private fun animateTabFlipLeft(aTab: View?) {
        assertNull(iTabAnimator)
        aTab?.let{
            // Adjust camera distance to avoid clipping
            val scale = resources.displayMetrics.density
            it.cameraDistance = it.width * scale * 2
            iTabAnimator = it.animate()
                    .rotationY(360f)
                    .setDuration(userPreferences.onTabBackAnimationDuration.toLong()*10)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            it.rotationY = 0f
                        //
                        iTabAnimator = null;
                        }
                    })
        }
    }

    /**
     * Used when going back in tab history
     */
    private fun animateTabFlipRight(aTab: View?) {
        assertNull(iTabAnimator)

        aTab?.let{
            // Adjust camera distance to avoid clipping
            val scale = resources.displayMetrics.density
            it.cameraDistance = it.width * scale * 2

            iTabAnimator = it.animate()
                    .rotationY(-360f)
                    .setDuration(userPreferences.onTabBackAnimationDuration.toLong()*10)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            it.rotationY = 0f
                        //
                        iTabAnimator = null;
                        }
                    })





        }
    }


    override fun showBlockedLocalFileDialog(onPositiveClick: Function0<Unit>) {
        MaterialAlertDialogBuilder(this)
                .setCancelable(true)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_blocked_local)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_open) { _, _ -> onPositiveClick.invoke() }
                .resizeAndShow()
    }

    override fun showSnackbar(@StringRes resource: Int) = snackbar(resource, if (configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)

    fun showSnackbar(aMessage: String) = snackbar(aMessage, if (configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)

    override fun tabCloseClicked(position: Int) {
        presenter.deleteTab(position)
    }

    override fun tabClicked(position: Int) {
        // Switch tab
        presenter.tabChanged(position,false, false)
        // Keep the drawer open while the tab change animation in running
        // Has the added advantage that closing of the drawer itself should be smoother as the webview had a bit of time to load
        mainHandler.postDelayed({ closePanels(null) }, 350)
    }

    // This is the callback from 'new tab' button on page drawer
    override fun newTabButtonClicked() {
        // First close drawer
        closePanels(null)
        // Then slightly delay page loading to give enough time for the drawer to close without stutter
        mainHandler.postDelayed({
            if (isIncognito()) {
                presenter.newTab(
                    incognitoPageInitializer,
                    true
                )
            } else {
                presenter.newTab(
                    homePageInitializer,
                    true
                )
            }
        }, 300)
    }

    override fun newTabButtonLongClicked() {
        presenter.recoverClosedTab()
    }

    override fun bookmarkButtonClicked() {
        val currentTab = tabsManager.currentTab
        val url = currentTab?.url
        val title = currentTab?.title
        if (url == null || title == null) {
            return
        }

        if (!url.isSpecialUrl()) {
            bookmarkManager.isBookmark(url)
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe { boolean ->
                        if (boolean) {
                            deleteBookmark(title, url)
                        } else {
                            addBookmark(title, url)
                        }
                    }
        }
    }

    override fun bookmarkItemClicked(entry: Bookmark.Entry) {
        if (userPreferences.bookmarkInNewTab) {
            presenter.newTab(UrlInitializer(entry.url), true)
        } else {
            presenter.loadUrlInCurrentView(entry.url)
        }
        // keep any jank from happening when the drawer is closed after the URL starts to load
        mainHandler.postDelayed({ closePanels(null) }, 150)
    }

    /**
     * Is that supposed to reload our history page if it changes?
     * Are we rebuilding our history page every time our history is changing?
     * Meaning every time we load a web page?
     * Thankfully not, apparently.
     */
    override fun handleHistoryChange() {
        historyPageFactory
                .buildPage()
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribeBy(onSuccess = { tabsManager.currentTab?.reload() })
    }

    protected fun handleNewIntent(intent: Intent) {
        presenter.onNewIntent(intent)
    }

    protected fun performExitCleanUp() {
        exitCleanup.cleanUp(tabsManager.currentTab?.webView, this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        logger.log(TAG, "onConfigurationChanged")

        setFullscreenIfNeeded(newConfig)
        setupTabBar(newConfig)
        setupToolBar(newConfig)
        setupBookmarksView()

        // Can't find a proper event to do that after the configuration changes were applied so we just delay it
        mainHandler.postDelayed({
            setupToolBar()
            setupPullToRefresh(newConfig)
            // Do we really need that?
            scrollToCurrentTab()
        }, 300)
        iMenuMain.dismiss() // As it wont update somehow
        iMenuWebPage.dismiss()
        // Make sure our drawers adjust accordingly
        iBinding.drawerLayout.requestLayout()
    }

    private fun initializeToolbarHeight(configuration: Configuration) =
            iBinding.uiLayout.doOnLayout {
                // TODO externalize the dimensions
                val toolbarSize = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    R.dimen.toolbar_height_portrait
                } else {
                    R.dimen.toolbar_height_landscape
                }

                iBinding.toolbarInclude.toolbar.layoutParams.height = dimen(toolbarSize)
                iBinding.toolbarInclude.toolbar.minimumHeight = toolbarSize
                iBinding.toolbarInclude.toolbar.requestLayout()
            }

    /**
     *
     */
    override fun closeBrowser() {
        currentTabView?.removeFromParent()

        performExitCleanUp()
        finishAndRemoveTask()
    }

    override fun onPause() {
        super.onPause()
        logger.log(TAG, "onPause")
        tabsManager.pauseAll()

        // Dismiss any popup menu
        iMenuMain.dismiss()
        iMenuWebPage.dismiss()
        iMenuSessions.dismiss()

        if (isIncognito() && isFinishing) {
            overridePendingTransition(R.anim.fade_in_scale, R.anim.slide_down_out)
        }
    }

    override fun onBackPressed() {
        doBackAction()
    }

    /**
     * Go back in current tab history.
     * Perform animation as specified in settings.
     */
    private fun currentTabGoBack() {
        tabsManager.currentTab?.let {
            it.goBack()
            // If no animation running yet…
            if (iTabAnimator==null &&
                    //…and user wants animation
                    userPreferences.onTabBackShowAnimation) {
                animateTabFlipRight(iTabViewContainerFront)
            }
        }
    }

    /**
     * Go forward in current tab history.
     * Perform animation as specified in settings.
     */
    private fun currentTabGoForward() {
        tabsManager.currentTab?.let {
            it.goForward()
            // If no animation running yet…
            if (iTabAnimator==null
                    //…and user wants animation
                    && userPreferences.onTabBackShowAnimation) {
                animateTabFlipLeft(iTabViewContainerFront)
            }
        }
    }

    /**
     *
     */
    private fun doBackAction() {
        val currentTab = tabsManager.currentTab
        if (iJustClosedMenu) {
            return
        }

        if (showingTabs()) {
            closePanelTabs()
        } else if (showingBookmarks()) {
            bookmarksView?.navigateBack()
        } else {
            if (currentTab != null) {
                logger.log(TAG, "onBackPressed")
                if (searchView.hasFocus()) {
                    currentTab.requestFocus()
                } else if (currentTab.canGoBack()) {
                    if (!currentTab.isShown) {
                        onHideCustomView()
                    } else {
                        if (isToolBarVisible()) {
                            currentTabGoBack()
                        } else {
                            showActionBar()
                        }
                    }
                } else {
                    if (customView != null || customViewCallback != null) {
                        onHideCustomView()
                    } else {
                        if (isToolBarVisible()) {
                            presenter.deleteTab(tabsManager.positionOf(currentTab))
                        } else {
                            showActionBar()
                        }
                    }
                }
            } else {
                logger.log(TAG, "This shouldn't happen ever")
                super.onBackPressed()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        proxyUtils.onStop()
    }

    /**
     * Amazingly this is not called when closing our app from Task list.
     * See: https://developer.android.com/reference/android/app/Activity.html#onDestroy()
     *
     * NOTE: Moreover when restarting this activity this is called after the onCreate of the new activity.
     */
    override fun onDestroy() {
        logger.log(TAG, "onDestroy")

        queue.cancelAll(TAG)

        incognitoNotification?.hide()

        mainHandler.removeCallbacksAndMessages(null)

        // Presenter and tab manager, which are tightly coupled, are owned by the application now.
        // Therefore we should not clean and destroy them here as they will survive that activity.
        // Instead we just unhook our tab.
        // Actually even that is a bad idea since it could already be in used by another instance of that activity.
        //removeTabView()
        // That would do, not strictly needed though
        currentTabView = null

        //
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        proxyUtils.onStart(this)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        tabsManager.shutdown()
    }

    /**
     *
     */
    override fun onResume() {
        super.onResume()
        logger.log(TAG, "onResume")
        // Check if some settings changes require application restart
        if (swapBookmarksAndTabs != userPreferences.bookmarksAndTabsSwapped
                || analytics != userPreferences.analytics
                || crashReport != userPreferences.crashReport
                || showCloseTabButton != userPreferences.showCloseTabButton
        ) {
            restart()
        }

        // Lock our drawers when not in use, I wonder if this logic should be applied elsewhere
        // TODO: Tab drawer should be locked when not in use, but not the bookmarks drawer
        // TODO: Consider !configPrefs.verticalTabBar and !configPrefs.tabBarInDrawer
        if (userPreferences.lockedDrawers
                // We need to lock our drawers when using bottom sheets
                // See: https://github.com/Slion/Fulguris/issues/192
                || userPreferences.useBottomSheets
        ) {
            lockDrawers()
        }
        else {
            unlockDrawers()
        }

        if (userPreferences.bookmarksChanged)
        {
            handleBookmarksChange()
            userPreferences.bookmarksChanged = false
        }

        if (userPreferences.incognito) {
            WebUtils.clearHistory(this, historyModel, databaseScheduler)
            WebUtils.clearCookies()
        }

        suggestionsAdapter?.let {
            it.refreshPreferences()
            it.refreshBookmarks()
        }
        tabsManager.resumeAll()
        initializePreferences()

        if (!setupTabBar(resources.configuration)) {
            // useBottomsheets settings could have changed
            addTabsViewToParent()
        }
        setupBookmarksView()
        setupToolBar(resources.configuration)
        mainHandler.postDelayed({ setupToolBar() }, 500)
        setupPullToRefresh(resources.configuration)

        // We think that's needed in case there was a rotation while in the background
        iBinding.drawerLayout.requestLayout()

        //intent?.let {logger.log(TAG, it.toString())}
    }

    /**
     * We need to make sure bookmarks are shown at the right place
     * Potentially moving them from the bottom sheets back to the drawers
     */
    private fun setupBookmarksView() {
        if (userPreferences.useBottomSheets) {
            createBookmarksDialog()
        } else {
            bookmarksView?.removeFromParent()
            getBookmarksContainer().addView(bookmarksView)
        }

    }


    /**
     * searches the web for the query fixing any and all problems with the input
     * checks if it is a search, url, etc.
     */
    private fun searchTheWeb(query: String) {
        val currentTab = tabsManager.currentTab
        if (query.isEmpty()) {
            return
        }
        val searchUrl = "$searchText$QUERY_PLACE_HOLDER"

        val (url, isSearch) = smartUrlFilter(query.trim(), true, searchUrl)

        if ((userPreferences.searchInNewTab && isSearch) or (userPreferences.urlInNewTab && !isSearch)) {
            // Create a new tab according to user preference
            // TODO: URI resolution should not be here really
            // That's also done in LightningView.loadURL
            when {
                url.isHomeUri() -> {
                    presenter.newTab(homePageInitializer, true)
                }
                url.isIncognitoUri() -> {
                    presenter.newTab(incognitoPageInitializer, true)
                }
                url.isBookmarkUri() -> {
                    presenter.newTab(bookmarkPageInitializer, true)
                }
                url.isHistoryUri() -> {
                    presenter.newTab(historyPageInitializer, true)
                }
                else -> {
                    presenter.newTab(UrlInitializer(url), true)
                }
            }
        }
        else if (currentTab != null) {
            // User don't want us the create a new tab
            currentTab.stopLoading()
            presenter.loadUrlInCurrentView(url)
        }
    }

    /**
     *
     */
    private fun setStatusBarColor(color: Int, darkIcons: Boolean) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // You don't want this as it somehow prevents smooth transition of tool bar when opening drawer
            //window.statusBarColor = R.color.transparent
        }
        backgroundDrawable.color = color
        window.setBackgroundDrawable(backgroundDrawable)
        // That if statement is preventing us to change the icons color while a drawer is showing
        // That's typically the case when user open a drawer before the HTML meta theme color was delivered

        //if (!tabsDialog.isShowing && !bookmarksDialog.isShowing)
        if (drawerClosing || !drawerOpened) // Do not update icons color if drawer is opened
        {
            // Make sure the status bar icons are still readable
            window.setStatusBarIconsColor(darkIcons && !userPreferences.useBlackStatusBar)
        }
    }

    /**
     * Apply given color and derivative to our toolbar, its components and status bar too.
     */
    private fun applyToolbarColor(color: Int) {
        //Workout a foreground color that will be working with our background color
        currentToolBarTextColor = foregroundColorFromBackgroundColor(color)
        // Change search view text color
        searchView.setTextColor(currentToolBarTextColor)
        searchView.setHintTextColor(DrawableUtils.mixColor(0.5f, currentToolBarTextColor, color))
        // Change tab counter color
        iBindingToolbarContent.tabsButton.apply {
            textColor = currentToolBarTextColor
            invalidate();
        }
        // Change tool bar home button color, needed when using desktop style tabs
        iBindingToolbarContent.homeButton.setColorFilter(currentToolBarTextColor)
        iBindingToolbarContent.buttonActionBack.setColorFilter(currentToolBarTextColor)
        iBindingToolbarContent.buttonActionForward.setColorFilter(currentToolBarTextColor)

        // Needed to delay that as otherwise disabled alpha state didn't get applied
        mainHandler.postDelayed({ setupToolBarButtons() }, 500)

        // Change reload icon color
        //setMenuItemColor(R.id.action_reload, currentToolBarTextColor)
        // SSL status icon color
        iBindingToolbarContent.addressBarInclude.searchSslStatus.setColorFilter(currentToolBarTextColor)
        // Toolbar buttons filter
        iBindingToolbarContent.buttonMore.setColorFilter(currentToolBarTextColor)
        iBindingToolbarContent.buttonReload.setColorFilter(currentToolBarTextColor)

        // Pull to refresh spinner color also follow current theme
        iTabViewContainerFront.setProgressBackgroundColorSchemeColor(color)
        iTabViewContainerFront.setColorSchemeColors(currentToolBarTextColor)

        // Color also applies to the following backgrounds as they show during tool bar show/hide animation
        iBinding.uiLayout.setBackgroundColor(color)
        iTabViewContainerFront.setBackgroundColor(color)
        // Somehow this was needed to make sure background colors are not swapped, most visible during page navigation animation
        // TODO: Investigate what's going on here, as that should not be the case and could lead to other issues
        iTabViewContainerBack.setBackgroundColor(color)

        val webViewEx: WebViewEx? = currentTabView as? WebViewEx

        webViewEx?.let {
            // Now also set WebView background color otherwise it is just white and we don't want that.
            // This one is going to be a problem as it will break some websites such as bbc.com.
            // Make sure we reset our background color after page load, thanks bbc.com and bbc.com/news for not defining background color.
            if (iBinding.toolbarInclude.progressView.progress >= 100
                    // Don't reset background color back to white on empty urls, that prevents displaying large empty white pages and blinding users in dark mode.
                    // When opening some download links a tab is spawned first with the download URL and later that URL is set back to null.
                    // Luckily our delayed call and the absence of invalidate prevents a flicker to white screen.
                    && !webViewEx.url.isNullOrBlank()) {
                // We delay that to avoid some web sites including default startup page to flash white on app startup
                mainHandler.removeCallbacks(resetBackgroundColorRunnable)
                resetBackgroundColorRunnable = Runnable {
                    webViewEx.setBackgroundColor(Color.WHITE)
                    // We do not want to apply that color on the spot though.
                    // It does not make sense anyway since it is a delayed call.
                    // It also still causes a flicker notably when a tab is spawned by a download link.
                    //webViewEx.invalidate()
                }
                mainHandler.postDelayed(resetBackgroundColorRunnable, 750);
            } else {
                mainHandler.removeCallbacks(resetBackgroundColorRunnable)
                webViewEx.setBackgroundColor(color)
                // Make sure that color is applied on the spot for earlier color change when loading tabs
                webViewEx.invalidate()
            }
        }

        // No animation for now
        // Toolbar background color
        iBinding.toolbarInclude.toolbarLayout.setBackgroundColor(color)
        iBinding.toolbarInclude.progressView.mProgressColor = color
        // Search text field color
        setSearchBarColors(color)

        // Progress bar background color
        DrawableUtils.mixColor(0.5f, color, Color.WHITE).let {
            // Set progress bar background color making sure it isn't too bright
            // That's notably making it more visible on lequipe.fr and bbc.com/sport
            // We hope this is going to work with most white themed website too
            if (ColorUtils.calculateLuminance(it)>0.75) {
                iBinding.toolbarInclude.progressView.setBackgroundColor(Color.BLACK)
            }
            else {
                iBinding.toolbarInclude.progressView.setBackgroundColor(it)
            }
        }

        // Then the color of the status bar itself
        setStatusBarColor(color, currentToolBarTextColor == Color.BLACK)

        // Remove that if ever we re-enable color animation below
        currentUiColor = color
        // Needed for current tab color update in desktop style tabs
        tabsView?.tabChanged(tabsManager.indexOfCurrentTab())

        /*
        // Define our color animation
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                val animatedColor = DrawableUtils.mixColor(interpolatedTime, currentUiColor, color)
                if (shouldShowTabsInDrawer) {
                    backgroundDrawable.color = animatedColor
                    mainHandler.post { window.setBackgroundDrawable(backgroundDrawable) }
                } else {
                    tabBackground?.tint(animatedColor)
                }
                currentUiColor = animatedColor
                toolbar_layout.setBackgroundColor(animatedColor)
                searchBackground?.background?.tint(
                        // Set search background a little lighter
                        // SL: See also Utils.mixTwoColors, why do we have those two functions?
                        getSearchBarColor(animatedColor)
                )
            }
        }
        animation.duration = 300
        toolbar_layout.startAnimation(animation)

         */

    }


    /**
     * Overrides [UIController.changeToolbarBackground]
     *
     * Animates the color of the toolbar from one color to another. Optionally animates
     * the color of the tab background, for use when the tabs are displayed on the top
     * of the screen.
     *
     * @param favicon the Bitmap to extract the color from
     * @param color HTML meta theme color. Color.TRANSPARENT if not available.
     * @param tabBackground the optional LinearLayout to color
     */
    override fun changeToolbarBackground(favicon: Bitmap?, color: Int, tabBackground: Drawable?) {

        val defaultColor = primaryColor

        if (!isColorMode()) {
            // Put back the theme color then
            applyToolbarColor(defaultColor);
        }
        else if (color != Color.TRANSPARENT
                // Do not apply meta color if forced dark mode
                && tabsManager.currentTab?.darkMode != true)
        {
            // We have a meta theme color specified in our page HTML, use it
            applyToolbarColor(color);
        }
        else if (favicon==null
                // Use default color if forced dark mode
                || tabsManager.currentTab?.darkMode == true)
        {
            // No HTML meta theme color and no favicon, use app theme color then
            applyToolbarColor(defaultColor);
        }
        else {
            Palette.from(favicon).generate { palette ->
                // OR with opaque black to remove transparency glitches
                val color = Color.BLACK or (palette?.getVibrantColor(defaultColor) ?: defaultColor)
                applyToolbarColor(color);
            }
        }
    }


    /**
     * Set our search bar color for focused and non focused state
     */
    private fun setSearchBarColors(aColor: Int) {
        iBindingToolbarContent.addressBarInclude.root.apply {
            val stateListDrawable = background as StateListDrawable
            // Order may matter depending of states declared in our background drawable
            // See: [R.drawable.card_bg_elevate]
            stateListDrawable.drawableForState(android.R.attr.state_focused).tint(ThemeUtils.getSearchBarFocusedColor(aColor))
            stateListDrawable.drawableForState(android.R.attr.state_enabled).tint(ThemeUtils.getSearchBarColor(aColor))
        }
    }




    @ColorInt
    override fun getUiColor(): Int = currentUiColor

    override fun updateUrl(url: String?, isLoading: Boolean) {
        if (url == null || searchView.hasFocus() != false) {
            return
        }
        val currentTab = tabsManager.currentTab
        bookmarksView?.handleUpdatedUrl(url)

        val currentTitle = currentTab?.title

        searchView.setText(searchBoxModel.getDisplayContent(url, currentTitle, isLoading))
    }

    override fun updateTabNumber(number: Int) {
        iBindingToolbarContent.tabsButton.updateCount(number)
    }

    override fun updateProgress(progress: Int) {
        setIsLoading(progress < 100)
        iBinding.toolbarInclude.progressView.progress = progress
    }

    protected fun addItemToHistory(title: String?, url: String) {
        if (url.isSpecialUrl()) {
            return
        }

        historyModel.visitHistoryEntry(url, title)
                .subscribeOn(databaseScheduler)
                .subscribe()
    }

    /**
     * method to generate search suggestions for the AutoCompleteTextView from
     * previously searched URLs
     */
    private fun initializeSearchSuggestions(getUrl: AutoCompleteTextView) {
        suggestionsAdapter = SuggestionsAdapter(this, isIncognito())
        suggestionsAdapter?.onSuggestionInsertClick = {
            if (it is SearchSuggestion) {
                getUrl.setText(it.title)
                getUrl.setSelection(it.title.length)
            } else {
                getUrl.setText(it.url)
                getUrl.setSelection(it.url.length)
            }
        }
        getUrl.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            doSearchSuggestionAction(getUrl, position)
        }
        getUrl.setAdapter(suggestionsAdapter)
    }


    private fun doSearchSuggestionAction(getUrl: AutoCompleteTextView, position: Int) {
        val url = when (val selection = suggestionsAdapter?.getItem(position) as WebPage) {
            is HistoryEntry,
            is Bookmark.Entry -> selection.url
            is SearchSuggestion -> selection.title
            else -> null
        } ?: return
        getUrl.setText(url)
        searchTheWeb(url)
        inputMethodManager.hideSoftInputFromWindow(getUrl.windowToken, 0)
        presenter.onAutoCompleteItemPressed()
    }

    /**
     * function that opens the HTML history page in the browser
     */
    private fun openHistory() {
        presenter.newTab(
                historyPageInitializer,
                true
        )
    }

    /**
     * Display downloads folder one way or another
     */
    private fun openDownloads() {
        startActivity(Utils.getIntentForDownloads(this, userPreferences.downloadDirectory))
        // Our built-in downloads list did not display downloaded items properly
        // Not sure why, consider fixing it or just removing it altogether at some point
        //presenter.newTab(downloadPageInitializer,true)
    }

    /**
     *
     */
    private fun showingBookmarks() : Boolean {
        return bookmarksDialog.isShowing || iBinding.drawerLayout.isDrawerOpen(getBookmarkDrawer())
    }

    /**
     *
     */
    private fun showingTabs() : Boolean {
        return tabsDialog.isShowing || iBinding.drawerLayout.isDrawerOpen(getTabDrawer())
    }


    /**
     * helper function that opens the bookmark drawer
     */
    private fun openBookmarks() {
        if (showingTabs()) {
            closePanelTabs()
        }

        if (userPreferences.useBottomSheets) {
            //createBookmarksDialog()
            bookmarksDialog.show()

            // See: https://github.com/material-components/material-components-android/issues/2165
            mainHandler.postDelayed({
                bookmarksDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }, 100)
        } else {
            // Define what to do once our drawer it opened
            //iBinding.drawerLayout.onceOnDrawerOpened {
            iBinding.drawerLayout.findViewById<RecyclerView>(R.id.list_bookmarks)?.apply {
                // Focus first item in our list
                findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
            //}
            // Open bookmarks drawer
            iBinding.drawerLayout.openDrawer(getBookmarkDrawer())
        }

        // Define what to do once our drawer it opened
        //iBinding.drawerLayout.onceOnDrawerOpened {
        bookmarksView?.iBinding?.listBookmarks?.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        //}
    }

    /**
     *
     */
    private fun toggleBookmarks() {
        if (showingBookmarks()) {
            closePanelBookmarks()
        } else {
            openBookmarks()
        }
    }



    /**
     * Open our tab list, works for both drawers and bottom sheets.
     */
    private fun openTabs() {

        // Defensive, don't show empty drawers when not in use
        if (!configPrefs.tabBarInDrawer) {
            return
        }

        if (showingBookmarks()) {
            closePanelBookmarks()
        }

        // Loose focus on current tab web page
        // Actually this was causing our search field to gain focus on HTC One M8 - Android 6
        // currentTabView?.clearFocus()
        // That's needed for focus issue when opening with tap on button
        val tabListView = (tabsView as ViewGroup).findViewById<RecyclerView>(R.id.tabs_list)
        tabListView?.requestFocus()

        if (userPreferences.useBottomSheets) {
            //createTabsDialog()
            tabsDialog.show()
            // See: https://github.com/material-components/material-components-android/issues/2165
            mainHandler.postDelayed({
                tabsDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }, 100)
        } else {
            // Open our tab list drawer
            iBinding.drawerLayout.openDrawer(getTabDrawer())
            //iBinding.drawerLayout.onceOnDrawerOpened {
            // Looks like we can do that without delays for drawers
            scrollToCurrentTab()
            //}

        }

    }

    /**
     * Scroll to current tab.
     */
    fun scrollToCurrentTab() {

        /*if (userPreferences.useBottomSheets && tabsView is TabsDrawerView && !(tabsDialog.isShowing && tabsDialog.behavior.state == BottomSheetBehavior.STATE_EXPANDED)) {
            return
        }*/

        val tabListView = (tabsView as ViewGroup).findViewById<RecyclerView>(R.id.tabs_list)
        // Set focus
        // Find our recycler list view
        tabListView?.apply {
            // Get current tab index and layout manager
            val index = tabsManager.indexOfCurrentTab()
            val lm = layoutManager as LinearLayoutManager
            // Check if current item is currently visible
            if (lm.findFirstCompletelyVisibleItemPosition() <= index && index <= lm.findLastCompletelyVisibleItemPosition()) {
                // We don't need to scroll as current item is already visible
                // Just focus our current item then for best keyboard navigation experience
                findViewHolderForAdapterPosition(tabsManager.indexOfCurrentTab())?.itemView?.requestFocus()
            } else {
                // Our current item is not completely visible, we need to scroll then
                // Once scroll is complete we will focus our current item
                onceOnScrollStateIdle { findViewHolderForAdapterPosition(tabsManager.indexOfCurrentTab())?.itemView?.requestFocus() }
                // Trigger scroll
                smoothScrollToPosition(index)
            }
        }
    }

    /**
     * Toggle tab list visibility
     */
    private fun toggleTabs() {
        if (showingTabs()) {
            closePanelTabs()
        } else {
            openTabs()
        }
    }

    /**
     * Toggle tab list visibility
     */
    private fun toggleSessions() {
        // isShowing always return false for some reason
        // Therefore toggle is not working however one can use Esc to close menu.
        // TODO: Fix that at some point
        if (iMenuSessions.isShowing) {
            iMenuSessions.dismiss()
        } else {
            showSessions()
        }
    }


    /**
     * This method closes any open drawer and executes the runnable after the drawers are closed.
     *
     * @param runnable an optional runnable to run after the drawers are closed.
     */
    protected fun closePanels(runnable: (() -> Unit)?) {
        closePanelTabs()
        closePanelBookmarks()
        //TODO: delay?
        // Yeah looks like delay are not needed we could even get rid of that functionality
        runnable?.invoke()
    }

    override fun setForwardButtonEnabled(enabled: Boolean) {
        iMenuMain.iBinding.menuShortcutForward.isEnabled = enabled
        iMenuWebPage.iBinding.menuShortcutForward.isEnabled = enabled
        tabsView?.setGoForwardEnabled(enabled)
    }

    override fun setBackButtonEnabled(enabled: Boolean) {
        iMenuMain.iBinding.menuShortcutBack.isEnabled = enabled
        iMenuWebPage.iBinding.menuShortcutBack.isEnabled = enabled
        tabsView?.setGoBackEnabled(enabled)
    }

    /**
     * opens a file chooser
     * param ValueCallback is the message from the WebView indicating a file chooser
     * should be opened
     */
    override fun openFileChooser(uploadMsg: ValueCallback<Uri>) {
        uploadMessageCallback = uploadMsg
        startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, getString(R.string.title_file_chooser)), FILE_CHOOSER_REQUEST_CODE)
    }


    /**
     * used to allow uploading into the browser
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                val result = if (intent == null || resultCode != Activity.RESULT_OK) {
                    null
                } else {
                    intent.data
                }

                uploadMessageCallback?.onReceiveValue(result)
                uploadMessageCallback = null
            } else {
                val results: Array<Uri>? = if (resultCode == Activity.RESULT_OK) {
                    if (intent == null) {
                        // If there is not data, then we may have taken a photo
                        cameraPhotoPath?.let { arrayOf(it.toUri()) }
                    } else {
                        intent.dataString?.let { arrayOf(it.toUri()) }
                    }
                } else {
                    null
                }

                filePathCallback?.onReceiveValue(results)
                filePathCallback = null
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent)
        }
    }

    /**
     * SL: This implementation is really strange.
     * It looks like that's being called from LightningChromeClient.onShowFileChooser.
     * My understanding is that this is a WebView callback from web forms asking user to pick a file.
     * So why do we create an image file in there? That does not make sense to me.
     */
    override fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>) {
        this.filePathCallback?.onReceiveValue(null)
        this.filePathCallback = filePathCallback

        // Create the File where the photo should go
        val intentArray: Array<Intent> = try {
            arrayOf(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra("PhotoPath", cameraPhotoPath)
                putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(Utils.createImageFile().also { file ->
                            cameraPhotoPath = "file:${file.absolutePath}"
                        })
                )
            })
        } catch (ex: IOException) {
            // Error occurred while creating the File
            logger.log(TAG, "Unable to create Image File", ex)
            emptyArray()
        }

        startActivityForResult(Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            })
            putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
        }, FILE_CHOOSER_REQUEST_CODE)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback, requestedOrientation: Int) {
        val currentTab = tabsManager.currentTab
        if (customView != null) {
            try {
                callback.onCustomViewHidden()
            } catch (e: Exception) {
                logger.log(TAG, "Error hiding custom view", e)
            }

            return
        }

        try {
            view.keepScreenOn = true
        } catch (e: SecurityException) {
            logger.log(TAG, "WebView is not allowed to keep the screen on")
        }

        originalOrientation = getRequestedOrientation()
        customViewCallback = callback
        customView = view

        setRequestedOrientation(requestedOrientation)
        val decorView = window.decorView as FrameLayout

        fullscreenContainerView = FrameLayout(this)
        fullscreenContainerView?.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
        if (view is FrameLayout) {
            val child = view.focusedChild
            if (child is VideoView) {
                videoView = child
                child.setOnErrorListener(VideoCompletionListener())
                child.setOnCompletionListener(VideoCompletionListener())
            }
        } else if (view is VideoView) {
            videoView = view
            view.setOnErrorListener(VideoCompletionListener())
            view.setOnCompletionListener(VideoCompletionListener())
        }
        decorView.addView(fullscreenContainerView, COVER_SCREEN_PARAMS)
        fullscreenContainerView?.addView(customView, COVER_SCREEN_PARAMS)
        decorView.requestLayout()
        setFullscreen(enabled = true, immersive = true)
        currentTab?.setVisibility(INVISIBLE)
    }

    override fun onHideCustomView() {
        val currentTab = tabsManager.currentTab
        if (customView == null || customViewCallback == null || currentTab == null) {
            if (customViewCallback != null) {
                try {
                    customViewCallback?.onCustomViewHidden()
                } catch (e: Exception) {
                    logger.log(TAG, "Error hiding custom view", e)
                }

                customViewCallback = null
            }
            return
        }
        logger.log(TAG, "onHideCustomView")
        currentTab.setVisibility(VISIBLE)
        currentTab.requestFocus()
        try {
            customView?.keepScreenOn = false
        } catch (e: SecurityException) {
            logger.log(TAG, "WebView is not allowed to keep the screen on")
        }

        setFullscreenIfNeeded(resources.configuration)
        if (fullscreenContainerView != null) {
            val parent = fullscreenContainerView?.parent as ViewGroup
            parent.removeView(fullscreenContainerView)
            fullscreenContainerView?.removeAllViews()
        }

        fullscreenContainerView = null
        customView = null

        logger.log(TAG, "VideoView is being stopped")
        videoView?.stopPlayback()
        videoView?.setOnErrorListener(null)
        videoView?.setOnCompletionListener(null)
        videoView = null

        try {
            customViewCallback?.onCustomViewHidden()
        } catch (e: Exception) {
            logger.log(TAG, "Error hiding custom view", e)
        }

        customViewCallback = null
        requestedOrientation = originalOrientation
    }

    private inner class VideoCompletionListener : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

        override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean = false

        override fun onCompletion(mp: MediaPlayer) = onHideCustomView()

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logger.log(TAG, "onWindowFocusChanged")
        if (hasFocus) {
            setFullscreen(hideStatusBar, isImmersiveMode)
        }
    }

    /**
     * TODO: Is that being used?
     */
    override fun onBackButtonPressed() {
        if (closeTabsPanelIfOpen()) {
            val currentTab = tabsManager.currentTab
            if (currentTab?.canGoBack() == true) {
                currentTabGoBack()
            } else if (currentTab != null) {
                tabsManager.let { presenter.deleteTab(it.positionOf(currentTab)) }
            }
        } else if (closeBookmarksPanelIfOpen()) {
            // Don't do anything other than close the bookmarks drawer when the activity is being
            // delegated to.
        }
    }

    override fun onForwardButtonPressed() {
        val currentTab = tabsManager.currentTab
        if (currentTab?.canGoForward() == true) {
            currentTabGoForward()
            closePanels(null)
        }
    }

    override fun onHomeButtonPressed() {
        executeAction(R.id.action_show_homepage)
    }


    private val fullScreenFlags = (SYSTEM_UI_FLAG_LAYOUT_STABLE
            or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or SYSTEM_UI_FLAG_FULLSCREEN
            or SYSTEM_UI_FLAG_IMMERSIVE_STICKY)


    /**
     * Hide the status bar according to orientation and user preferences
     */
    private fun setFullscreenIfNeeded(configuration: Configuration) {
        setFullscreen(configPrefs(configuration).hideStatusBar, false)
    }


    var statusBarHidden = false

    /**
     * This method sets whether or not the activity will display
     * in full-screen mode (i.e. the ActionBar will be hidden) and
     * whether or not immersive mode should be set. This is used to
     * set both parameters correctly as during a full-screen video,
     * both need to be set, but other-wise we leave it up to user
     * preference.
     *
     * @param enabled   true to enable full-screen, false otherwise
     * @param immersive true to enable immersive mode, false otherwise
     */
    private fun setFullscreen(enabled: Boolean, immersive: Boolean) {
        hideStatusBar = enabled
        isImmersiveMode = immersive
        val window = window
        val decor = window.decorView
        if (enabled) {
            if (immersive) {
                decor.systemUiVisibility = decor.systemUiVisibility or fullScreenFlags
            } else {
                decor.systemUiVisibility = decor.systemUiVisibility and fullScreenFlags.inv()
            }
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN)
            statusBarHidden = true
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decor.systemUiVisibility = decor.systemUiVisibility and fullScreenFlags.inv()
            statusBarHidden = false
        }

        // Keep our bottom sheets dialog in sync
        tabsDialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        tabsDialog.window?.setFlags(window.attributes.flags, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        bookmarksDialog.window?.decorView?.systemUiVisibility = window.decorView.systemUiVisibility
        bookmarksDialog.window?.setFlags(window.attributes.flags, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    /**
     * This method handles the JavaScript callback to create a new tab.
     * Basically this handles the event that JavaScript needs to create
     * a popup.
     *
     * @param resultMsg the transport message used to send the URL to
     * the newly created WebView.
     */
    override fun onCreateWindow(resultMsg: Message) {
        presenter.newTab(ResultMessageInitializer(resultMsg), true)
    }

    /**
     * Closes the specified [LightningView]. This implements
     * the JavaScript callback that asks the tab to close itself and
     * is especially helpful when a page creates a redirect and does
     * not need the tab to stay open any longer.
     *
     * @param tab the LightningView to close, delete it.
     */
    override fun onCloseWindow(tab: LightningView) {
        presenter.deleteTab(tabsManager.positionOf(tab))
    }

    /**
     * Hide the ActionBar if we are in full-screen
     */
    override fun hideActionBar() {
        if (isFullScreen) {
            doHideToolBar()
        }
    }

    /**
     * Display the ActionBar if it was hidden
     */
    override fun showActionBar() {
        logger.log(TAG, "showActionBar")
        iBinding.toolbarInclude.toolbarLayout.visibility = View.VISIBLE
    }

    private fun doHideToolBar() { iBinding.toolbarInclude.toolbarLayout.visibility = View.GONE }
    private fun isToolBarVisible() = iBinding.toolbarInclude.toolbarLayout.visibility == View.VISIBLE

    private fun toggleToolBar() : Boolean
    {
        return if (isToolBarVisible()) {
            doHideToolBar()
            currentTabView?.requestFocus()
            false
        } else {
            showActionBar()
            iBindingToolbarContent.buttonMore.requestFocus()
            true
        }
    }


    override fun handleBookmarksChange() {
        val currentTab = tabsManager.currentTab
        if (currentTab != null && currentTab.url.isBookmarkUrl()) {
            currentTab.loadBookmarkPage()
        }
        if (currentTab != null) {
            bookmarksView?.handleUpdatedUrl(currentTab.url)
        }
        suggestionsAdapter?.refreshBookmarks()
    }

    override fun handleDownloadDeleted() {
        val currentTab = tabsManager.currentTab
        if (currentTab != null && currentTab.url.isDownloadsUrl()) {
            currentTab.loadDownloadsPage()
        }
        if (currentTab != null) {
            bookmarksView?.handleUpdatedUrl(currentTab.url)
        }
    }

    override fun handleBookmarkDeleted(bookmark: Bookmark) {
        bookmarksView?.handleBookmarkDeleted(bookmark)
        handleBookmarksChange()
    }

    override fun handleNewTab(newTabType: LightningDialogBuilder.NewTab, url: String) {
        val urlInitializer = UrlInitializer(url)
        when (newTabType) {
            LightningDialogBuilder.NewTab.FOREGROUND -> presenter.newTab(urlInitializer, true)
            LightningDialogBuilder.NewTab.BACKGROUND -> presenter.newTab(urlInitializer, false)
            LightningDialogBuilder.NewTab.INCOGNITO -> {
                closePanels { }
                val intent = IncognitoActivity.createIntent(this, url.toUri())
                startActivity(intent)
                overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)
            }
        }
    }


    //var refreshButtonResId = R.drawable.ic_action_refresh

    /**
     * This method lets the search bar know that the page is currently loading
     * and that it should display the stop icon to indicate to the user that
     * pressing it stops the page from loading
     *
     *  TODO: Should we just have two buttons and manage their visibility?
     *  That should also animate the transition I guess.
     */
    private fun setIsLoading(isLoading: Boolean) {
        if (searchView.hasFocus() == false) {
            iBindingToolbarContent.addressBarInclude.searchSslStatus.updateVisibilityForContent()
        }

        // Set stop or reload icon according to current load status
        //setMenuItemIcon(R.id.action_reload, if (isLoading) R.drawable.ic_action_delete else R.drawable.ic_action_refresh)
        iBindingToolbarContent.buttonReload.setImageResource(if (isLoading) R.drawable.ic_action_delete else R.drawable.ic_action_refresh);

        // That fancy animation would be great but somehow it looks like it is causing issues making the button unresponsive.
        // I'm guessing it is conflicting with animations from layout change.
        // Animations on Android really are a pain in the ass, half baked crappy implementations.
        /*
        button_reload.let {
            val imageRes = if (isLoading) R.drawable.ic_action_delete else R.drawable.ic_action_refresh
            // Only change our image if needed otherwise we animate for nothing
            // Therefore first check if the selected image is already displayed
            if (refreshButtonResId != imageRes){
                refreshButtonResId = imageRes
                if (it.animation==null) {
                    val transition = AnimationUtils.createRotationTransitionAnimation(it, refreshButtonResId)
                    it.startAnimation(transition)
                }
                else{
                    button_reload.setImageResource(imageRes);
                }
            }
        }
         */

        setupPullToRefresh(resources.configuration)
    }


    /**
     * handle presses on the refresh icon in the search bar, if the page is
     * loading, stop the page, if it is done loading refresh the page.
     * See setIsFinishedLoading and setIsLoading for displaying the correct icon
     */
    private fun refreshOrStop() {
        val currentTab = tabsManager.currentTab
        if (currentTab != null) {
            if (currentTab.progress < 100) {
                currentTab.stopLoading()
            } else {
                currentTab.reload()
            }
        }
    }

    /**
     * Handle the click event for the views that are using
     * this class as a click listener. This method should
     * distinguish between the various views using their IDs.
     *
     * @param v the view that the user has clicked
     */
    override fun onClick(v: View) {
        val currentTab = tabsManager.currentTab ?: return
        when (v.id) {
            R.id.home_button -> currentTab.apply { requestFocus(); loadHomePage() }
            // When not using drawer for tabs that button is used to show webpage menu
            R.id.tabs_button -> if (configPrefs.tabBarInDrawer) openTabs() else {showMenuWebPage()}
            R.id.button_reload -> refreshOrStop()
            R.id.button_next -> currentTab.findNext()
            R.id.button_back -> currentTab.findPrevious()
            R.id.button_quit -> {
                currentTab.clearFind()
                iBinding.findInPageInclude.root.isVisible = false
                // Hide software keyboard
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(iBinding.findInPageInclude.searchQuery.windowToken, 0)
                //tabsManager.currentTab?.requestFocus()
            }
        }
    }

    /**
     * Handle the callback that permissions requested have been granted or not.
     * This method should act upon the results of the permissions request.
     *
     * @param requestCode  the request code sent when initially making the request
     * @param permissions  the array of the permissions that was requested
     * @param grantResults the results of the permissions requests that provides
     * information on whether the request was granted or not
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        PermissionsManager.getInstance().notifyPermissionsChange(permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * If the [drawer] is open, close it and return true. Return false otherwise.
     */
    private fun closeTabsPanelIfOpen(): Boolean =
            if (showingTabs()) {
                closePanelTabs()
                true
            } else {
                false
            }

    /**
     * If the [drawer] is open, close it and return true. Return false otherwise.
     */
    private fun closeBookmarksPanelIfOpen(): Boolean =
            if (showingBookmarks()) {
                closePanelBookmarks()
                true
            } else {
                false
            }


    /**
     * Welcome user after first installation.
     */
    private fun welcomeToFulguris() {
        MaterialAlertDialogBuilder(this)
                .setCancelable(true)
                .setTitle(R.string.title_welcome)
                .setMessage(R.string.message_welcome)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> val url = getString(R.string.url_app_home_page)
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    // Not sure that does anything
                    i.putExtra("SOURCE", "SELF")
                    startActivity(i)}
                .resizeAndShow()
    }


    /**
     * Notify user about application update.
     */
    private fun notifyVersionUpdate() {
        // TODO: Consider using snackbar instead to be less intrusive, make it a settings option?
        MaterialAlertDialogBuilder(this)
                .setCancelable(true)
                .setTitle(R.string.title_updated)
                .setMessage(getString(R.string.message_updated, BuildConfig.VERSION_NAME))
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> val url = getString(R.string.url_app_updates)
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    // Not sure that does anything
                    i.putExtra("SOURCE", "SELF")
                    startActivity(i)}
                .resizeAndShow()
    }


    /**
     * Check for update on slions.net.
     */
    private fun checkForUpdates() {
        val url = getString(R.string.slions_update_check_url)
        // Request a JSON object response from the provided URL.
        val request = object: JsonObjectRequest(Request.Method.GET, url, null,
                Response.Listener<JSONObject> { response ->

                    val latestVersion = response.getJSONArray("versions").getJSONObject(0).getString("version_string")
                    if (latestVersion != BuildConfig.VERSION_NAME) {
                        // We have an update available, tell our user about it
                        makeSnackbar(
                                getString(R.string.update_available) + " - v" + latestVersion, 5000, if (configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM) //Snackbar.LENGTH_LONG
                                .setAction(R.string.show, OnClickListener {
                                    val url = getString(R.string.url_app_home_page)
                                    val i = Intent(Intent.ACTION_VIEW)
                                    i.data = Uri.parse(url)
                                    // Not sure that does anything
                                    i.putExtra("SOURCE", "SELF")
                                    startActivity(i)
                                }).show()
                    }

                    //Log.d(TAG,response.toString())
                },
                Response.ErrorListener { error: VolleyError ->
                    // Just ignore error for background update check
                    // Use the following for network status code
                    // Though networkResponse can be null in flight mode for instance
                    // error.networkResponse.statusCode.toString()

                }
        ){
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params: MutableMap<String, String> = HashMap()
                // Provide here slions.net API key as part of this requests HTTP headers
                params["XF-Api-Key"] = getString(R.string.slions_api_key)
                return params
            }
        }

        request.tag = TAG
        // Add the request to the RequestQueue.
        queue.add(request)
    }

    var iLastTouchUpPosition: Point = Point()

    /**
     * TODO: Unused
     */
    override fun dispatchTouchEvent(anEvent: MotionEvent?): Boolean {

        when (anEvent?.action) {
            MotionEvent.ACTION_UP -> {
                iLastTouchUpPosition.x = anEvent.x.toInt()
                iLastTouchUpPosition.y = anEvent.y.toInt()

            }
        }
        return super.dispatchTouchEvent(anEvent)
    }

    /**
     * Implement [BrowserView.onMaxTabReached]
     */
    override fun onMaxTabReached() {
        // Show a message telling the user to contribute.
        // It provides a link to our settings Contribute section.
        makeSnackbar(
                getString(R.string.max_tabs), 10000, if (configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM) //Snackbar.LENGTH_LONG
                .setAction(R.string.show, OnClickListener {
                    // We want to launch our settings activity
                    val i = Intent(this, SettingsActivity::class.java)
                    /** See [SettingsActivity.onResume] for details of how this is handled on the other side */
                    // Tell our settings activity to load our Contrubite/Sponsorship fragment
                    i.putExtra(SETTINGS_CLASS_NAME, SponsorshipSettingsFragment::class.java.name)
                    startActivity(i)
                }).show()
    }

    /**
     * Implement [BrowserView.setAddressBarText]
     */
    override fun setAddressBarText(aText: String) {
        mainHandler.postDelayed({
            // Emulate tap to open up soft keyboard if needed
            searchView.simulateTap()
            searchView.setText(aText)
            searchView.selectAll()
            // Large one second delay to be safe otherwise we no-op or find the UI in a weird state
        }, 1000)
    }

    private fun stringContainsItemFromList(inputStr: String, items: Array<String>): Boolean {
        for (i in items.indices) {
            if (inputStr.contains(items[i])) {
                return true
            }
        }
        return false
    }

    /**
     * Show the page tools dialog.
     */
    @SuppressLint("CutPasteId")
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun showPageToolsDialog(position: Int) {
        if (position < 0) {
            return
        }
        val currentTab = tabsManager.currentTab ?: return
        val arrayOfURLs = userPreferences.javaScriptBlocked
        val strgs: Array<String> = if (arrayOfURLs.contains(", ")) {
            arrayOfURLs.split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else {
            arrayOfURLs.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        }
        val jsEnabledString = if (userPreferences.javaScriptChoice == JavaScriptChoice.BLACKLIST && !stringContainsItemFromList(currentTab.url, strgs) || userPreferences.javaScriptChoice == JavaScriptChoice.WHITELIST && stringContainsItemFromList(currentTab.url, strgs)) {
            R.string.allow_javascript
        } else{
            R.string.blocked_javascript
        }
        BrowserDialog.showWithIcons(this, this.getString(R.string.dialog_tools_title),
            /*
            DialogItem(
                icon = this.drawable(R.drawable.ic_baseline_code_24),
                title = R.string.page_source) {
                currentTab.webView?.evaluateJavascript("""(function() {
                        return "<html>" + document.getElementsByTagName('html')[0].innerHTML + "</html>";
                     })()""".trimMargin()) {
                    // Hacky workaround for weird WebView encoding bug
                    var name = it?.replace("\\u003C", "<")
                    name = name?.replace("\\n", System.getProperty("line.separator").toString())
                    name = name?.replace("\\t", "")
                    name = name?.replace("\\\"", "\"")
                    name = name?.substring(1, name.length - 1)

                    val builder = MaterialAlertDialogBuilder(this)
                    val inflater = this.layoutInflater
                    builder.setTitle(R.string.page_source)
                    val dialogLayout = inflater.inflate(R.layout.dialog_view_source, null)
                    val editText = dialogLayout.findViewById<CodeEditor>(R.id.dialog_multi_line)
                    editText.setText(name, 1)
                    builder.setView(dialogLayout)
                    builder.setNegativeButton(R.string.action_cancel) { _, _ -> }
                    builder.setPositiveButton(R.string.action_ok) { _, _ ->
                        editText.setText(editText.text?.toString()?.replace("\'", "\\\'"), 1)
                        currentTab.loadUrl("javascript:(function() { document.documentElement.innerHTML = '" + editText.text.toString() + "'; })()")
                    }
                    builder.show()
                }
            },*/
            DialogItem(
                icon= this.drawable(R.drawable.ic_script_add),
                title = R.string.inspect){
                val builder = MaterialAlertDialogBuilder(this)
                val inflater = this.layoutInflater
                builder.setTitle(R.string.inspect)
                val dialogLayout = inflater.inflate(R.layout.dialog_code_editor, null)
                val codeView: CodeView = dialogLayout.findViewById(R.id.dialog_multi_line)
                codeView.text.toString()
                builder.setView(dialogLayout)
                builder.setNegativeButton(R.string.action_cancel) { _, _ -> }
                builder.setPositiveButton(R.string.action_ok) { _, _ -> currentTab.loadUrl("javascript:(function() {" + codeView.text.toString() + "})()") }
                builder.show()
            },
            DialogItem(
                icon = this.drawable(R.drawable.outline_script_text_key_outline),
                colorTint = this.attrColor(R.attr.colorPrimary).takeIf { userPreferences.javaScriptChoice == JavaScriptChoice.BLACKLIST && !stringContainsItemFromList(currentTab.url, strgs) || userPreferences.javaScriptChoice == JavaScriptChoice.WHITELIST && stringContainsItemFromList(currentTab.url, strgs) },
                title = jsEnabledString) {
                val url = URL(currentTab.url)
                if (userPreferences.javaScriptChoice != JavaScriptChoice.NONE) {
                    if (!stringContainsItemFromList(currentTab.url, strgs)) {
                        if (userPreferences.javaScriptBlocked == "") {
                            userPreferences.javaScriptBlocked = url.host
                        } else {
                            userPreferences.javaScriptBlocked = userPreferences.javaScriptBlocked + ", " + url.host
                        }
                    } else {
                        if (!userPreferences.javaScriptBlocked.contains(", " + url.host)) {
                            userPreferences.javaScriptBlocked = userPreferences.javaScriptBlocked.replace(url.host, "")
                        } else {
                            userPreferences.javaScriptBlocked = userPreferences.javaScriptBlocked.replace(", " + url.host, "")
                        }
                    }
                } else {
                    userPreferences.javaScriptChoice = JavaScriptChoice.WHITELIST
                }
                tabsManager.currentTab?.reload()
                Handler(Looper.getMainLooper()).postDelayed({
                    tabsManager.currentTab?.reload()
                }, 250)
            },
            DialogItem(
                icon = this.drawable(R.drawable.cookie_outline),
                title = R.string.edit_cookies) {
                val cookieManager = CookieManager.getInstance()
                if (cookieManager.getCookie(currentTab.url) != null) {
                    val builder = MaterialAlertDialogBuilder(this)
                    val inflater = this.layoutInflater
                    builder.setTitle(R.string.site_cookies)
                    val dialogLayout = inflater.inflate(R.layout.dialog_code_editor, null)
                    val codeView: CodeView = dialogLayout.findViewById(R.id.dialog_multi_line)
                    codeView.setText(cookieManager.getCookie(currentTab.url))
                    builder.setView(dialogLayout)
                    builder.setNegativeButton(R.string.action_cancel) { _, _ -> }
                    builder.setPositiveButton(R.string.action_ok) { _, _ ->
                        val cookiesList = codeView.text.toString().split(";")
                        cookiesList.forEach { item ->
                            CookieManager.getInstance().setCookie(currentTab.url, item)
                        }
                    }
                    builder.show()
                }

            },
            DialogItem(
                icon = this.drawable(R.drawable.ic_tabs),
                title = R.string.close_tab) {
                presenter.deleteTab(position)
            },
            DialogItem(
                icon = this.drawable(R.drawable.ic_delete_forever),
                title = R.string.close_all_tabs) {
                presenter.closeAllOtherTabs()
            },
            DialogItem(
                icon = this.drawable(R.drawable.round_clear_24),
                title = R.string.exit, onClick = this::closeBrowser))
    }



    @RequiresApi(Build.VERSION_CODES.N)
    lateinit var iShortcuts: fulguris.keyboard.Shortcuts

    /**
     *
     */
    private fun createKeyboardShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            iShortcuts = fulguris.keyboard.Shortcuts(this)
        }
    }

    /**
     * Publish keyboard shortcuts so that user can see them when doing Meta+/?
     */
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup?>, menu: Menu?, deviceId: Int) {

        // Publish our shortcuts, could publish a different list based on current state too
        if (iShortcuts.iList.isNotEmpty()) {
                data.add(KeyboardShortcutGroup(getString(R.string.app_name), iShortcuts.iList))
        }

        super.onProvideKeyboardShortcuts(data, menu, deviceId)
    }

    companion object {

        private const val TAG = "BrowserActivity"

        const val INTENT_PANIC_TRIGGER = "info.guardianproject.panic.action.TRIGGER"

        private const val FILE_CHOOSER_REQUEST_CODE = 1111

        // Constant
        private val MATCH_PARENT = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        private val COVER_SCREEN_PARAMS = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    }

}

