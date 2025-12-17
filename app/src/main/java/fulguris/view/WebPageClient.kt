package fulguris.view

import fulguris.BuildConfig
import fulguris.R
import fulguris.adblock.AbpBlockerManager
import fulguris.adblock.AdBlocker
import fulguris.adblock.NoOpAdBlocker
import fulguris.browser.WebBrowser
import fulguris.activity.WebBrowserActivity
import fulguris.di.HiltEntryPoint
import fulguris.di.configPrefs
import fulguris.extensions.getDrawable
import fulguris.extensions.getText
import fulguris.extensions.ihs
import fulguris.extensions.makeSnackbar
import fulguris.extensions.launch
import fulguris.extensions.setIcon
import fulguris.html.homepage.HomePageFactory
import fulguris.js.InvertPage
import fulguris.js.SetMetaViewport
import fulguris.js.TextReflow
import fulguris.settings.NoYesAsk
import fulguris.settings.preferences.DomainPreferences
import fulguris.settings.preferences.UserPreferences
import fulguris.ssl.SslState
import fulguris.utils.*
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.webkit.*
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.EntryPointAccessors
import fulguris.enums.LogLevel
import fulguris.app
import fulguris.utils.ThemeUtils
import fulguris.utils.htmlColor
import fulguris.utils.isSpecialUrl
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.math.abs

/**
 * We have one instance of this per [WebView] and our [WebPageTab] also as a reference to it.
 *
 * Page load events sequence tested against slions.net:
 * - [shouldOverrideUrlLoading] when applicable
 * - [shouldInterceptRequest]
 * - [onLoadResource] - For the page URL is called first
 * - [onPageStarted]  - Not called if interrupted before the first resource completed
 * - [onLoadResource] - For each resources
 * - [onPageFinished] - Also called when cancelled - can be called even though onPageStarted was not called
 * - [onLoadResource] - Can still occur after onPageFinished even if load was cancelled
 *
 */
