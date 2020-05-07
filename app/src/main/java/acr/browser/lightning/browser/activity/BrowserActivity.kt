/*
 * Copyright 2015 Anthony Restaino
 */

package acr.browser.lightning.browser.activity

import acr.browser.lightning.AppTheme
import acr.browser.lightning.BuildConfig
import acr.browser.lightning.IncognitoActivity
import acr.browser.lightning.R
import acr.browser.lightning.browser.*
import acr.browser.lightning.browser.bookmarks.BookmarksDrawerView
import acr.browser.lightning.browser.cleanup.ExitCleanup
import acr.browser.lightning.browser.tabs.TabsDesktopView
import acr.browser.lightning.browser.tabs.TabsDrawerView
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.database.Bookmark
import acr.browser.lightning.database.HistoryEntry
import acr.browser.lightning.database.SearchSuggestion
import acr.browser.lightning.database.WebPage
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.database.history.HistoryRepository
import acr.browser.lightning.di.*
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.dialog.DialogItem
import acr.browser.lightning.dialog.LightningDialogBuilder
import acr.browser.lightning.extensions.*
import acr.browser.lightning.html.bookmark.BookmarkPageFactory
import acr.browser.lightning.html.history.HistoryPageFactory
import acr.browser.lightning.html.homepage.HomePageFactory
import acr.browser.lightning.icon.TabCountView
import acr.browser.lightning.log.Logger
import acr.browser.lightning.notifications.IncognitoNotification
import acr.browser.lightning.reading.activity.ReadingActivity
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.search.SuggestionsAdapter
import acr.browser.lightning.settings.NewTabPosition
import acr.browser.lightning.settings.activity.SettingsActivity
import acr.browser.lightning.ssl.SslState
import acr.browser.lightning.ssl.createSslDrawableForState
import acr.browser.lightning.ssl.showSslDialog
import acr.browser.lightning.utils.*
import acr.browser.lightning.view.*
import acr.browser.lightning.view.SearchView
import acr.browser.lightning.view.find.FindResults
import android.animation.LayoutTransition
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.MediaStore
import android.view.*
import android.view.View.*
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebView
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView.OnEditorActionListener
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.customview.widget.ViewDragHelper
import androidx.drawerlayout.widget.DrawerLayout
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import butterknife.ButterKnife
import com.anthonycr.grant.PermissionsManager
import io.reactivex.Completable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.browser_content.*
import kotlinx.android.synthetic.main.search.*
import kotlinx.android.synthetic.main.search_interface.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.android.synthetic.slions.toolbar_content.*
import java.io.IOException
import javax.inject.Inject
import kotlin.system.exitProcess

abstract class BrowserActivity : ThemableBrowserActivity(), BrowserView, UIController, OnClickListener {

    // Notifications
    lateinit var CHANNEL_ID: String

    // Toolbar Views
    private var searchBackground: View? = null
    private var searchView: SearchView? = null
    private var homeImageView: ImageView? = null
    private var tabCountView: TabCountView? = null

    // Current tab view being displayed
    private var currentTabView: View? = null

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
    public var isDarkTheme: Boolean = false
    private var isImmersiveMode = false
    private var shouldShowTabsInDrawer: Boolean = false
    private var swapBookmarksAndTabs: Boolean = false

    private var originalOrientation: Int = 0
    private var currentUiColor = Color.BLACK
    private var currentToolBarTextColor = Color.BLACK
    private var keyDownStartTime: Long = 0
    private var searchText: String? = null
    private var cameraPhotoPath: String? = null

    private var findResult: FindResults? = null

    // Flavors
    private val isFlavorSlions = BuildConfig.FLAVOR.contains("slions")

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
    @Inject lateinit var tabsManager: TabsManager
    @Inject lateinit var homePageFactory: HomePageFactory
    @Inject lateinit var bookmarkPageFactory: BookmarkPageFactory
    @Inject lateinit var historyPageFactory: HistoryPageFactory
    @Inject lateinit var historyPageInitializer: HistoryPageInitializer
    @Inject lateinit var downloadPageInitializer: DownloadPageInitializer
    @Inject lateinit var homePageInitializer: HomePageInitializer
    @Inject @field:MainHandler lateinit var mainHandler: Handler
    @Inject lateinit var proxyUtils: ProxyUtils
    @Inject lateinit var logger: Logger
    @Inject lateinit var bookmarksDialogBuilder: LightningDialogBuilder
    @Inject lateinit var exitCleanup: ExitCleanup

    // Image
    private var webPageBitmap: Bitmap? = null
    private val backgroundDrawable = ColorDrawable()
    private var incognitoNotification: IncognitoNotification? = null

    private var presenter: BrowserPresenter? = null
    private var tabsView: TabsView? = null
    private var bookmarksView: BookmarksView? = null

    // Menu
    private var backMenuItem: MenuItem? = null
    private var forwardMenuItem: MenuItem? = null
    private var popup: PopupMenu? = null

    private val longPressBackRunnable = Runnable {
        showCloseDialog(tabsManager.positionOf(tabsManager.currentTab))
    }

    /**
     * Determines if the current browser instance is in incognito mode or not.
     */
    protected abstract fun isIncognito(): Boolean

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
        //window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        //window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        super.onCreate(savedInstanceState)
        injector.inject(this)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

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

        presenter = BrowserPresenter(
            this,
            isIncognito(),
            userPreferences,
            tabsManager,
            mainScheduler,
            homePageFactory,
            bookmarkPageFactory,
            RecentTabModel(),
            logger
        )

