/*
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright 2014 A.C.R. Development
 */

package acr.browser.lightning.view

import acr.browser.lightning.Capabilities
import acr.browser.lightning.R
import acr.browser.lightning.ThemedActivity
import acr.browser.lightning.browser.TabModel
import acr.browser.lightning.constant.*
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.di.*
import acr.browser.lightning.dialog.LightningDialogBuilder
import acr.browser.lightning.download.LightningDownloadListener
import acr.browser.lightning.extensions.*
import acr.browser.lightning.isSupported
import acr.browser.lightning.log.Logger
import acr.browser.lightning.network.NetworkConnectivityModel
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.settings.preferences.userAgent
import acr.browser.lightning.settings.fragment.DisplaySettingsFragment.Companion.MIN_BROWSER_TEXT_SIZE
import acr.browser.lightning.settings.preferences.webViewEngineVersionDesktop
import acr.browser.lightning.ssl.SslState
import acr.browser.lightning.utils.*
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.IntentFilter
import android.graphics.*
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.OnScrollChangeListener
import android.view.View.OnTouchListener
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebSettings.LayoutAlgorithm
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.collection.ArrayMap
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.EntryPointAccessors
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.lang.ref.WeakReference

/**
 * [LightningView] acts as a tab for the browser, handling WebView creation and handling logic, as
 * well as properly initialing it. All interactions with the WebView should be made through this
 * class.
 */