class WebPageClient(
        private val activity: Activity,
        private val webPageTab: WebPageTab
) : WebViewClient() {

    private val webBrowser: WebBrowser = activity as WebBrowser

    private val hiltEntryPoint = EntryPointAccessors.fromApplication(activity.applicationContext, HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val preferences: SharedPreferences = hiltEntryPoint.userSharedPreferences()
    val textReflowJs: TextReflow = hiltEntryPoint.textReflowJs
    val invertPageJs: InvertPage = hiltEntryPoint.invertPageJs
    val setMetaViewport: SetMetaViewport = hiltEntryPoint.setMetaViewport
    val homePageFactory: HomePageFactory = hiltEntryPoint.homePageFactory
    val abpBlockerManager: AbpBlockerManager = hiltEntryPoint.abpBlockerManager
    val noopBlocker: NoOpAdBlocker = hiltEntryPoint.noopBlocker

    private var adBlock: AdBlocker

    // Needed this to keep track of all SSL error since it seems onReceivedSslError is not called again after you proceed
    // We use this list to make sure our SSL state is maintained correctly after navigating away and back between various SSL error pages
    private var sslErrorUrls = arrayListOf<String>()

    @Volatile private var isRunning = false
    private var zoomScale = 0.0f

    private var currentUrl: String = ""

    // Count the number of resources loaded since the page was last started
    private var iResourceCount: Int = 0;

    // Track page load timing for profiling
    private var pageLoadStartTime: Long = 0

    // Track all requests for the current page and whether they were blocked
    data class PageRequest(
        val url: String,
        val wasBlocked: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val pageRequests = mutableListOf<PageRequest>()

    /**
     * Get all requests for the current page
     */
    fun getPageRequests(): List<PageRequest> = pageRequests.toList()

    /**
     * Clear tracked requests
     */
    fun clearPageRequests() {
        pageRequests.clear()
    }

//    private var elementHide = userPreferences.elementHide

    var sslState: SslState = SslState.None
        private set(value) {
            field = value
            webBrowser.updateSslState(field)
        }


    init {
        //activity.injector.inject(this)
        adBlock = chooseAdBlocker()
    }


    fun updatePreferences() {
        adBlock = chooseAdBlocker()
    }

    private fun chooseAdBlocker(): AdBlocker = if (userPreferences.adBlockEnabled) {
        abpBlockerManager
    } else {
        noopBlocker
    }

    /**
     * Should be called once when the root HTML page had been loaded.
     *
     * If user requested a viewport different than 100% then we enable wide viewport mode and inject some JavaScript to manipulate meta viewport HTML element.
     * This enables a zoomed out desktop mode on smartphones.
     */
    private fun applyDesktopModeIfNeeded(aView: WebView) {

        // Just use normal viewport unless we decide otherwise later
        aView.settings.useWideViewPort = false;

        if (webPageTab.desktopMode) {
            // Do not hack anything when desktop width is set to 100%
            // In this case desktop mode then only overrides the user agent which is all you should need in most cases really
            if (aView.context.configPrefs.desktopWidth != 100F) {
                // That's needed for custom desktop mode support
                // See: https://stackoverflow.com/a/60621350/3969362
                // See: https://stackoverflow.com/a/39642318/3969362
                // Just pass on user defined viewport width in percentage of the actual viewport to the JavaScript
                aView.settings.useWideViewPort = true
                Timber.w("evaluateJavascript: desktop mode")
                aView.evaluateJavascript(setMetaViewport.provideJs().replaceFirst("\$width\$","${aView.context.configPrefs.desktopWidth}"), null)
            }
        }
    }

    /**
     * Overrides [WebViewClient.shouldInterceptRequest].
     * Looks like we need to intercept our custom URLs here to implement support for fulguris and about scheme.
     *   comment Helium314: adBLock.shouldBock always never blocks if url.isSpecialUrl() or url.isAppScheme(), could be moved here
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        Timber.v("$ihs : shouldInterceptRequest")
        // returns some dummy response if blocked, null if not blocked

        val response = adBlock.shouldBlock(request, currentUrl)
        val wasBlocked = response != null

        val url = request.url.toString()

        // Detect if this is a main document request (page navigation) vs subresource
        // Main document requests have isForMainFrame == true
        if (request.isForMainFrame) {
            // Update targetUrl if this is a new navigation (including JavaScript history navigation)
            // This ensures targetUrl is always accurate for domain preferences and other logic
            if (webPageTab.targetUrl != request.url) {
                Timber.i("$ihs : Main frame navigation detected, updating targetUrl: $url")
                webPageTab.targetUrl = request.url
            }

            // This resource request is the main frame
            // Record page load start time for profiling
            pageLoadStartTime = System.currentTimeMillis()
            // Reset our resource count
            iResourceCount = 0
            // Clear page requests for the new page
            clearPageRequests()
        }

        // Track this request
        synchronized(pageRequests) {
            pageRequests.add(PageRequest(url, wasBlocked))
        }

        //SL: Use this when debugging
//        if (response!=null)
//        {
//            Timber.d( "Request hijacked: " + request.url
//                    + "\n Reason phrase:" + response.reasonPhrase
//                    + "\n Status code:" + response.statusCode
//            )
//        }

        return response
    }

    /**
     * Overrides [WebViewClient.onLoadResource]
     * Called multiple times during page load, once for every resource we load.
     */
    override fun onLoadResource(view: WebView, url: String?) {
        super.onLoadResource(view, url)

        //Timber.d("$ihs : onLoadResource - url: ${webPageTab.webView?.url}")
        //Timber.d("$ihs : onLoadResource - original: ${webPageTab.webView?.originalUrl}")
        //Timber.d("$ihs : onLoadResource - target: ${webPageTab.targetUrl}")

        // Count our resources
        iResourceCount++
        Timber.d("$ihs : onLoadResource - $iResourceCount - $url")
        val uri  = Uri.parse(url)
        loadDomainPreferences(uri.host ?: "", false)

        // Only do that on the first resource load
        if (iResourceCount==1) {
            // Now assuming our root HTML document has been loaded
            applyDesktopModeIfNeeded(view)
        }
    }

    /**
     *
     */
    private fun updateUrlIfNeeded(url: String, isLoading: Boolean) {
        // Update URL unless we are dealing with our special internal URL
        (url.isSpecialUrl()). let { dontDoUpdate ->
            webBrowser.updateUrl(if (dontDoUpdate) webPageTab.url else url, isLoading)
        }
    }

    /**
     * Overrides [WebViewClient.onPageFinished].
     * Also called when loading is interrupted using stopLoading.
     * That means this can be called even as onPageStarted was not yet called.
     */
    override fun onPageFinished(view: WebView, url: String) {
        // Calculate page load duration
        val pageLoadDuration = System.currentTimeMillis() - pageLoadStartTime
        Timber.i("$ihs : onPageFinished - $url - Load time: ${pageLoadDuration}ms - Resources: $iResourceCount")

        // Execute and clear callback registered with loadUrl
        webPageTab.onLoadCompleteCallback?.invoke()
        webPageTab.onLoadCompleteCallback = null

        // Make sure we apply desktop mode now as it may fail when done from onLoadResource
        // In fact the HTML page may not be loaded yet when we hit our condition in onLoadResource
        applyDesktopModeIfNeeded(view)


        if (view.isShown) {
            updateUrlIfNeeded(url, false)
            webBrowser.setBackButtonEnabled(view.canGoBack())
            webBrowser.setForwardButtonEnabled(view.canGoForward())
            view.postInvalidate()
        }
        if (view.title == null || (view.title as String).isEmpty()) {
            webPageTab.titleInfo.setTitle(activity.getString(R.string.untitled))
        } else {
            view.title?.let {webPageTab.titleInfo.setTitle(it)}
        }
        if (webPageTab.invertPage) {
            Timber.w("evaluateJavascript: invert page colors")
            view.evaluateJavascript(invertPageJs.provideJs(), null)
        }
/*        // TODO: element hiding does not work
        //  maybe because of the late injection?
        //  copy onDomContentLoaded callback from yuzu and use this to inject JS (used in yuzu also for invert and userJS)
        if (elementHide) {
            adBlock.loadScript(Uri.parse(currentUrl))?.let {
                view.evaluateJavascript(it, null)
            }
            // takes around half a second, but not sure what that tells me
        }*/

        if (userPreferences.forceZoom) {
            view.loadUrl(
                "javascript:(function() { document.querySelector('meta[name=\"viewport\"]').setAttribute(\"content\",\"width=device-width\"); })();"
            )
        }

        webBrowser.onTabChanged(webPageTab)

        // To prevent potential overhead when logs are not needed
        if (userPreferences.isLog(LogLevel.VERBOSE)) {
            val cookies = CookieManager.getInstance().getCookie(url)?.split(';')
            Timber.v("Cookies count: ${cookies?.count()}")
            cookies?.forEach {
                Timber.v(it.trim())
            }
        }
    }

    /**
     * Overrides [WebViewClient.onPageStarted]
     * You have no guarantee that the root HTML document has been loaded when this is called.
     * However I believe it means the first and main resource of the page has been downloaded.
     * Or it least it has started downloading as this is called after the first [onLoadResource].
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        Timber.i("$ihs : onPageStarted - $url")

        currentUrl = url
        val uri  = Uri.parse(url)
        loadDomainPreferences(uri.host ?: "", true)

        (view as WebViewEx).proxy.apply {
            // Only apply domain settings dark mode if no bypass
            if (!darkModeBypassDomainSettings) {
                darkMode = domainPreferences.darkMode
            }

            // Only apply domain settings desktop mode if no bypass
            if (!desktopModeBypassDomainSettings) {
                desktopMode = domainPreferences.desktopMode
            }

            // JavaScript
            if (domainPreferences.javaScriptEnabled) {
                view.settings.javaScriptEnabled = true
                view.settings.javaScriptCanOpenWindowsAutomatically = true
            } else {
                view.settings.javaScriptEnabled = false
                view.settings.javaScriptCanOpenWindowsAutomatically = false
            }
        }

        // Third-party cookies
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, domainPreferences.thirdPartyCookies)

        // Only set the SSL state if there isn't an error for the current URL.
        sslState = if (sslErrorUrls.contains(url)) {
            // We know this URL has an invalid certificate
            SslState.Invalid
        } else {
            // This URL has either a valid certificate or none
            if (URLUtil.isHttpsUrl(url)) {
                SslState.Valid
            } else {
                SslState.None
            }
        }
        webPageTab.titleInfo.resetFavicon()
        if (webPageTab.isShown) {
            updateUrlIfNeeded(url, true)
            webBrowser.showActionBar()
        }

        // Reset flag to fetch meta tags for new page
        webPageTab.shouldFetchMetaTags = true

        //uiController.onTabChanged(webPageTab)
        webBrowser.onPageStarted(webPageTab)
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
     *
     */
    override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest?) {
        Timber.d("$ihs : onReceivedClientCertRequest")
        super.onReceivedClientCertRequest(view, request)
    }

    /**
     *
     */
    override fun onReceivedHttpAuthRequest(
            view: WebView,
            handler: HttpAuthHandler,
            host: String,
            realm: String
    ) {
        Timber.d("$ihs : onReceivedHttpAuthRequest")
        MaterialAlertDialogBuilder(activity).apply {
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_auth_request, null)

            val realmLabel = dialogView.findViewById<TextView>(R.id.auth_request_realm_textview)
            val name = dialogView.findViewById<EditText>(R.id.auth_request_username_edittext)
            val password = dialogView.findViewById<EditText>(R.id.auth_request_password_edittext)

            realmLabel.text = activity.getString(R.string.label_realm, realm)

            setView(dialogView)
            setTitle(R.string.title_sign_in)
            setCancelable(true)
            setPositiveButton(R.string.title_sign_in) { _, _ ->
                val user = name.text.toString()
                val pass = password.text.toString()
                handler.proceed(user.trim(), pass.trim())
                Timber.i("Attempting HTTP Authentication")
            }
            setNegativeButton(R.string.action_cancel) { _, _ ->
                handler.cancel()
            }
        }.launch()
    }

    /**
     * Modern version of onReceivedError for API 23+
     * Called for any resource error (main frame, subframes, images, etc.)
     */
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        Timber.w("$ihs : onReceivedError (modern): ${request?.url} - Code: ${error?.errorCode} - ${error?.description}")
        super.onReceivedError(view, request, error)
    }

    /**
     * Deprecated but still called for main frame errors on older APIs
     * This deprecated callback is still in use and conveniently called only when the error affect the page main frame.
     */
    @Deprecated("Deprecated in Java")
    override fun onReceivedError(webview: WebView, errorCode: Int, error: String, failingUrl: String) {

        // None of those were working so we did Base64 encoding instead
        //"file:///android_asset/ask.png"
        //"android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.ic_about}"
        //"file:///android_res/drawable/ic_about"

        Timber.e("onReceivedError: ${domainPreferences.domain}")


        //Encode image to base64 string
        val output = ByteArrayOutputStream()
        val bitmap = activity.getDrawable(R.drawable.ic_about, android.R.attr.state_enabled).toBitmap()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        val imageBytes: ByteArray = output.toByteArray()
        val imageString = "data:image/png;base64,"+Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        // Generate a JavaScript that's going to modify the standard WebView error page for us.
        // It saves us from making up our own error texts and having to manage the translations ourselves.
        // Thus we simply use the standard and localized error messages from WebView.
        // The down side is that it makes a bunch of assumptions about WebView's error page that could fail us on some device or in case it gets changed at some point.
        val script = """(function() {
        document.getElementsByTagName('style')[0].innerHTML += "body { margin: 10px; background-color: ${htmlColor(ThemeUtils.getSurfaceColor(activity))}; color: ${htmlColor(ThemeUtils.getOnSurfaceColor(activity))};}"
        var img = document.getElementsByTagName('img')[0]
        img.src = "$imageString"
        img.width = ${bitmap.width}
        img.height = ${bitmap.height}
        })()"""

        // Run our script once, did not help anything apparently
        //webview.evaluateJavascript(script) {}
        // Stall our thread to workaround issues were our JavaScript would not apply to our error page for some reason
        // That works better than post or post delayed
        Thread.sleep(100)
        // Just run that script now
        Timber.w("evaluateJavascript: error page theming")
        webview.evaluateJavascript(script) {}
    }


    /**
     *
     */
    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        Timber.d("$ihs : onScaleChanged")
        if (view.isShown && webPageTab.userPreferences.textReflowEnabled) {
            if (isRunning)
                return
            val changeInPercent = abs(100 - 100 / zoomScale * newScale)
            if (changeInPercent > 2.5f && !isRunning) {
                isRunning = view.postDelayed({
                    zoomScale = newScale
                    Timber.w("evaluateJavascript: text reflow")
                    view.evaluateJavascript(textReflowJs.provideJs()) { isRunning = false }
                }, 100)
            }

        }
    }

    /**
     * Looks like this comes after our domain preferences have been loaded.
     * It seems if we proceed once for one issue that callback won't be called again, unless you restart the app.
     * NOTE: Test that stuff from https://badssl.com
     *
     * [webView] Points to the URL we are coming from
     * [error] Points to the URL we are going to
     */
    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(webView: WebView, handler: SslErrorHandler, error: SslError) {
        Timber.d("$ihs : onReceivedSslError")
        Timber.e("WebView URL: ${webView.url}")
        Timber.e("SSL error URL: ${error.url}")

        if (!sslErrorUrls.contains(error.url)) {
            sslErrorUrls.add(error.url)
        }

        when (domainPreferences.sslError) {
            NoYesAsk.YES -> return handler.proceed()
            NoYesAsk.NO -> {
                // TODO: Add a button to open proper domain settings
                //activity.snackbar(activity.getString(R.string.message_ssl_error_aborted, domainPreferences.domain))
                // Capture error domain cause it will have changed by the time we run the action
                val errorDomain = domainPreferences.domain
                activity.makeSnackbar(activity.getString(R.string.message_ssl_error_aborted),5000, Gravity.BOTTOM)
                    .setIcon(R.drawable.ic_unsecured)
                    .setAction(R.string.settings) {
                        (activity as? WebBrowserActivity)?.showDomainSettings(errorDomain)
                    }
                    .show()
                return handler.cancel()
            }
            else -> {}
        }

        // Ask user what to do then

        val errorCodeMessageCodes = getAllSslErrorMessageCodes(error)

        val stringBuilder = StringBuilder()
        for (messageCode in errorCodeMessageCodes) {
            stringBuilder.append("❌ ").append(activity.getString(messageCode)).append("\n\n")
        }

        // Our HTML conversion is a mess when it comes to new lines handling thus that trim and \n
        // TODO: sort it out at some point
        val alertMessage = activity.getText(R.string.message_ssl_error, domainPreferences.domain, stringBuilder.toString().trim() + "\n")?.trim()

        MaterialAlertDialogBuilder(activity).apply {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_with_checkbox, null)
            val dontAskAgain = view.findViewById<CheckBox>(R.id.checkBoxDontAskAgain)
            setTitle(activity.getString(R.string.title_warning))
            setMessage(alertMessage)
            setCancelable(true)
            setView(view)
            setIcon(R.drawable.ic_unsecured)
            setOnCancelListener { handler.cancel() }
            setPositiveButton(activity.getString(R.string.action_yes)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    applySslErrorToDomainSettings(NoYesAsk.YES)
                }
                handler.proceed()
            }
            setNegativeButton(activity.getString(R.string.action_no)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    applySslErrorToDomainSettings(NoYesAsk.NO)
                }
                handler.cancel()
            }
        }.launch()
    }

    /**
     * Persist user preference on SSL error to domain settings
     */
    private fun applySslErrorToDomainSettings(aSslError: NoYesAsk) {
        // Defensive we should not change default domain settings
        if (!domainPreferences.isDefault) {
            // SharedPreferences will automatically create the file when we write to it
            domainPreferences.sslErrorOverride = true
            domainPreferences.sslErrorLocal = aSslError
        } else {
            Timber.w("Domain settings should have been loaded already")
        }
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        Timber.d("$ihs : onFormResubmission")
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(activity.getString(R.string.title_form_resubmission))
            setMessage(activity.getString(R.string.message_form_resubmission))
            setCancelable(true)
            setPositiveButton(activity.getString(R.string.action_yes)) { _, _ ->
                resend.sendToTarget()
            }
            setNegativeButton(activity.getString(R.string.action_no)) { _, _ ->
                dontResend.sendToTarget()
            }
        }.launch()
    }

    // We use this to prevent opening such dialogs multiple times
    // Notably on Google Play app pages
    var appLaunchDialog: Dialog? = null

    // TODO: Shall this live somewhere else
    // Load default settings
    var domainPreferences = DomainPreferences(app)

    /**
     * Load domain preferences
     */
    private fun loadDomainPreferences(aHost :String, aEntryPoint: Boolean = false) {

        // Don't reload our preferences if we already have it
        // We hit that a lot actually as we load resources
        if (domainPreferences.domain==aHost) {
            Timber.v("$ihs : loadDomainPreferences: already loaded")
            return
        }

        Timber.d("$ihs : loadDomainPreferences for $aHost")

        // Load domain preferences
        // SharedPreferences cache is cleared when files are deleted, so we can safely load
        domainPreferences = DomainPreferences(app, aHost)
    }

    // Used to debounce app launch
    private var debounceLaunch: Runnable? = null

    /**
     * Overrides [WebViewClient.shouldOverrideUrlLoading].
     * It looks like this is only called when user navigates by following a link.
     * The following actions notably do not typically call this method:
     * - Opening a new tab if no redirect
     * - Loading a new URL without redirect
     * - Page reload or force reload
     * - Back and forward in tab history
     *
     * The following actions should call this method:
     * - Redirect, like when opening http://slions.net will redirect to https://slions.net
     * - When user clicks on a like
     * - Basically whenever the URL of the tab should change except when going back and forward weirdly
     *
     * It's notably called before the first [WebViewClient.onLoadResource] for the main page.
     * I reckon returning true should cancel that main page [WebViewClient.onLoadResource] callback.
     *
     * We are using it to trigger app launch according to user preferences.
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {

        Timber.i("$ihs : shouldOverrideUrlLoading - ${request.url}")

        val url = request.url.toString()
        val uri = Uri.parse(url)
        val headers = webPageTab.requestHeaders

        // This callback comes in before [shouldInterceptRequest] giving us an opportunity to update the target URL earlier
        // Though I reckon this is just defensive and could be removed, isForMainFrame should always be true here
        if (request.isForMainFrame) {
            // Update the target URL
            webPageTab.targetUrl = uri
        }

        if (webPageTab.isIncognito) {
            // If we are in incognito, immediately load, we don't want the url to leave the app
            return shouldStopUrlLoading(view, url, headers)
        }

        if (URLUtil.isAboutUrl(url)) {
            // If this is an about page, immediately load, we don't need to leave the app
            return shouldStopUrlLoading(view, url, headers)
        }

        loadDomainPreferences(uri.host ?: "", false)

        // Regardless of app launch we do not cancel URL loading
        // Doing so would require we deal with empty pages in new tab and such issues
        val intent = activity.intentForUrl(view, uri)
        if (intent != null) {
            // Don't launch apps from background tab
            if (webPageTab.isForeground) {
                var appLaunched = false

                // That debounce logic allows us launch our app ASAP while cancelling repeat launch
                if (debounceLaunch==null) {
                    // No pending debounce, just launch our app then
                    appLaunched = launchAppIfNeeded(view, intent)
                }
                // Cancel debounce if any
                view.removeCallbacks(debounceLaunch)
                // Create a new one
                debounceLaunch = Runnable {
                    debounceLaunch = null
                }
                // Schedule our debounce
                view.postDelayed(debounceLaunch,1000)

                // Not sure how to test that now
                if (appLaunched) {
                    Timber.d("$ihs : Override loading after app launch")
                    view.stopLoading()
                    if (activity is WebBrowserActivity) {
                        activity.closeCurrentTabIfEmpty()
                    }
                    return true
                }
            }
        }

        // Continue with loading the url
        // Don't show error page if we have an intent to launch an app
        // Still show error page if no intent to signal unsupported scheme
        return shouldStopUrlLoading(view, url, headers, intent != null)
    }

    /**
     * Check domain settings to decide whether to launch an app
     * The [view] this request is coming from
     * The [intent] defining the application we should launch
     * @return True if an app was launched on the spot, false otherwise.
     */
    private fun launchAppIfNeeded(view: WebView, intent: Intent): Boolean {

        Timber.d("$ihs : launchAppIfNeeded: $intent")

        when (domainPreferences.launchApp) {
            NoYesAsk.YES -> {
                Timber.d("$ihs : Launch app - YES")
                return activity.startActivityWithFallback(view, intent, false)
            }
            NoYesAsk.NO -> {
                Timber.d("$ihs : Launch app - NO")
                // Still load the page when not launching
                return false
            }
            NoYesAsk.ASK -> {
                Timber.d("$ihs : Launch app - ASK")

                if (appLaunchDialog == null) {
                    // Get app info from the intent
                    val packageManager = activity.packageManager
                    // Query for all apps that can handle this intent
                    val allResolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

                    // Filter to get specialized apps (non-browser apps)
                    val specializedApps = allResolveInfos.filter { info ->
                        val filter = info.filter
                        // Look for apps with specific schemes or data authorities (specialized apps)
                        filter != null && (filter.countDataAuthorities() > 0 ||
                            (filter.countDataSchemes() > 0 && !filter.hasDataScheme("http") && !filter.hasDataScheme("https")))
                    }

                    // Use specialized apps if available, otherwise use all
                    val resolveInfos = if (specializedApps.isNotEmpty()) specializedApps else allResolveInfos

                    if (resolveInfos.isEmpty()) {
                        Timber.w("No apps found to handle intent")
                        return false
                    }

                    // Determine if we have a single app or multiple apps
                    val hasSingleApp = resolveInfos.size == 1

                    // Choose layout based on number of apps
                    val dialogView: android.view.View

                    if (hasSingleApp) {
                        // Single app - use special layout with app icon and label
                        val resolveInfo = resolveInfos.first()
                        val appLabel = resolveInfo.loadLabel(packageManager).toString()
                        val appIcon = resolveInfo.loadIcon(packageManager)

                        // Inflate layout with app info and checkbox
                        dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_launch, null)
                        dialogView.findViewById<android.widget.ImageView>(R.id.app_icon)?.setImageDrawable(appIcon)
                        dialogView.findViewById<TextView>(R.id.app_label)?.text = appLabel

                        Timber.d("$ihs : Single app: $appLabel")
                    } else {
                        // Multiple apps - use simple layout with just checkbox
                        dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_with_checkbox, null)

                        Timber.d("$ihs : Multiple apps available (${resolveInfos.size})")
                    }

                    val checkboxView = dialogView.findViewById<CheckBox>(R.id.checkBoxDontAskAgain)

                    appLaunchDialog = MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.dialog_title_third_party_app)
                        .setMessage(R.string.dialog_message_third_party_app)
                        .setView(dialogView)
                        .setPositiveButton(activity.getText(R.string.action_launch)) { _, _ ->
                            // If checkbox is checked, save YES preference for this domain
                            if (checkboxView.isChecked) {
                                domainPreferences.launchAppOverride = true
                                domainPreferences.launchAppLocal = NoYesAsk.YES
                                Timber.d("$ihs : Saved preference: Launch app = YES for domain ${domainPreferences.domain}")
                            }
                            activity.startActivityWithFallback(view, intent, false)
                            appLaunchDialog = null
                        }
                        .setNegativeButton(activity.getText(R.string.action_cancel)) { _, _ ->
                            // If checkbox is checked, save NO preference for this domain
                            if (checkboxView.isChecked) {
                                domainPreferences.launchAppOverride = true
                                domainPreferences.launchAppLocal = NoYesAsk.NO
                                Timber.d("$ihs : Saved preference: Launch app = NO for domain ${domainPreferences.domain}")
                            }
                            activity.startActivityWithFallback(view, intent, true)
                            appLaunchDialog = null
                        }.setOnCancelListener {
                            appLaunchDialog = null
                        }.launch()
                }

                // Still load the page when asking
                return false
            }
        }
    }

    /**
     * Called as last step from [shouldOverrideUrlLoading] to decide whether to stop loading the URL
     * [aSkipErrorPage] True if you don't want to show ERR_UNKNOWN_URL_SCHEME error page after app launch for instance.
     */
    private fun shouldStopUrlLoading(webView: WebView, url: String, headers: Map<String, String>, aSkipErrorPage: Boolean = true): Boolean {
        Timber.d("$ihs : shouldStopUrlLoading")

        // Looks like it's intended to block everything that's not one of those
        // Will stop loading custom app schemes like: spotify:// or whatsapp://
        // That basically prevents showing the error page saying ERR_UNKNOWN_URL_SCHEME
        // But in some situations we may actually want to show it
        if (!URLUtil.isNetworkUrl(url)
            && !URLUtil.isFileUrl(url)
            && !URLUtil.isAboutUrl(url)
            && !URLUtil.isDataUrl(url)
            && !URLUtil.isJavaScriptUrl(url)) {
            if (aSkipErrorPage) {
                webView.stopLoading()
                Timber.w("$ihs : Stop loading")
                return true
            }
            // Should load error page
            // Test this by navigating to an unknown scheme like youtube:// as it's not a thing apparently
            // or simply use something like unsupported://example.com
            return false
        }
        return when {
            headers.isEmpty() -> false
            else -> {
                // I reckon this is what breaks page history when using custom headers
                // See: https://github.com/Slion/Fulguris/issues/414
                // TODO: There must be a way to make custom headers work without calling loadUrl from here
                webView.loadUrl(url, headers)
                Timber.w("$ihs : Load URL with headers")
                true
            }
        }
    }

    /**
     *
     */
    private fun getAllSslErrorMessageCodes(error: SslError): List<Int> {
        val errorCodeMessageCodes = ArrayList<Int>(1)

        if (error.hasError(SslError.SSL_DATE_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_date_invalid)
        }
        if (error.hasError(SslError.SSL_EXPIRED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_expired)
        }
        if (error.hasError(SslError.SSL_IDMISMATCH)) {
            errorCodeMessageCodes.add(R.string.message_certificate_domain_mismatch)
        }
        if (error.hasError(SslError.SSL_NOTYETVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_not_yet_valid)
        }
        if (error.hasError(SslError.SSL_UNTRUSTED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_untrusted)
        }
        if (error.hasError(SslError.SSL_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_invalid)
        }

        // Used this to test layout of multiple errors in dialog
        if (BuildConfig.DEBUG) {
            errorCodeMessageCodes.add(R.string.message_certificate_invalid)
        }

        return errorCodeMessageCodes
    }

    /**
     *
     *
     * See: https://developer.android.com/develop/ui/views/layout/webapps/managing-webview#termination-handle
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Timber.e("onRenderProcessGone")
        return webPageTab.onRenderProcessGone(view,detail)
    }

    /**
     * From [WebViewClient.doUpdateVisitedHistory]
     * Should we use this to build our history?
     * Though to be fair the system we had thus far seems to be working fine too.
     *
     * See: https://stackoverflow.com/a/56395424/3969362
     */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        Timber.d("$ihs : doUpdateVisitedHistory: $isReload - $url - ${view.url}")
        super.doUpdateVisitedHistory(view, url, isReload)

        if (webPageTab.lastUrl!=url) {
            webPageTab.lastUrl=url
            webBrowser.onTabChangedUrl(webPageTab)
        }
    }

    /**
     * Called when an HTTP error is received from the server.
     * HTTP errors have status codes >= 400.
     * This callback will be called for any resource (main page, image, subframe, etc.)
     */
    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        Timber.w("$ihs : onReceivedHttpError: ${request?.url} - Status: ${errorResponse?.statusCode}")
        super.onReceivedHttpError(view, request, errorResponse)
    }

    /**
     *
     */
    override fun onPageCommitVisible(view: WebView?, url: String?) {
        Timber.d("$ihs : onPageCommitVisible: $url")
        super.onPageCommitVisible(view, url)
    }

    /**
     *
     */
    override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
        Timber.d("$ihs : shouldOverrideKeyEvent: $event")
        return super.shouldOverrideKeyEvent(view, event)
    }

    /**
     *
     */
    override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {
        Timber.d("$ihs : onUnhandledKeyEvent: $event")
        super.onUnhandledKeyEvent(view, event)
    }

    /**
     *
     */
    override fun onReceivedLoginRequest(view: WebView?, realm: String?, account: String?, args: String?) {
        Timber.d("$ihs : onReceivedLoginRequest: $realm")
        super.onReceivedLoginRequest(view, realm, account, args)
    }

    /**
     *
     */
    override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
        Timber.d("$ihs : onSafeBrowsingHit: $threatType")
        super.onSafeBrowsingHit(view, request, threatType, callback)
    }
}