        initialize(savedInstanceState)
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


    private fun initialize(savedInstanceState: Bundle?) {

        createNotificationChannel()

        //initializeToolbarHeight(resources.configuration)
        //setSupportActionBar(toolbar)
        layoutInflater.inflate(R.layout.toolbar_content,toolbar)
        //val actionBar = requireNotNull(supportActionBar)

        //TODO make sure dark theme flag gets set correctly
        isDarkTheme = userPreferences.useTheme != AppTheme.LIGHT || isIncognito()
        shouldShowTabsInDrawer = userPreferences.showTabsInDrawer
        swapBookmarksAndTabs = userPreferences.bookmarksAndTabsSwapped

        // initialize background ColorDrawable
        val primaryColor = ThemeUtils.getPrimaryColor(this)
        backgroundDrawable.color = primaryColor

        // Drawer stutters otherwise
        //left_drawer.setLayerType(LAYER_TYPE_NONE, null)
        //right_drawer.setLayerType(LAYER_TYPE_NONE, null)


        drawer_layout.addDrawerListener(DrawerLocker())

        webPageBitmap = drawable(R.drawable.ic_webpage).toBitmap()

        tabsView = if (shouldShowTabsInDrawer) {
            TabsDrawerView(this).also(findViewById<FrameLayout>(getTabsContainerId())::addView)
        } else {
            TabsDesktopView(this).also(findViewById<FrameLayout>(getTabsContainerId())::addView)
        }

        bookmarksView = BookmarksDrawerView(this).also(findViewById<FrameLayout>(getBookmarksContainerId())::addView)

        if (shouldShowTabsInDrawer) {
            tabs_toolbar_container.visibility = GONE
        }

        // Is that still needed
        val customView = toolbar
        customView.layoutParams = customView.layoutParams.apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }


        tabCountView = customView.findViewById(R.id.tab_count_view)
        homeImageView = customView.findViewById(R.id.home_image_view)
        if (shouldShowTabsInDrawer && !isIncognito()) {
            tabCountView?.visibility = VISIBLE
            homeImageView?.visibility = GONE
        } else if (shouldShowTabsInDrawer) {
            tabCountView?.visibility = GONE
            homeImageView?.visibility = VISIBLE
            homeImageView?.setImageResource(R.drawable.incognito_mode)
        } else {
            tabCountView?.visibility = GONE
            homeImageView?.visibility = VISIBLE
            homeImageView?.setImageResource(R.drawable.ic_action_home)
        }

        customView.findViewById<View>(R.id.home_button).setOnClickListener(this)

        // create the search EditText in the ToolBar
        searchView = customView.findViewById<SearchView>(R.id.search).apply {
            search_ssl_status.setOnClickListener {
                tabsManager.currentTab?.let { tab ->
                    tab.sslCertificate?.let { showSslDialog(it, tab.currentSslState()) }
                }
            }
            search_ssl_status.updateVisibilityForContent()
            setMenuItemIcon(R.id.action_reload, R.drawable.ic_action_refresh)
            //toolbar?.menu?.findItem(R.id.action_reload)?.let { it.icon = ContextCompat.getDrawable(this@BrowserActivity, R.drawable.ic_action_refresh) }

            val searchListener = SearchListenerClass()
            setOnKeyListener(searchListener)
            onFocusChangeListener = searchListener
            setOnEditorActionListener(searchListener)
            onPreFocusListener = searchListener
            addTextChangedListener(StyleRemovingTextWatcher())

            initializeSearchSuggestions(this)
        }


        searchBackground = customView.findViewById<View>(R.id.search_container).apply {
            // initialize search background color
            background.tint(getSearchBarColor(primaryColor))
        }