class LightningView(
    private val activity: Activity,
    tabInitializer: TabInitializer,
    val isIncognito: Boolean,
    // TODO: Could we remove those?
    private val homePageInitializer: HomePageInitializer,
    private val incognitoPageInitializer: IncognitoPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer,
    private val downloadPageInitializer: DownloadPageInitializer,
    private val historyPageInitializer: HistoryPageInitializer,
    private val logger: Logger
): WebView.FindListener {

    /**
     * The unique ID of the view.
     */
    val id = View.generateViewId()

    /**
     * Getter for the [LightningViewTitle] of the current LightningView instance.
     *
     * @return a NonNull instance of LightningViewTitle
     * @return a NonNull instance of LightningViewTitle
     */
    val titleInfo: LightningViewTitle

    /**
     * Meta theme-color content value as extracted from page HTML
     */
    var htmlMetaThemeColor: Int = KHtmlMetaThemeColorInvalid

    /**
     * Define the number of times we should try to fetch HTML meta tehme-color
     */
    var fetchMetaThemeColorTries = KFetchMetaThemeColorTries

    /**
     * A tab initializer that should be run when the view is first attached.
     * Notably contains a bundle to be load in our webView.
     */
    private var latentTabInitializer: FreezableBundleInitializer? = null

    /**
     * Gets the current WebView instance of the tab.
     *
     * @return the WebView instance of the tab, which can be null.
     */
    var webView: WebViewEx? = null
        private set

    private lateinit var lightningWebClient: LightningWebClient

    /**
     * The URL we tried to load
     */
    private var iTargetUrl: Uri = Uri.parse("")

    private val uiController: UIController
    private lateinit var gestureDetector: GestureDetector
    private val paint = Paint()

    /**
     * Sets whether this tab was the result of a new intent sent to the browser.
     * That's notably used to decide if we close our activity when closing this tab thus going back to the app which opened it.
     */
    var isNewTab: Boolean = false

    /**
     * This method sets the tab as the foreground tab or a background tab.
     */
    var isForeground: Boolean = false
        set(aIsForeground) {
            field = aIsForeground
            if (isForeground) {
                // When frozen tab goes foreground we need to load its bundle in webView
                latentTabInitializer?.apply {
		            // Lazy creation of our WebView
                    createWebView()
                    // Load bundle in WebView
                    initializeContent(this)
                    // Discard tab initializer since we just consumed it
                    latentTabInitializer = null
                }
            } else {
                // A tab sent to the background is not so new anymore
                isNewTab = false
            }
            uiController.tabChanged(this)
        }
    /**
     * Gets whether or not the page rendering is inverted or not. The main purpose of this is to
     * indicate that JavaScript should be run at the end of a page load to invert only the images
     * back to their non-inverted states.
     *
     * @return true if the page is in inverted mode, false otherwise.
     */
    var invertPage = false
        private set

    /**
     * True if desktop mode is enabled for this tab.
     */
    var desktopMode = false
        set(aDesktopMode) {
            field = aDesktopMode
            // Set our user agent accordingly
            if (aDesktopMode) {
                webView?.settings?.userAgentString = WINDOWS_DESKTOP_USER_AGENT_PREFIX + webViewEngineVersionDesktop(activity.application)
            } else {
                setUserAgentForPreference(userPreferences)
            }
        }

    /**
     *
     */
    var darkMode = false
        set(aDarkMode) {
            field = aDarkMode
            applyDarkMode();
        }

    /**
     * Get our find in page search query.
     *
     * @return The find in page search query or an empty string.
     */
    var searchQuery: String = ""
        set(aSearchQuery) {
            field = aSearchQuery
            //find(searchQuery)
        }

    /**
     * Define if this tab has an active find in page search.
     */
    var searchActive = false

    /**
     *
     */
    private val webViewHandler = WebViewHandler(this)

    /**
     * This method gets the additional headers that should be added with each request the browser
     * makes.
     *
     * @return a non null Map of Strings with the additional request headers.
     */
    internal val requestHeaders = ArrayMap<String, String>()

    private val maxFling: Float

    private val hiltEntryPoint = EntryPointAccessors.fromApplication(activity.applicationContext, HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val dialogBuilder: LightningDialogBuilder = hiltEntryPoint.dialogBuilder
    val proxyUtils: ProxyUtils = hiltEntryPoint.proxyUtils
    val databaseScheduler: Scheduler = hiltEntryPoint.databaseScheduler()
    val mainScheduler: Scheduler = hiltEntryPoint.mainScheduler()
    val networkConnectivityModel: NetworkConnectivityModel = hiltEntryPoint.networkConnectivityModel

    private val networkDisposable: Disposable

    /**
     * This method determines whether the current tab is visible or not.
     *
     * @return true if the WebView is non-null and visible, false otherwise.
     */
    val isShown: Boolean
        get() = webView?.isShown == true

    /**
     * Gets the current progress of the WebView.
     *
     * @return returns a number between 0 and 100 with the current progress of the WebView. If the
     * WebView is null, then the progress returned will be 100.
     */
    val progress: Int
        get() = webView?.progress ?: 100

    /**
     * Tells if a web page is currently loading.
     */
    val isLoading
        get() = progress != 100

    /**
     * Get the current user agent used by the WebView.
     *
     * @return retuns the current user agent of the WebView instance, or an empty string if the
     * WebView is null.
     */
    private val userAgent: String
        get() = webView?.settings?.userAgentString ?: ""

    /**
     * Gets the favicon currently in use by the page. If the current page does not have a favicon,
     * it returns a default icon.
     *
     * @return a non-null Bitmap with the current favicon.
     */
    val favicon: Bitmap?
        get() = titleInfo.getFavicon()

    /**
     * Get the current title of the page, retrieved from the title object.
     *
     * @return the title of the page, or an empty string if there is no title.
     */
    val title: String
        get() = titleInfo.getTitle() ?: ""

    /**
     * Get the current [SslCertificate] if there is any associated with the current page.
     */
    val sslCertificate: SslCertificate?
        get() = webView?.certificate

    /**
     * Get the current URL of the WebView, or an empty string if the WebView is null or the URL is
     * null.
     *
     * @return the current URL or an empty string.
     */
    val url: String
        get() {
            //TODO: One day find a way to write this expression without !! and without duplicating iTargetUrl.toString(), Kotlin is so weird
            return if (webView == null || webView!!.url.isNullOrBlank() || webView!!.url.isSpecialUrl()) {
                iTargetUrl.toString()
            } else  {
                webView!!.url as String
            }
        }

    /**
     * Return true if this tab is frozen, meaning it was not yet loaded from its bundle
     */
    val isFrozen : Boolean
        get() = latentTabInitializer?.tabModel?.webView != null


    /**
     * We had forgotten to unregisterReceiver our download listener thus leaking them all whenever we switched between sessions.
     * It turns out android as a hardcoded limit of 1000 [BroadcastReceiver] per application.
     * So after a while switching between sessions with many tabs we would get an exception saying:
     * "Too many receivers, total of 1000, registered for pid"
     * See: https://stackoverflow.com/q/58179733/3969362
     * TODO: Do we really need one of those per tab/WebView?
     */
    private var iDownloadListener: LightningDownloadListener? = null

    /**
     * Constructor
     */
    init {
        //activity.injector.inject(this)
        uiController = activity as UIController
        titleInfo = LightningViewTitle(activity)
        maxFling = ViewConfiguration.get(activity).scaledMaximumFlingVelocity.toFloat()
	
        // Mark our URL
        iTargetUrl = Uri.parse(tabInitializer.url())

        if (tabInitializer !is FreezableBundleInitializer) {
            // Create our WebView now
            //TODO: it looks like our special URLs don't get frozen for some reason
            createWebView()
            initializeContent(tabInitializer)
            desktopMode = userPreferences.desktopModeDefault
            darkMode = userPreferences.darkModeDefault
        } else {
            // Our WebView will only be created whenever our tab goes to the foreground
            latentTabInitializer = tabInitializer
            titleInfo.setTitle(tabInitializer.tabModel.title)
            titleInfo.setFavicon(tabInitializer.tabModel.favicon)
            desktopMode = tabInitializer.tabModel.desktopMode
            darkMode = tabInitializer.tabModel.darkMode
            searchQuery = tabInitializer.tabModel.searchQuery
            searchActive = tabInitializer.tabModel.searchActive
        }

        networkDisposable = networkConnectivityModel.connectivity()
            .observeOn(mainScheduler)
            .subscribe(::setNetworkAvailable)
    }

    /**
     * Create our WebView.
     */
    private fun createWebView() {
        lightningWebClient = LightningWebClient(activity, this)
        // Inflate our WebView as loading it from XML layout is needed to be able to set scrollbars color
        webView = activity.layoutInflater.inflate(R.layout.webview, null) as WebViewEx;
        webView?.apply {
            setFindListener(this@LightningView)
            //id = this@LightningView.id
            gestureDetector = GestureDetector(activity, CustomGestureListener(this))

            isFocusableInTouchMode = true
            isFocusable = true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                isAnimationCacheEnabled = false
                isAlwaysDrawnWithCacheEnabled = false
            }

            // Some web sites are broken if the background color is not white, thanks bbc.com and bbc.com/news for not defining background color.
            // However whatever we set here should be irrelevant as this is being taken care of in [BrowserActivity.changeToolbarBackground]
            // Though strictly speaking in a perfect world where web sites always define their background color themselves this should be our theme background color.
            setBackgroundColor(ThemeUtils.getBackgroundColor(activity))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            }

            isSaveEnabled = true
            setNetworkAvailable(true)
            webChromeClient = LightningChromeClient(activity, this@LightningView)
            webViewClient = lightningWebClient
            // We want to receive download complete notifications
            iDownloadListener = LightningDownloadListener(activity)
            setDownloadListener(iDownloadListener.also { activity.registerReceiver(it, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) })
            // For older devices show Tool Bar On Page Top won't work after fling to top.
            // Who cares? I mean those devices are probably from 2014 or older.
            val tl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) TouchListener().also { setOnScrollChangeListener(it) } else TouchListenerLollipop()
            setOnTouchListener(tl)

            initializeSettings()
        }

        initializePreferences()

        // If search was active enable it again
        if (searchActive) {
            find(searchQuery)
        }
    }

    fun currentSslState(): SslState = lightningWebClient.sslState

    /**
     * This method loads the homepage for the browser. Either it loads the URL stored as the
     * homepage, or loads the startpage or bookmark page if either of those are set as the homepage.
     */
    fun loadHomePage() {
        if (isIncognito) {
            iTargetUrl = Uri.parse(Uris.FulgurisIncognito)
            initializeContent(incognitoPageInitializer)
        } else {
            iTargetUrl = Uri.parse(Uris.FulgurisHome)
            initializeContent(homePageInitializer)
        }
    }

    /**
     * This function loads the bookmark page via the [BookmarkPageInitializer].
     */
    fun loadBookmarkPage() {
        iTargetUrl = Uri.parse(Uris.FulgurisBookmarks)
        initializeContent(bookmarkPageInitializer)
    }

    /**
     * This function loads the download page via the [DownloadPageInitializer].
     */
    fun loadDownloadsPage() {
        iTargetUrl = Uri.parse(Uris.FulgurisDownloads)
        initializeContent(downloadPageInitializer)
    }

    /**
     *
     */
    fun loadHistoryPage() {
        iTargetUrl = Uri.parse(Uris.FulgurisHistory)
        initializeContent(historyPageInitializer)
    }


    /**
     * Basically activate our tab initializer which typically loads something in our WebView.
     * [ResultMessageInitializer] being a notable exception as it will only send a message to something to load target URL at a later stage.
     */
    private fun initializeContent(tabInitializer: TabInitializer) {
        webView?.let { tabInitializer.initialize(it, requestHeaders) }
    }


    /**
     * Initialize the preference driven settings of the WebView. This method must be called whenever
     * the preferences are changed within SharedPreferences.
     */
    @SuppressLint("NewApi", "SetJavaScriptEnabled")
    fun initializePreferences() {
        val settings = webView?.settings ?: return

        lightningWebClient.updatePreferences()

        val modifiesHeaders = userPreferences.doNotTrackEnabled
            || userPreferences.saveDataEnabled
            || userPreferences.removeIdentifyingHeadersEnabled

        if (userPreferences.doNotTrackEnabled) {
            requestHeaders[HEADER_DNT] = "1"
        } else {
            requestHeaders.remove(HEADER_DNT)
        }

        if (userPreferences.saveDataEnabled) {
            requestHeaders[HEADER_SAVEDATA] = "on"
        } else {
            requestHeaders.remove(HEADER_SAVEDATA)
        }

        if (userPreferences.removeIdentifyingHeadersEnabled) {
            requestHeaders[HEADER_REQUESTED_WITH] = ""
            requestHeaders[HEADER_WAP_PROFILE] = ""
        } else {
            requestHeaders.remove(HEADER_REQUESTED_WITH)
            requestHeaders.remove(HEADER_WAP_PROFILE)
        }

        settings.defaultTextEncodingName = userPreferences.textEncoding
        setColorMode(userPreferences.renderingMode)

        if (!isIncognito) {
            settings.setGeolocationEnabled(userPreferences.locationEnabled)
        } else {
            settings.setGeolocationEnabled(false)
        }

        setUserAgentForPreference(userPreferences)

        settings.saveFormData = userPreferences.savePasswordsEnabled && !isIncognito

        if (userPreferences.javaScriptEnabled) {
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
        } else {
            settings.javaScriptEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
        }

        if (userPreferences.textReflowEnabled) {
            settings.layoutAlgorithm = LayoutAlgorithm.NARROW_COLUMNS
            try {
                settings.layoutAlgorithm = LayoutAlgorithm.TEXT_AUTOSIZING
            } catch (e: Exception) {
                // This shouldn't be necessary, but there are a number
                // of KitKat devices that crash trying to set this
                logger.log(TAG, "Problem setting LayoutAlgorithm to TEXT_AUTOSIZING")
            }
        } else {
            settings.layoutAlgorithm = LayoutAlgorithm.NORMAL
        }

        settings.blockNetworkImage = !userPreferences.loadImages
        // Modifying headers causes SEGFAULTS, so disallow multi window if headers are enabled.
        settings.setSupportMultipleWindows(userPreferences.popupsEnabled && !modifiesHeaders)

        settings.useWideViewPort = userPreferences.useWideViewPortEnabled
        settings.loadWithOverviewMode = userPreferences.overviewModeEnabled

        settings.textZoom = userPreferences.browserTextSize +  MIN_BROWSER_TEXT_SIZE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView,
                !userPreferences.blockThirdPartyCookiesEnabled)
        }

        applyDarkMode();
    }

    /**
     * Apply dark mode as needed.
     * We try to go dark when using app dark theme or when page is forced to dark mode.
     *
     * To test that you can load:
     * https://septatrix.github.io/prefers-color-scheme-test/
     *
     * See also:
     * https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
     */
    private fun applyDarkMode() {
        val settings = webView?.settings ?: return

        // If forced dark mode is supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) &&
            // and we are in dark theme or forced dark mode
            ((activity as ThemedActivity).useDarkTheme || darkMode)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                if (darkMode) {
                    // User requested forced dark mode from menu, we need to enable user agent dark mode then.
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        // Looks like that flag it's not working and will just do user agent dark mode even if page supports dark web theme.
                        // That means that when using app light theme you can't get dark web theme, you will just get user agent dark theme.
                        // No big deal though, just use app dark theme if you want proper web dark theme.
                        WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                    )
                } else {
                    // We are in app dark theme but this page does not forces to dark mode
                    // Just request dark web theme then.
                    // That's actually the only way to dark web theme rather than user agent darkening, see above comment.
                    WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                    )
                }
            }

            // We are either in app dark theme or forced dark mode, just request dark theme without actually forcing it.
            // Yes I know that flag's name is misleading to say the least.
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
        } else {
            // We are neither app dark theme or force dark mode or force dark mode is not supported.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                // We are in app light theme and force dark mode is disabled therefore:
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
            } else {
                // WebView force dark mode is not supported.
                if (darkMode) {
                    // Fallback to our special rendering mode then if user requests dark mode
                    // TODO: Have a setting option to make this the default behaviour?
                    setColorMode(RenderingMode.INVERTED_GRAYSCALE)
                } else {
                    setColorMode(userPreferences.renderingMode)
                }
            }
        }
    }

    /**
     * Initialize the settings of the WebView that are intrinsic to Lightning and cannot be altered
     * by the user. Distinguish between Incognito and Regular tabs here.
     */
    @SuppressLint("NewApi")
    private fun WebView.initializeSettings() {
        settings.apply {
            // That needs to be false for WebRTC to work at all, don't ask me why
            mediaPlaybackRequiresUserGesture = false

            if (API >= Build.VERSION_CODES.LOLLIPOP && !isIncognito) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            } else if (API >= Build.VERSION_CODES.LOLLIPOP) {
                // We're in Incognito mode, reject
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            if (!isIncognito || Capabilities.FULL_INCOGNITO.isSupported) {
                domStorageEnabled = true
                setAppCacheEnabled(true)
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            } else {
                domStorageEnabled = false
                setAppCacheEnabled(false)
                databaseEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowContentAccess = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            // Needed to prevent CTRL+TAB to scroll back to top of the page
            // See: https://github.com/Slion/Fulguris/issues/82
            setNeedInitialFocus(false)

            getPathObservable("appcache")
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { file ->
                    setAppCachePath(file.path)
                }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                getPathObservable("geolocation")
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe { file ->
                        setGeolocationDatabasePath(file.path)
                    }
            }
        }

    }

    private fun getPathObservable(subFolder: String) = Single.fromCallable {
        activity.getDir(subFolder, 0)
    }

    /**
     * This method is used to toggle the user agent between desktop and the current preference of
     * the user.
     */
    fun toggleDesktopUserAgent() {
        // Toggle desktop mode
        desktopMode = !desktopMode
    }

    /**
     *
     */
    fun toggleDarkMode() {
        // Toggle dark mode
        darkMode = !darkMode
    }


    /**
     * This method sets the user agent of the current tab based on the user's preference
     */
    private fun setUserAgentForPreference(userPreferences: UserPreferences) {
        webView?.settings?.userAgentString = userPreferences.userAgent(activity.application)
    }

    /**
     * Save the state of this tab Web View and return it as a [Bundle].
     * We get that state bundle either directly from our Web View,
     * or from our frozen tab initializer if ever our Web View was never loaded.
     */
    private fun webViewState(): Bundle = latentTabInitializer?.tabModel?.webView
        ?: Bundle(ClassLoader.getSystemClassLoader()).also {
            webView?.saveState(it)
        }

    /**
     * Save the state of this tab and return it as a [Bundle].
     */
    fun saveState(): Bundle {
         return TabModel(url, title, desktopMode, darkMode, favicon, searchQuery, searchActive, webViewState()).toBundle()
    }
    /**
     * Pause the current WebView instance.
     */
    fun onPause() {
        webView?.onPause()
        logger.log(TAG, "WebView onPause: ${webView?.id}")
    }

    /**
     * Resume the current WebView instance.
     */
    fun onResume() {
        webView?.onResume()
        logger.log(TAG, "WebView onResume: ${webView?.id}")
    }

    /**
     * Notify the WebView to stop the current load.
     */
    fun stopLoading() {
        webView?.stopLoading()
    }

    /**
     * This method forces the layer type to hardware, which
     * enables hardware rendering on the WebView instance
     * of the current LightningView.
     */
    private fun setHardwareRendering() {
        webView?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    /**
     * This method sets the layer type to none, which
     * means that either the GPU and CPU can both compose
     * the layers when necessary.
     */
    private fun setNormalRendering() {
        webView?.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /**
     * This method forces the layer type to software, which
     * disables hardware rendering on the WebView instance
     * of the current LightningView and makes the CPU render
     * the view.
     */
    fun setSoftwareRendering() {
        webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Sets the current rendering color of the WebView instance
     * of the current LightningView. The for modes are normal
     * rendering, inverted rendering, grayscale rendering,
     * and inverted grayscale rendering
     *
     * @param mode the integer mode to set as the rendering mode.
     * see the numbers in documentation above for the
     * values this method accepts.
     */
    private fun setColorMode(mode: RenderingMode) {
        invertPage = false
        when (mode) {
            RenderingMode.NORMAL -> {
                paint.colorFilter = null
                // setSoftwareRendering(); // Some devices get segfaults
                // in the WebView with Hardware Acceleration enabled,
                // the only fix is to disable hardware rendering
                //setNormalRendering()
                // SL: enabled that and the performance gain is very noticeable on  F(x)tec Pro1
                // Notably on: https://www.bbc.com/worklife
                setHardwareRendering()
            }
            RenderingMode.INVERTED -> {
                val filterInvert = ColorMatrixColorFilter(
                    negativeColorArray)
                paint.colorFilter = filterInvert
                setHardwareRendering()

                invertPage = true
            }
            RenderingMode.GRAYSCALE -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val filterGray = ColorMatrixColorFilter(cm)
                paint.colorFilter = filterGray
                setHardwareRendering()
            }
            RenderingMode.INVERTED_GRAYSCALE -> {
                val matrix = ColorMatrix()
                matrix.set(negativeColorArray)
                val matrixGray = ColorMatrix()
                matrixGray.setSaturation(0f)
                val concat = ColorMatrix()
                concat.setConcat(matrix, matrixGray)
                val filterInvertGray = ColorMatrixColorFilter(concat)
                paint.colorFilter = filterInvertGray
                setHardwareRendering()

                invertPage = true
            }

            RenderingMode.INCREASE_CONTRAST -> {
                val increaseHighContrast = ColorMatrixColorFilter(increaseContrastColorArray)
                paint.colorFilter = increaseHighContrast
                setHardwareRendering()
            }
        }

    }

    /**
     * Pauses the JavaScript timers of the
     * WebView instance, which will trigger a
     * pause for all WebViews in the app.
     */
    fun pauseTimers() {
        webView?.pauseTimers()
        logger.log(TAG, "Pausing JS timers")
    }

    /**
     * Resumes the JavaScript timers of the
     * WebView instance, which will trigger a
     * resume for all WebViews in the app.
     */
    fun resumeTimers() {
        webView?.resumeTimers()
        logger.log(TAG, "Resuming JS timers")
    }

    /**
     * Requests focus down on the WebView instance
     * if the view does not already have focus.
     */
    fun requestFocus() {
        if (webView?.hasFocus() == false) {
            webView?.requestFocus()
        }
    }

    /**
     * Sets the visibility of the WebView to either
     * View.GONE, View.VISIBLE, or View.INVISIBLE.
     * other values passed in will have no effect.
     *
     * @param visible the visibility to set on the WebView.
     */
    fun setVisibility(visible: Int) {
        webView?.visibility = visible
    }

    /**
     * Tells the WebView to reload the current page.
     * If the proxy settings are not ready then the
     * this method will not have an affect as the
     * proxy must start before the load occurs.
     */
    fun reload() {
        // Check if configured proxy is available
        if (!proxyUtils.isProxyReady(activity)) {
            // User has been notified
            return
        }

        // Handle the case where we display error page for instance
        loadUrl(url)

        //webView?.reload()
    }

    /**
     * Finds all the instances of the text passed to this
     * method and highlights the instances of that text
     * in the WebView.
     *
     * @param text the text to search for.
     */
    @SuppressLint("NewApi")
    fun find(text: String) {
        resetFind()
        searchQuery = text
        searchActive = true
        // Kick off our search
        webView?.findAllAsync(text)
    }

    fun findNext() {
        webView?.findNext(true)
    }

    fun findPrevious() {
        webView?.findNext(false)
    }

    fun clearFind() {
        webView?.clearMatches()
        searchActive = false
        resetFind()
    }

    // Used to implement find in page
    private var iActiveMatchOrdinal: Int = -1
    private var iNumberOfMatches: Int = -1
    private var iSnackbar: Snackbar? = null

    /**
     *
     */
    private fun resetFind() {
        iActiveMatchOrdinal = -1
        iNumberOfMatches = -1
    }

    /**
     * That's where find in page results are being reported by our WebView.
     */
    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {

        // If our page is still loading or if our find in page search is not complete
        if (isLoading || !isDoneCounting) {
            // Just don't report intermediary results
            return
        }
        // Only display message if something was changed
        if (iActiveMatchOrdinal != activeMatchOrdinal || iNumberOfMatches != numberOfMatches) {

            // Remember what we last reported
            iActiveMatchOrdinal = activeMatchOrdinal
            iNumberOfMatches = numberOfMatches

            // Empty search query just dismisses any results previously displayed
            if (searchQuery.isEmpty()) {
                // Hide last snackbar to avoid having outdated stats lingering
                // Notably useful when doing backspace on the search field until no characters are left
                iSnackbar?.dismiss()
            }
            // Check if our search is reporting any match
            else if (iNumberOfMatches==0) {
                // Find in page did not find any match, tell our user about it
                iSnackbar = activity.makeSnackbar(
                        activity.getString(R.string.no_match_found),
                        Snackbar.LENGTH_SHORT, if (activity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                        .setAction(R.string.button_dismiss) {
                            iSnackbar?.dismiss()
                        }

                iSnackbar?.show()
            } else {
                // Show our user how many matches we have and which one is currently focused
                val currentMatch = iActiveMatchOrdinal + 1
                iSnackbar = activity.makeSnackbar(
                        activity.getString(R.string.match_x_of_n,currentMatch,iNumberOfMatches) ,
                        Snackbar.LENGTH_SHORT, if (activity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                        .setAction(R.string.button_dismiss) {
                            iSnackbar?.dismiss()
                        }

                iSnackbar?.show()
            }
        }
    }

    /**
     * Notify the tab to shutdown and destroy
     * its WebView instance and to remove the reference
     * to it. After this method is called, the current
     * instance of the LightningView is useless as
     * the WebView cannot be recreated using the public
     * api.
     */
    fun destroy() {

        //See: https://console.firebase.google.com/project/fulguris-b1f69/crashlytics/app/android:net.slions.fulguris.full.playstore/issues/ea99c7ea0c57f66eae6e95532a16859d
        if (iDownloadListener!=null) {
            activity.unregisterReceiver(iDownloadListener)
            iDownloadListener = null
        }
        networkDisposable.dispose()
        webView?.autoDestruction()
    }

    /**
     * Tell the WebView to navigate backwards
     * in its history to the previous page.
     */
    fun goBack() {
        webView?.goBack()
    }

    /**
     * Tell the WebView to navigate forwards
     * in its history to the next page.
     */
    fun goForward() {
        webView?.goForward()
    }

    /**
     * Notifies the [WebView] whether the network is available or not.
     */
    private fun setNetworkAvailable(isAvailable: Boolean) {
        webView?.setNetworkAvailable(isAvailable)
    }

    /**
     * Handles a long click on the page and delegates the URL to the
     * proper dialog if it is not null, otherwise, it tries to get the
     * URL using HitTestResult.
     *
     * @param url the url that should have been obtained from the WebView touch node
     * thingy, if it is null, this method tries to deal with it and find
     * a workaround.
     * @param text Text from the target Anchor
     * @param src Source from the target Image
     */
    private fun longClickPage(url: String?, text: String?, src: String?) {
        val result = webView?.hitTestResult
        val currentUrl = webView?.url
        val newUrl = result?.extra

        if (currentUrl != null && currentUrl.isSpecialUrl()) {
            if (currentUrl.isHistoryUrl()) {
                if (url != null) {
                    dialogBuilder.showLongPressedHistoryLinkDialog(activity, uiController, url)
                } else if (newUrl != null) {
                    dialogBuilder.showLongPressedHistoryLinkDialog(activity, uiController, newUrl)
                }
            } else if (currentUrl.isBookmarkUrl()) {
                if (url != null) {
                    dialogBuilder.showLongPressedDialogForBookmarkUrl(activity, uiController, url)
                } else if (newUrl != null) {
                    dialogBuilder.showLongPressedDialogForBookmarkUrl(activity, uiController, newUrl)
                }
            } else if (currentUrl.isDownloadsUrl()) {
                if (url != null) {
                    dialogBuilder.showLongPressedDialogForDownloadUrl(activity, uiController, url)
                } else if (newUrl != null) {
                    dialogBuilder.showLongPressedDialogForDownloadUrl(activity, uiController, newUrl)
                }
            }
        } else {

            // See: https://developer.android.com/reference/android/webkit/WebView#getHitTestResult()
            result?.extra?.let { extraUrl ->
                if (result.type == WebView.HitTestResult.IMAGE_TYPE) {
                    dialogBuilder.showLongPressLinkImageDialog(
                        activity, uiController, "", extraUrl, text, userAgent,
                        showLinkTab = false,
                        showImageTab = true
                    )
                } else if (result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    dialogBuilder.showLongPressLinkImageDialog(
                        activity, uiController, url ?: "", extraUrl, text, userAgent,
                        showLinkTab = true,
                        showImageTab = true
                    )
                } else if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                    dialogBuilder.showLongPressLinkImageDialog(
                        activity, uiController, extraUrl, "", text, userAgent,
                        showLinkTab = true,
                        showImageTab = false
                    )
                }
                // TODO: UNKNOWN_TYPE for JavaScript URLs do we really want to?
                // TODO: Handle other types such as phone, geo and email
            }
        }
    }

    /**
     * Determines whether or not the WebView can go
     * backward or if it as the end of its history.
     *
     * @return true if the WebView can go back, false otherwise.
     */
    fun canGoBack(): Boolean = webView?.canGoBack() == true

    /**
     * Determine whether or not the WebView can go
     * forward or if it is at the front of its history.
     *
     * @return true if it can go forward, false otherwise.
     */
    fun canGoForward(): Boolean = webView?.canGoForward() == true

    /**
     * Loads the URL in the WebView. If the proxy settings
     * are still initializing, then the URL will not load
     * as it is necessary to have the settings initialized
     * before a load occurs.
     *
     * SL: Funny enough this is hardly ever used only when opening new tba from intent apparently
     *
     * @param aUrl the non-null URL to attempt to load in
     * the WebView.
     */
    fun loadUrl(aUrl: String) {
        // Check if configured proxy is available
        if (!proxyUtils.isProxyReady(activity)) {
            return
        }

        iTargetUrl = Uri.parse(aUrl)

        if (iTargetUrl.scheme == Schemes.Fulguris || iTargetUrl.scheme == Schemes.About) {
            //TODO: support more of our custom URLs?
            if (iTargetUrl.host == Hosts.Home) {
                loadHomePage()
            } else if (iTargetUrl.host == Hosts.Bookmarks) {
                loadBookmarkPage()
            } else if (iTargetUrl.host == Hosts.History) {
                loadHistoryPage()
            }
        } else {
            webView?.loadUrl(aUrl, requestHeaders)
        }
    }

    /**
     * Check relevant user preferences and configuration before showing the tool bar if needed
     */
    fun showToolBarOnScrollUpIfNeeded() {
        if (webView?.context?.configPrefs?.showToolBarOnScrollUp == true) {
            uiController.showActionBar()
        }
    }

    /**
     * Check relevant user preferences and configuration before showing the tool bar if needed
     */
    fun showToolBarOnPageTopIfNeeded() {
        if (webView?.context?.configPrefs?.showToolBarOnPageTop == true) {
            uiController.showActionBar()
        }
    }

    /**
     * The OnTouchListener used by the WebView so we can
     * get scroll events and show/hide the action bar when
     * the page is scrolled up/down.
     */
    private open inner class TouchListenerLollipop : OnTouchListener {

        internal var location: Float = 0f
        protected var touchingScreen: Boolean = false
        internal var y: Float = 0f
        internal var action: Int = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View?, arg1: MotionEvent): Boolean {

            if (view == null) return false

            if (!view.hasFocus()) {
                view.requestFocus()
            }

            action = arg1.action
            y = arg1.y
            // Handle tool bar visibility when doing slow scrolling
            if (action == MotionEvent.ACTION_DOWN) {
                location = y
                touchingScreen=true
            }
            // Only show or hide tool bar when the user stop touching the screen otherwise that looks ugly
            else if (action == MotionEvent.ACTION_UP) {
                val distance = y - location
                touchingScreen=false
                if (view.scrollY < SCROLL_DOWN_THRESHOLD
                        // Touch input won't show tool bar again if no vertical scroll
                        // It can still be accessed using the back button
                        && view.canScrollVertically()) {
                    showToolBarOnPageTopIfNeeded()
                } else if (distance < -SCROLL_UP_THRESHOLD) {
                    // Aggressive hiding of tool bar
                    uiController.hideActionBar()
                }
                location = 0f
            }

            // Handle tool bar visibility upon fling gesture
            gestureDetector.onTouchEvent(arg1)

            return false
        }
    }

    /**
     * Improved touch listener for devices above API 21 Lollipop
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private inner class TouchListener: TouchListenerLollipop(), OnScrollChangeListener {

        override fun onScrollChange(view: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {

            view?.apply {
                if (canScrollVertically()) {
                    // Handle the case after fling all the way to the top of the web page
                    // Are we near the top of our web page and is user finger not on the screen
                    if (scrollY < SCROLL_DOWN_THRESHOLD && !touchingScreen) {
                        showToolBarOnPageTopIfNeeded()
                    }
                }
            }
        }
    }

    /**
     * The SimpleOnGestureListener used by the [TouchListener]
     * in order to delegate show/hide events to the action bar when
     * the user flings the page. Also handles long press events so
     * that we can capture them accurately.
     */
    private inner class CustomGestureListener(private val view: View) : SimpleOnGestureListener() {

        /**
         * Without this, onLongPress is not called when user is zooming using
         * two fingers, but is when using only one.
         *
         *
         * The required behaviour is to not trigger this when the user is
         * zooming, it shouldn't matter how much fingers the user's using.
         */
        private var canTriggerLongPress = true

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val power = (velocityY * 100 / maxFling).toInt()
            if (power < -10) {
                uiController.hideActionBar()
            } else if (power > 15
                    // Touch input won't show tool bar again if no top level vertical scroll
                    // It can still be accessed using the back button
                    && view.canScrollVertically()) {
                showToolBarOnScrollUpIfNeeded()
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onLongPress(e: MotionEvent) {
            if (canTriggerLongPress) {
                val msg = webViewHandler.obtainMessage()
                if (msg != null) {
                    msg.target = webViewHandler
                    webView?.requestFocusNodeHref(msg)
                }
            }
        }

        /**
         * Is called when the user is swiping after the doubletap, which in our
         * case means that he is zooming.
         */
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            canTriggerLongPress = false
            return false
        }

        /**
         * Is called when something is starting being pressed, always before
         * onLongPress.
         */
        override fun onShowPress(e: MotionEvent) {
            canTriggerLongPress = true
        }
    }

    /**
     * A Handler used to get the URL from a long click
     * event on the WebView. It does not hold a hard
     * reference to the WebView and therefore will not
     * leak it if the WebView is garbage collected.
     */
    private class WebViewHandler(view: LightningView) : Handler() {

        private val reference: WeakReference<LightningView> = WeakReference(view)

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            // Fetch message data: url, text, image source
            // See: https://developer.android.com/reference/android/webkit/WebView#requestFocusNodeHref(android.os.Message)
            val url = msg.data.getString("url")
            val title = msg.data.getString("title")
            val src = msg.data.getString("src")
            //
            reference.get()?.longClickPage(url,title,src)
        }
    }

    companion object {

        public const val KHtmlMetaThemeColorInvalid: Int = Color.TRANSPARENT
        public const val KFetchMetaThemeColorTries: Int = 6

        private const val TAG = "LightningView"

        const val HEADER_REQUESTED_WITH = "X-Requested-With"
        const val HEADER_WAP_PROFILE = "X-Wap-Profile"
        private const val HEADER_DNT = "DNT"
        private const val HEADER_SAVEDATA = "Save-Data"

        private val API = Build.VERSION.SDK_INT
        private val SCROLL_UP_THRESHOLD = Utils.dpToPx(10f)
        private val SCROLL_DOWN_THRESHOLD = Utils.dpToPx(30f)

        private val negativeColorArray = floatArrayOf(
            -1.0f, 0f, 0f, 0f, 255f, // red
            0f, -1.0f, 0f, 0f, 255f, // green
            0f, 0f, -1.0f, 0f, 255f, // blue
            0f, 0f, 0f, 1.0f, 0f // alpha
        )
        private val increaseContrastColorArray = floatArrayOf(
            2.0f, 0f, 0f, 0f, -160f, // red
            0f, 2.0f, 0f, 0f, -160f, // green
            0f, 0f, 2.0f, 0f, -160f, // blue
            0f, 0f, 0f, 1.0f, 0f // alpha
        )
    }
}