        drawer_layout.setDrawerShadow(R.drawable.drawer_right_shadow, GravityCompat.END)
        drawer_layout.setDrawerShadow(R.drawable.drawer_left_shadow, GravityCompat.START)

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
            presenter?.setupTabs(intent)
            setIntent(null)
            proxyUtils.checkForProxy(this)
        }

        if (isFlavorSlions) {
            //TODO: make this a settings option?
            // Drawers are full screen and locked for this flavor so as to avoid closing them when scrolling through tabs
            lockDrawers()
        }

        // Enable swipe to refresh
        content_frame.setOnRefreshListener {
            tabsManager.currentTab?.reload()
            mainHandler.postDelayed({content_frame.isRefreshing = false}, 1000)   // Stop the loading spinner after one second
        }

        // TODO: define custom transitions to make flying in and out of the tool bar nicer
        //ui_layout.layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, ui_layout.layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING))
        // Disabling animations which are not so nice
        ui_layout.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        ui_layout.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)


        // Create our custom popup overflow menu
        popup = PopupMenu(this, button_more)
        // Needed for the menu to pop-up on top of the button
        // See: https://stackoverflow.com/a/46557131/3969362
        popup?.gravity = Gravity.END;
        //Inflating the Popup using xml file
        popup?.menuInflater?.inflate(R.menu.main, popup?.menu)
        //registering popup with OnMenuItemClickListener
        popup?.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
            // Hook in our legacy handler
            this@BrowserActivity.onOptionsItemSelected(item)
        })

        button_more.setOnClickListener(OnClickListener {
            // Web page is loosing focus as we open our menu
            currentTabView?.clearFocus()
            // Check if virtual keyboard is showing
            if (inputMethodManager.isActive) {
                // Open our menu with a slight delay giving enough time for our virtual keyboard to close
                mainHandler.postDelayed({popup?.show()},100)
            } else {
                //Display our popup menu instantly
                popup?.show()
            }


        })
    }

    private fun getBookmarksContainerId(): Int = if (swapBookmarksAndTabs) {
        R.id.left_drawer
    } else {
        R.id.right_drawer
    }

    private fun getTabsContainerId(): Int = if (shouldShowTabsInDrawer) {
        if (swapBookmarksAndTabs) {
            R.id.right_drawer
        } else {
            R.id.left_drawer
        }
    } else {
        R.id.tabs_toolbar_container
    }

    private fun getBookmarkDrawer(): View = if (swapBookmarksAndTabs) {
        left_drawer
    } else {
        right_drawer
    }

    private fun getTabDrawer(): View = if (swapBookmarksAndTabs) {
        right_drawer
    } else {
        left_drawer
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
                    searchView?.let {
                        inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                        searchTheWeb(it.text.toString())
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
                searchView?.let {
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
            if (!hasFocus && currentView != null) {
                setIsLoading(currentView.progress < 100)
                updateUrl(currentView.url, false)
            } else if (hasFocus && currentView != null) {

                // Hack to make sure the text gets selected
                (v as SearchView).selectAll()
                search_ssl_status.visibility = GONE
                setMenuItemIcon(R.id.action_reload, R.drawable.ic_action_delete)
                //toolbar?.menu?.findItem(R.id.action_reload)?.let { it.icon = ContextCompat.getDrawable(this@BrowserActivity, R.drawable.ic_action_delete) }
            }

            if (!hasFocus) {
                search_ssl_status.updateVisibilityForContent()
                searchView?.let {
                    inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
                }
            }
        }

        override fun onPreFocus() {
            val currentView = tabsManager.currentTab ?: return
            val url = currentView.url
            if (!url.isSpecialUrl()) {
                if (searchView?.hasFocus() == false) {
                    searchView?.setText(url)
                }
            }
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

            currentTabView?.requestFocus()

            //TODO: make this a settings option?
            if (isFlavorSlions) return; // Drawers remain locked for that flavor
            val tabsDrawer = getTabDrawer()
            val bookmarksDrawer = getBookmarkDrawer()

            if (v === tabsDrawer) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, bookmarksDrawer)
            } else if (shouldShowTabsInDrawer) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, tabsDrawer)
            }

        }

        override fun onDrawerOpened(v: View) {

            drawerOpened = true
            drawerClosing = false
            drawerOpening = false

            //TODO: make this a settings option?
            if (isFlavorSlions) return; // Drawers remain locked for that flavor
            val tabsDrawer = getTabDrawer()
            val bookmarksDrawer = getBookmarkDrawer()

            if (v === tabsDrawer) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, bookmarksDrawer)
            } else {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, tabsDrawer)
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
                    setStatusBarIconsColor(!isDarkTheme)
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
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getTabDrawer())
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getBookmarkDrawer())
    }


    // Set tool bar color corresponding to the current tab
    private fun setToolbarColor()
    {
        val currentView = tabsManager.currentTab
        if (isColorMode() && currentView != null && currentView.htmlMetaThemeColor!=Color.TRANSPARENT) {
            // Web page does specify theme color, use it much like Google Chrome does
            mainHandler.post {changeToolbarBackground(currentView.htmlMetaThemeColor, null)}
        }
        else if (isColorMode() && currentView?.favicon != null) {
            // Web page as favicon, use it to extract page theme color
            changeToolbarBackground(currentView.favicon, Color.TRANSPARENT, null)
        } else {
            // That should be the primary color from current theme
            mainHandler.post {changeToolbarBackground(ThemeUtils.getPrimaryColor(this), null)}
        }
    }

    private fun initFullScreen(configuration: Configuration) {
        isFullScreen = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            userPreferences.hideToolBarInPortrait
        }
        else {
            userPreferences.hideToolBarInLandscape
        }
    }

    /**
     * Setup our tool bar as collapsible or always on according to orientation and user preferences
     */
    private fun setupToolBar(configuration: Configuration) {
        initFullScreen(configuration)
        initializeToolbarHeight(configuration)
        showActionBar()
        setToolbarColor()
        setFullscreenIfNeeded(resources.configuration)
    }

    private fun initializePreferences() {

        // TODO layout transition causing memory leak
        //        content_frame.setLayoutTransition(new LayoutTransition());

        val currentSearchEngine = searchEngineProvider.provideSearchEngine()
        searchText = currentSearchEngine.queryUrl

        updateCookiePreference().subscribeOn(diskScheduler).subscribe()
        proxyUtils.updateProxySettings(this)
    }

    public override fun onWindowVisibleToUserAfterResume() {
        super.onWindowVisibleToUserAfterResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (searchView?.hasFocus() == true) {
                searchView?.let { searchTheWeb(it.text.toString()) }
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {

        if (event.action == KeyEvent.ACTION_UP && (event.keyCode==KeyEvent.KEYCODE_CTRL_LEFT||event.keyCode==KeyEvent.KEYCODE_CTRL_RIGHT)) {
                // Exiting CTRL+TAB mode
                iRecentTabIndex = -1;
                iCapturedRecentTabsIndices = null;
                //logger.log(TAG,"CTRL+TAB: Reset")
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
                    setFullscreen(!statusBarHidden,false)
                    return true
                }
                // Toggle tool bar visibility
                KeyEvent.KEYCODE_F11 -> {
                    if (toggleToolBar()) {
                        // Highlight search field
                        searchView?.requestFocus()
                        searchView?.selectAll()
                    }
                    return true
                }
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
                        presenter?.tabChanged(nextIndex)
                        return true
                    }
                }
            }

            // CTRL+TAB for tab cycling logic
            if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_TAB) {

                tabsManager.let { it ->

                    if (iCapturedRecentTabsIndices==null)
                    {
                        // Entering CTRL+TAB mode
                        // Fetch snapshot of our recent tab list
                        iCapturedRecentTabsIndices = it.iRecentTabs.toSet()
                        iRecentTabIndex = iCapturedRecentTabsIndices?.size?.minus(1) ?: -1
                        //logger.log(TAG, "Recent indices snapshot: iCapturedRecentTabsIndices")
                    }

                    iCapturedRecentTabsIndices?.let{

                        // Reversing can be done with those three modifiers notably to make it easier with two thumbs on F(x)tec Pro1
                        if (event.isShiftPressed or event.isAltPressed or event.isFunctionPressed) {
                            // Go forward one tab
                            iRecentTabIndex++
                            if (iRecentTabIndex>=it.size) iRecentTabIndex=0

                        } else {
                            // Go back one tab
                            iRecentTabIndex--
                            if (iRecentTabIndex<0) iRecentTabIndex=iCapturedRecentTabsIndices?.size?.minus(1) ?: -1
                        }

                        //logger.log(TAG, "Switching to $iRecentTabIndex : $iCapturedRecentTabsIndices")

                        if (iRecentTabIndex >= 0) {
                            // We worked out which tab to switch to, just do it now
                            presenter?.tabChanged(tabsManager.indexOfTab(it.elementAt(iRecentTabIndex)))
                        }
                    }


                }

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
                        presenter?.newTab(
                            homePageInitializer,
                            true
                        )
                        return true
                    }
                    KeyEvent.KEYCODE_W -> {
                        // Close current tab
                        tabsManager.let { presenter?.deleteTab(it.indexOfCurrentTab()) }
                        return true
                    }
                    KeyEvent.KEYCODE_Q -> {
                        // Close browser
                        closeBrowser()
                        return true
                    }
                    KeyEvent.KEYCODE_R -> {
                        // Refresh current tab
                        tabsManager.currentTab?.reload()
                        return true
                    }
                    KeyEvent.KEYCODE_L -> {
                        toggleTabs()
                    }
                    KeyEvent.KEYCODE_B -> {
                        toggleBookmarks()
                    }
                }

                isCtrlShiftOnly -> when (event.keyCode) {
                    KeyEvent.KEYCODE_T -> {
                        toggleTabs()
                    }
                    KeyEvent.KEYCODE_B -> {
                        toggleBookmarks()
                    }
                }

                event.keyCode == KeyEvent.KEYCODE_SEARCH -> {
                    // Highlight search field
                    searchView?.requestFocus()
                    searchView?.selectAll()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val currentView = tabsManager.currentTab
        val currentUrl = currentView?.url
        // Handle action buttons
        when (item.itemId) {
            android.R.id.home -> {
                if (drawer_layout.isDrawerOpen(getBookmarkDrawer())) {
                    drawer_layout.closeDrawer(getBookmarkDrawer())
                }
                return true
            }
            R.id.action_back -> {
                if (currentView?.canGoBack() == true) {
                    currentView.goBack()
                }
                return true
            }
            R.id.action_forward -> {
                if (currentView?.canGoForward() == true) {
                    currentView.goForward()
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
                presenter?.newTab(homePageInitializer, true)
                return true
            }
            R.id.action_reload -> {
                if (searchView?.hasFocus() == true) {
                    // SL: Not sure why?
                    searchView?.setText("")
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
            R.id.action_copy -> {
                if (currentUrl != null && !currentUrl.isSpecialUrl()) {
                    clipboardManager.copyToClipboard(currentUrl)
                    snackbar(R.string.message_link_copied)
                }
                return true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
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
            R.id.action_reading_mode -> {
                if (currentUrl != null) {
                    ReadingActivity.launch(this, currentUrl)
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
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
     * method that shows a dialog asking what string the user wishes to search
     * for. It highlights the text entered.
     */
    private fun findInPage() = BrowserDialog.showEditText(
        this,
        R.string.action_find,
        R.string.search_hint,
        R.string.search_hint
    ) { text ->
        if (text.isNotEmpty()) {
            findResult = presenter?.findInPage(text)
            showFindInPageControls(text)
        }
    }

    private fun showFindInPageControls(text: String) {
        search_bar.visibility = VISIBLE

        findViewById<TextView>(R.id.search_query).text = resources.getString(R.string.search_in_page_query, text)
        findViewById<ImageButton>(R.id.button_next).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_back).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_quit).setOnClickListener(this)
    }

    fun setupPullToRefresh()
    {
        // Disable pull to refresh if no vertical scroll as it bugs with frame internal scroll
        // See: https://github.com/Slion/Lightning-Browser/projects/1
        content_frame.isEnabled = currentTabView?.canScrollVertically()?:false
    }

    /**
     * Tells if web page color should be applied to tool and status bar
     */
    override fun isColorMode(): Boolean = userPreferences.colorModeEnabled

    override fun getTabModel(): TabsManager = tabsManager

    override fun showCloseDialog(position: Int) {
        if (position < 0) {
            return
        }
        BrowserDialog.show(this, R.string.dialog_title_close_browser,
            DialogItem(title = R.string.close_tab) {
                presenter?.deleteTab(position)
            },
            DialogItem(title = R.string.close_other_tabs) {
                presenter?.closeAllOtherTabs()
            },
            DialogItem(title = R.string.close_all_tabs, onClick = this::closeBrowser))
    }

    override fun notifyTabViewRemoved(position: Int) {
        logger.log(TAG, "Notify Tab Removed: $position")
        tabsView?.tabRemoved(position)
    }

    override fun notifyTabViewAdded() {
        logger.log(TAG, "Notify Tab Added")
        tabsView?.tabAdded()
    }

    override fun notifyTabViewChanged(position: Int) {
        logger.log(TAG, "Notify Tab Changed: $position")
        tabsView?.tabChanged(position)
        setToolbarColor()
        setupPullToRefresh()
    }

    override fun notifyTabViewInitialized() {
        logger.log(TAG, "Notify Tabs Initialized")
        tabsView?.tabsInitialized()
    }

    override fun updateSslState(sslState: SslState) {
        search_ssl_status.setImageDrawable(createSslDrawableForState(sslState))

        if (searchView?.hasFocus() == false) {
            search_ssl_status.updateVisibilityForContent()
        }
    }

    private fun ImageView.updateVisibilityForContent() {
        drawable?.let { visibility = VISIBLE } ?: run { visibility = GONE }
    }

    override fun tabChanged(tab: LightningView) {
        // SL: Is this being called way too many times?
        presenter?.tabChangeOccurred(tab)
        // SL: Putting this here to update toolbar background color was a bad idea
        // That somehow freezes the WebView after switching between a few tabs on F(x)tec Pro1 at least (Android 9)
        //initializePreferences()
    }

    override fun removeTabView() {

        logger.log(TAG, "Remove the tab view")

        currentTabView.removeFromParent()
        currentTabView?.onFocusChangeListener = null
        currentTabView = null

        // Use a delayed handler to make the transition smooth
        // otherwise it will get caught up with the showTab code
        // and cause a janky motion
        mainHandler.postDelayed(drawer_layout::closeDrawers, 200)

    }

    override fun setTabView(view: View) {
        // SL: Hide any drawers first, thus making sure we close our tab drawer even when user taps current tab
        // Use a delayed handler to make the transition smooth
        // otherwise it will get caught up with the showTab code
        // and cause a janky motion
        mainHandler.postDelayed(drawer_layout::closeDrawers, 200)

        if (currentTabView == view) {
            return
        }

        logger.log(TAG, "Setting the tab view")
        view.removeFromParent()
        currentTabView.removeFromParent()
        content_frame.resetTarget() // Needed to make it work together with swipe to refresh
        content_frame.addView(view, 0, MATCH_PARENT)
        view.requestFocus()
        // Remove existing focus change observer before we change our tab
        currentTabView?.onFocusChangeListener = null
        // Change our tab
        currentTabView = view
        // Close virtual keyboard if we loose focus
        currentTabView.onFocusLost { inputMethodManager.hideSoftInputFromWindow(ui_layout.windowToken, 0) }
        showActionBar()
    }

    override fun showBlockedLocalFileDialog(onPositiveClick: Function0<Unit>) {
        AlertDialog.Builder(this)
            .setCancelable(true)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_blocked_local)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_open) { _, _ -> onPositiveClick.invoke() }
            .resizeAndShow()
    }

    override fun showSnackbar(@StringRes resource: Int) = snackbar(resource)

    override fun tabCloseClicked(position: Int) {
        presenter?.deleteTab(position)
    }

    override fun tabClicked(position: Int) {
        presenter?.tabChanged(position)
    }

    // This is the callback from 'new tab' button on page drawer
    override fun newTabButtonClicked() {
        // First close drawer
        closeDrawers(null)
        // Then slightly delay page loading to give enough time for the drawer to close without stutter
        mainHandler.postDelayed({presenter?.newTab(
            homePageInitializer,
            true
        )}, 300)
    }

    override fun newTabButtonLongClicked() {
        presenter?.onNewTabLongClicked()
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
        presenter?.loadUrlInCurrentView(entry.url)
        // keep any jank from happening when the drawer is closed after the URL starts to load
        mainHandler.postDelayed({ closeDrawers(null) }, 150)
    }

    override fun handleHistoryChange() {
        historyPageFactory
            .buildPage()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribeBy(onSuccess = { tabsManager.currentTab?.reload() })
    }

    protected fun handleNewIntent(intent: Intent) {
        presenter?.onNewIntent(intent)
    }

    protected fun performExitCleanUp() {
        exitCleanup.cleanUp(tabsManager.currentTab?.webView, this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        logger.log(TAG, "onConfigurationChanged")

        setFullscreenIfNeeded(newConfig)
        setupToolBar(newConfig)
        // Can't find a proper event to do that after the configuration changes were applied so we just delay it
        mainHandler.postDelayed({setupPullToRefresh()} ,300)
        popup?.dismiss() // As it wont update somehow
        // Make sure our drawers adjust accordingly
        drawer_layout.requestLayout()
    }

    private fun initializeToolbarHeight(configuration: Configuration) =
        ui_layout.doOnLayout {
            // TODO externalize the dimensions
            val toolbarSize = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                R.dimen.toolbar_height_portrait
            } else {
                R.dimen.toolbar_height_landscape
            }
            toolbar.layoutParams = (toolbar.layoutParams as ConstraintLayout.LayoutParams).apply {
                height = dimen(toolbarSize)
            }
            toolbar.minimumHeight = toolbarSize
            toolbar.requestLayout()
        }

    override fun closeBrowser() {
        currentTabView.removeFromParent()
        performExitCleanUp()
        finish()
    }

    override fun onPause() {
        super.onPause()
        logger.log(TAG, "onPause")
        tabsManager.pauseAll()

        if (isIncognito() && isFinishing) {
            overridePendingTransition(R.anim.fade_in_scale, R.anim.slide_down_out)
        }
    }

    override fun onBackPressed() {
        doBackAction()
    }

    private fun doBackAction() {
        val currentTab = tabsManager.currentTab
        if (drawer_layout.isDrawerOpen(getTabDrawer())) {
            drawer_layout.closeDrawer(getTabDrawer())
        } else if (drawer_layout.isDrawerOpen(getBookmarkDrawer())) {
            bookmarksView?.navigateBack()
        } else {
            if (currentTab != null) {
                logger.log(TAG, "onBackPressed")
                if (searchView?.hasFocus() == true) {
                    currentTab.requestFocus()
                } else if (currentTab.canGoBack()) {
                    if (!currentTab.isShown) {
                        onHideCustomView()
                    } else {
                        if (isToolBarVisible()) {
                            currentTab.goBack()
                        } else {
                            showActionBar()
                        }
                    }
                } else {
                    if (customView != null || customViewCallback != null) {
                        onHideCustomView()
                    } else {
                        if (isToolBarVisible()) {
                            presenter?.deleteTab(tabsManager.positionOf(currentTab))
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

    protected fun saveOpenTabs() {
        if (userPreferences.restoreLostTabsEnabled) {
            tabsManager.saveState()
        }
    }

    override fun onStop() {
        super.onStop()
        proxyUtils.onStop()
    }

    override fun onDestroy() {
        logger.log(TAG, "onDestroy")
        // Should we remove saveOpenTabs from MainActivity.onPause then?
        // Make sure we save our tabs before we are destroyed
        saveOpenTabs()

        incognitoNotification?.hide()

        mainHandler.removeCallbacksAndMessages(null)

        presenter?.shutdown()

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

    override fun onResume() {
        super.onResume()
        logger.log(TAG, "onResume")
        if (swapBookmarksAndTabs != userPreferences.bookmarksAndTabsSwapped) {
            restart()
        }

        if (userPreferences.bookmarksChanged)
        {
            handleBookmarksChange()
            userPreferences.bookmarksChanged = false
        }

        suggestionsAdapter?.let {
            it.refreshPreferences()
            it.refreshBookmarks()
        }
        tabsManager.resumeAll()
        initializePreferences()

        setupToolBar(resources.configuration)
        setupPullToRefresh()

        // We think that's needed in case there was a rotation while in the background
        drawer_layout.requestLayout()

        //intent?.let {logger.log(TAG, it.toString())}
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

        val (url,isSearch) = smartUrlFilter(query.trim(), true, searchUrl)

        if ((userPreferences.searchInNewTab && isSearch) or (userPreferences.urlInNewTab && !isSearch)) {
            // Create a new tab according to user preferences
            presenter?.newTab(UrlInitializer(url), true)
        }
        else if (currentTab != null) {
            // User don't want us the create a new tab
            currentTab.stopLoading()
            presenter?.loadUrlInCurrentView(url)
        }
    }


    private fun setStatusBarColor(color: Int, darkIcons: Boolean) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // You don't want this as it somehow prevents smooth transition of tool bar when opening drawer
            //window.statusBarColor = R.color.transparent
        }
        backgroundDrawable.color = color
        window.setBackgroundDrawable(backgroundDrawable)
        // That if statement is preventing us to change the icons color while a drawer is showing
        // That's typically the case when user open a drawer before the HTML meta theme color was delivered
        if (drawerClosing || !drawerOpened) // Do not update icons color if drawer is opened
        {
            // Make sure the status bar icons are still readable
            setStatusBarIconsColor(darkIcons)
        }
    }


    private fun setMenuItemIcon(id: Int, drawable: Int)
    {
        popup?.menu?.findItem(id)?.let {
            // We need to preserve the color filter before changing the icon
            val cf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                it.icon.colorFilter
            } else {
                // TODO maybe just move to API 21, or use compoundDrawables instead
                PorterDuffColorFilter(currentToolBarTextColor, PorterDuff.Mode.SRC_ATOP)
            }
            // Change our icon
            it.icon = ContextCompat.getDrawable(this, drawable)
            // Now reset our color filter as it was
            it.icon.colorFilter = cf
        }
    }


    private fun setMenuItemColor(id: Int, color: Int)
    {
        //toolbar.findViewById<ActionMenuItemView>(id)?.compoundDrawables?.get(0)?.colorFilter = PorterDuffColorFilter(color,PorterDuff.Mode.SRC_ATOP)
        //toolbar?.menu?.findItem(id)?.let { it.icon.colorFilter = PorterDuffColorFilter(color,PorterDuff.Mode.SRC_ATOP)  }
    }


    private fun changeToolbarBackground(color: Int, tabBackground: Drawable?) {

        //Workout a foreground colour that will be working with our background color
        currentToolBarTextColor = foregroundColorFromBackgroundColor(color)
        // Change search view text color
        searchView?.setTextColor(currentToolBarTextColor)
        searchView?.setHintTextColor(DrawableUtils.mixColor(0.5f, currentToolBarTextColor, color))
        // Change tab counter color
        tabCountView?.textColor = currentToolBarTextColor
        tabCountView?.invalidate();
        // Change reload icon color
        setMenuItemColor(R.id.action_reload, currentToolBarTextColor)
        // SSL status icon color
        search_ssl_status.setColorFilter(currentToolBarTextColor)
        // Toolbar buttons filter
        button_more.setColorFilter(currentToolBarTextColor)

        // Pull to refresh spinner color also follow current theme
        content_frame.setProgressBackgroundColorSchemeColor(color)
        content_frame.setColorSchemeColors(currentToolBarTextColor)

        // Color also applies to the following backgrounds as they show during tool bar show/hide animation
        ui_layout.setBackgroundColor(color)
        content_frame.setBackgroundColor(color)
        // This one is going to be a problem as it will break some websites such as bbc.com
        // Make sure we reset our background color after page load, thanks bbc.com and bbc.com/news for not defining background color
        currentTabView?.setBackgroundColor(if (progress_view.progress>=100) Color.WHITE else color)
        currentTabView?.invalidate()

        // No animation for now
        // Toolbar background color
        toolbar_layout.setBackgroundColor(color)
        progress_view.mProgressColor = color
        // Search text field color
        searchBackground?.background?.tint(getSearchBarColor(color))

        // Progress bar background color
        DrawableUtils.mixColor(0.5f, color, Color.WHITE).let {
            // Set progress bar background color making sure it isn't too bright
            // That's notably making it more visible on lequipe.fr and bbc.com/sport
            // We hope this is going to work with most white themed website too
            if (ColorUtils.calculateLuminance(it)>0.75) {
                progress_view.setBackgroundColor(Color.BLACK)
            }
            else {
                progress_view.setBackgroundColor(it)
            }
        }

        // Then the color of the status bar itself
        setStatusBarColor(color,currentToolBarTextColor==Color.BLACK)



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
     * Animates the color of the toolbar from one color to another. Optionally animates
     * the color of the tab background, for use when the tabs are displayed on the top
     * of the screen.
     *
     * @param favicon the Bitmap to extract the color from
     * @param color HTML meta theme color. Color.TRANSPARENT if not available.
     * @param tabBackground the optional LinearLayout to color
     */
    override fun changeToolbarBackground(favicon: Bitmap?, color: Int, tabBackground: Drawable?) {

        val defaultColor = ThemeUtils.getPrimaryColor(this)

        if (!isColorMode()) {
            // Put back the theme color then
            changeToolbarBackground(defaultColor,tabBackground);
        }
        else if (color != Color.TRANSPARENT)
        {
            // We have a meta theme color specified in our page HTML, use it
            changeToolbarBackground(color,tabBackground);
        }
        else if (favicon==null)
        {
            // No HTML meta theme color and no favicon, use app theme color then
            changeToolbarBackground(defaultColor,tabBackground);
        }
        else {
            Palette.from(favicon).generate { palette ->
                // OR with opaque black to remove transparency glitches
                val color = Color.BLACK or (palette?.getVibrantColor(defaultColor) ?: defaultColor)
                changeToolbarBackground(color, tabBackground);
            }
        }

    }

    private fun getSearchBarColor(requestedColor: Int): Int =
            DrawableUtils.mixColor(0.25f, requestedColor, Color.WHITE)


    @ColorInt
    override fun getUiColor(): Int = currentUiColor

    override fun updateUrl(url: String?, isLoading: Boolean) {
        if (url == null || searchView?.hasFocus() != false) {
            return
        }
        val currentTab = tabsManager.currentTab
        bookmarksView?.handleUpdatedUrl(url)

        val currentTitle = currentTab?.title

        searchView?.setText(searchBoxModel.getDisplayContent(url, currentTitle, isLoading))
    }

    override fun updateTabNumber(number: Int) {
        if (shouldShowTabsInDrawer && !isIncognito()) {
            tabCountView?.updateCount(number)
        }
    }

    override fun updateProgress(progress: Int) {
        setIsLoading(progress < 100)
        progress_view.progress = progress
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
            val url = when (val selection = suggestionsAdapter?.getItem(position) as WebPage) {
                is HistoryEntry,
                is Bookmark.Entry -> selection.url
                is SearchSuggestion -> selection.title
                else -> null
            } ?: return@OnItemClickListener
            getUrl.setText(url)
            searchTheWeb(url)
            inputMethodManager.hideSoftInputFromWindow(getUrl.windowToken, 0)
            presenter?.onAutoCompleteItemPressed()
        }
        getUrl.setAdapter(suggestionsAdapter)
    }

    /**
     * function that opens the HTML history page in the browser
     */
    private fun openHistory() {
        presenter?.newTab(
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
        //presenter?.newTab(downloadPageInitializer,true)
    }

    private fun showingBookmarks() = drawer_layout.isDrawerOpen(getBookmarkDrawer())
    private fun showingTabs() = drawer_layout.isDrawerOpen(getTabDrawer())

    /**
     * helper function that opens the bookmark drawer
     */
    private fun openBookmarks() {
        if (showingTabs()) {
            drawer_layout.closeDrawers()
        }
        drawer_layout.openDrawer(getBookmarkDrawer())
    }

    /**
     *
     */
    private fun toggleBookmarks() {
        if (showingBookmarks()) {
            drawer_layout.closeDrawers()
        } else {
            openBookmarks()
        }
    }

    /**
     * Open our tab list drawer
     */
    private fun openTabs() {
        if (showingBookmarks()) {
            drawer_layout.closeDrawers()
        }

        // Loose focus on current tab web page
        currentTabView?.clearFocus()

        // Define what to do once our list drawer it opened
        // Item focus won't work sometimes when not using keyboard, I'm guessing that's somehow a feature
        drawer_layout.onceOnDrawerOpened {
            // Set focus
            // Find our recycler list view
            drawer_layout.findViewById<RecyclerView>(R.id.tabs_list)?.apply {
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

        // Open our tab list drawer
        drawer_layout.openDrawer(getTabDrawer())
    }

    /**
     *
     */
    private fun toggleTabs() {
        if (showingTabs()) {
            drawer_layout.closeDrawers()
        } else {
            openTabs()
        }
    }







    /**
     * This method closes any open drawer and executes the runnable after the drawers are closed.
     *
     * @param runnable an optional runnable to run after the drawers are closed.
     */
    protected fun closeDrawers(runnable: (() -> Unit)?) {
        if (!drawer_layout.isDrawerOpen(left_drawer) && !drawer_layout.isDrawerOpen(right_drawer)) {
            if (runnable != null) {
                runnable()
                return
            }
        }
        drawer_layout.closeDrawers()

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit

            override fun onDrawerOpened(drawerView: View) = Unit

            override fun onDrawerClosed(drawerView: View) {
                runnable?.invoke()
                drawer_layout.removeDrawerListener(this)
            }

            override fun onDrawerStateChanged(newState: Int) = Unit
        })
    }

    override fun setForwardButtonEnabled(enabled: Boolean) {
        forwardMenuItem?.isEnabled = enabled
        tabsView?.setGoForwardEnabled(enabled)
    }

    override fun setBackButtonEnabled(enabled: Boolean) {
        backMenuItem?.isEnabled = enabled
        tabsView?.setGoBackEnabled(enabled)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        backMenuItem = menu.findItem(R.id.action_back)
        forwardMenuItem = menu.findItem(R.id.action_forward)
        return super.onCreateOptionsMenu(menu)
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

    override fun onBackButtonPressed() {
        if (drawer_layout.closeDrawerIfOpen(getTabDrawer())) {
            val currentTab = tabsManager.currentTab
            if (currentTab?.canGoBack() == true) {
                currentTab.goBack()
            } else if (currentTab != null) {
                tabsManager.let { presenter?.deleteTab(it.positionOf(currentTab)) }
            }
        } else if (drawer_layout.closeDrawerIfOpen(getBookmarkDrawer())) {
            // Don't do anything other than close the bookmarks drawer when the activity is being
            // delegated to.
        }
    }

    override fun onForwardButtonPressed() {
        val currentTab = tabsManager.currentTab
        if (currentTab?.canGoForward() == true) {
            currentTab.goForward()
            closeDrawers(null)
        }
    }

    override fun onHomeButtonPressed() {
        tabsManager.currentTab?.loadHomePage()
        closeDrawers(null)
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
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setFullscreen( userPreferences.hideStatusBarInPortrait, false)
        }
        else {
            setFullscreen( userPreferences.hideStatusBarInLandscape, false)
        }
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
        presenter?.newTab(ResultMessageInitializer(resultMsg), true)
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
        presenter?.deleteTab(tabsManager.positionOf(tab))
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

        if (toolbar_layout.parent==null)
        {
            ui_layout.addView(toolbar_layout,0)
        }
    }

    private fun doHideToolBar() = ui_layout.removeView(toolbar_layout)

    private fun isToolBarVisible() = toolbar_layout.parent!=null

    private fun toggleToolBar() : Boolean
    {
        return if (isToolBarVisible()) {
            doHideToolBar()
            false
        } else {
            showActionBar()
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
            LightningDialogBuilder.NewTab.FOREGROUND -> presenter?.newTab(urlInitializer, true)
            LightningDialogBuilder.NewTab.BACKGROUND -> presenter?.newTab(urlInitializer, false)
            LightningDialogBuilder.NewTab.INCOGNITO -> {
                drawer_layout.closeDrawers()
                val intent = IncognitoActivity.createIntent(this, url.toUri())
                startActivity(intent)
                overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)
            }
        }
    }

    /**
     * This method lets the search bar know that the page is currently loading
     * and that it should display the stop icon to indicate to the user that
     * pressing it stops the page from loading
     */
    private fun setIsLoading(isLoading: Boolean) {
        if (searchView?.hasFocus() == false) {
            search_ssl_status.updateVisibilityForContent()

            setMenuItemIcon(R.id.action_reload, if (isLoading) R.drawable.ic_action_delete else R.drawable.ic_action_refresh)

        }
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
            R.id.home_button -> when {
                searchView?.hasFocus() == true -> currentTab.requestFocus()
                shouldShowTabsInDrawer -> openTabs()
                else -> currentTab.loadHomePage()
            }
            R.id.button_next -> findResult?.nextResult()
            R.id.button_back -> findResult?.previousResult()
            R.id.button_quit -> {
                findResult?.clearResults()
                findResult = null
                search_bar.visibility = GONE
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
    private fun DrawerLayout.closeDrawerIfOpen(drawer: View): Boolean =
        if (isDrawerOpen(drawer)) {
            closeDrawer(drawer)
            true
        } else {
            false
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
