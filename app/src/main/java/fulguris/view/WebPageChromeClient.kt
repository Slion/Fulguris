package fulguris.view

import fulguris.R
import fulguris.browser.WebBrowser
import fulguris.di.HiltEntryPoint
import fulguris.dialog.BrowserDialog
import fulguris.dialog.DialogItem
import fulguris.extensions.resizeAndShow
import fulguris.favicon.FaviconModel
import fulguris.settings.preferences.UserPreferences
import fulguris.view.webrtc.WebRtcPermissionsModel
import fulguris.view.webrtc.WebRtcPermissionsView
import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.webkit.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.anthonycr.grant.PermissionsManager
import com.anthonycr.grant.PermissionsResultAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.EntryPointAccessors
import io.reactivex.Scheduler
import timber.log.Timber

/**
 * We have one instance of this per [WebView].
 */
class WebPageChromeClient(
    private val activity: Activity,
    private val webPageTab: WebPageTab
) : WebChromeClient(),
    WebRtcPermissionsView {

    private val geoLocationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private val webBrowser: WebBrowser = activity as WebBrowser

    private val hiltEntryPoint = EntryPointAccessors.fromApplication(activity.applicationContext, HiltEntryPoint::class.java)
    val faviconModel: FaviconModel = hiltEntryPoint.faviconModel
    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val webRtcPermissionsModel: WebRtcPermissionsModel = hiltEntryPoint.webRtcPermissionsModel
    val diskScheduler: Scheduler = hiltEntryPoint.diskScheduler()

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        Timber.v("onProgressChanged: $newProgress")

        webBrowser.onProgressChanged(webPageTab, newProgress)

        // We don't need to run that when color mode is disabled
        if (userPreferences.colorModeEnabled) {
            if (newProgress > 10 && webPageTab.fetchMetaThemeColorTries > 0)
            {
                val triesLeft = webPageTab.fetchMetaThemeColorTries - 1
                webPageTab.fetchMetaThemeColorTries = 0

                // Extract meta theme-color
                Timber.w("evaluateJavascript: theme color extraction")
                view.evaluateJavascript("(function() { " +
                        "let e = document.querySelector('meta[name=\"theme-color\"]');" +
                        "if (e==null) return null;" +
                        "return e.content; })();") { themeColor ->
                    try {
                        webPageTab.htmlMetaThemeColor = Color.parseColor(themeColor.trim('\'').trim('"'));
                        // We did find a valid theme-color, tell our controller about it
                        webBrowser.onTabChanged(webPageTab)
                    }
                    catch (e: Exception) {
                        if (triesLeft==0 || newProgress==100)
                        {
                            // Exhausted all our tries or the page finished loading before we did
                            // Just give up then and reset our theme color
                            webPageTab.htmlMetaThemeColor = WebPageTab.KHtmlMetaThemeColorInvalid
                            webBrowser.onTabChanged(webPageTab)
                        }
                        else
                        {
                            // Try it again next time around
                            webPageTab.fetchMetaThemeColorTries = triesLeft
                        }
                    }
                }
            }
        }
    }

    /**
     * Called once the favicon is ready
     */
    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        Timber.d("onReceivedIcon")
        webPageTab.titleInfo.setFavicon(icon)
        webBrowser.onTabChangedIcon(webPageTab)
        cacheFavicon(view.url, icon)
    }

    /**
     * Naive caching of the favicon according to the domain name of the URL
     *
     * @param icon the icon to cache
     */
    private fun cacheFavicon(url: String?, icon: Bitmap?) {
        if (icon == null || url == null) {
            return
        }

        faviconModel.cacheFaviconForUrl(icon, url)
            .subscribeOn(diskScheduler)
            .subscribe()
    }

    /**
     *
     */
    override fun onReceivedTitle(view: WebView?, title: String?) {
        Timber.d("onReceivedTitle")
        if (title?.isNotEmpty() == true) {
            webPageTab.titleInfo.setTitle(title)
        } else {
            webPageTab.titleInfo.setTitle(activity.getString(R.string.untitled))
        }
        webBrowser.onTabChangedTitle(webPageTab)
        if (view != null && view.url != null) {
            webBrowser.updateHistory(title, view.url as String)
        }
    }

    /**
     * This is some sort of alternate favicon. F-Droid and Wikipedia have one for instance.
     * BBC has lots of them.
     * Possibly higher resolution than your typical favicon?
     */
    override fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) {
        Timber.d("onReceivedTouchIconUrl: $url")
        super.onReceivedTouchIconUrl(view, url, precomposed)
    }

    /**
     *
     */
    override fun onRequestFocus(view: WebView?) {
        Timber.d("onRequestFocus")
        super.onRequestFocus(view)
    }

    /**
     *
     */
    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // TODO: implement nicer dialog
        Timber.d("onJsAlert")
        return super.onJsAlert(view, url, message, result)
    }

    /**
     *
     */
    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // TODO: implement nicer dialog
        Timber.d("onJsConfirm")
        return super.onJsConfirm(view, url, message, result)
    }

    /**
     *
     */
    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        // TODO: implement nicer dialog
        Timber.d("onJsPrompt")
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }

    /**
     *
     */
    override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // TODO: implement nicer dialog
        Timber.d("onJsBeforeUnload")
        return super.onJsBeforeUnload(view, url, message, result)
    }

    /**
     *
     */
    override fun onJsTimeout(): Boolean {
        // Should never get there
        Timber.d("onJsTimeout")
        return super.onJsTimeout()
    }

    /**
     * From [WebRtcPermissionsView]
     */
    override fun requestPermissions(permissions: Set<String>, onGrant: (Boolean) -> Unit) {
        val missingPermissions = permissions
            // Filter out the permissions that we don't have
            .filter { !PermissionsManager.getInstance().hasPermission(activity, it) }

        if (missingPermissions.isEmpty()) {
            // We got all permissions already, notify caller then
            onGrant(true)
        } else {
            // Ask user for the missing permissions
            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(
                activity,
                missingPermissions.toTypedArray(),
                object : PermissionsResultAction() {
                    override fun onGranted() = onGrant(true)

                    override fun onDenied(permission: String?) = onGrant(false)
                }
            )
        }
    }

    /**
     * From [WebRtcPermissionsView]
     */
    override fun requestResources(source: String,
                                  resources: Array<String>,
                                  onGrant: (Boolean) -> Unit) {
        // Ask user to grant resource access
        activity.runOnUiThread {
            val resourcesString = resources.joinToString(separator = "\n")
            BrowserDialog.showPositiveNegativeDialog(
                aContext = activity,
                title = R.string.title_permission_request,
                message = R.string.message_permission_request,
                messageArguments = arrayOf(source, resourcesString),
                positiveButton = DialogItem(title = R.string.action_allow) { onGrant(true) },
                negativeButton = DialogItem(title = R.string.action_dont_allow) { onGrant(false) },
                onCancel = { onGrant(false) }
            )
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPermissionRequest(request: PermissionRequest) {
        Timber.d("onPermissionRequest")
        if (userPreferences.webRtcEnabled) {
            webRtcPermissionsModel.requestPermission(request, this)
        } else {
            //TODO: display warning message as snackbar I guess
            request.deny()
        }
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String,
                                                    callback: GeolocationPermissions.Callback) =
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(activity, geoLocationPermissions, object : PermissionsResultAction() {
            override fun onGranted() {
                val remember = true
                MaterialAlertDialogBuilder(activity).apply {
                    setTitle(activity.getString(R.string.location))
                    val org = if (origin.length > 50) {
                        "${origin.subSequence(0, 50)}..."
                    } else {
                        origin
                    }
                    setMessage(org + activity.getString(R.string.message_location))
                    setCancelable(true)
                    setPositiveButton(activity.getString(R.string.action_allow)) { _, _ ->
                        callback.invoke(origin, true, remember)
                    }
                    setNegativeButton(activity.getString(R.string.action_dont_allow)) { _, _ ->
                        callback.invoke(origin, false, remember)
                    }
                }.resizeAndShow()
            }

            override fun onDenied(permission: String) =//TODO show message and/or turn off setting
                Unit
        })

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        Timber.d("onCreateWindow")
        // TODO: redo that
        webBrowser.onCreateWindow(resultMsg)
        //TODO: surely that can't be right,
        return true
        //return false
    }

    override fun onCloseWindow(window: WebView) {
        Timber.d("onCloseWindow")
        webBrowser.onCloseWindow(webPageTab)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun openFileChooser(uploadMsg: ValueCallback<Uri>) = webBrowser.openFileChooser(uploadMsg)

    @Suppress("unused", "UNUSED_PARAMETER")
    fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String) =
        webBrowser.openFileChooser(uploadMsg)

    @Suppress("unused", "UNUSED_PARAMETER")
    fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) =
        webBrowser.openFileChooser(uploadMsg)

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                                   fileChooserParams: FileChooserParams): Boolean {
        webBrowser.showFileChooser(filePathCallback)
        return true
    }

    /**
     * Obtain an image that is displayed as a placeholder on a video until the video has initialized
     * and can begin loading.
     *
     * @return a Bitmap that can be used as a place holder for videos.
     */
    override fun getDefaultVideoPoster(): Bitmap? {
        Timber.d("getDefaultVideoPoster")
        // TODO: In theory we could even load site specific icons here or just tint that drawable using the site theme color
        val bitmap = AppCompatResources.getDrawable(activity, R.drawable.ic_filmstrip)?.toBitmap(1024,1024)
        if (bitmap==null) {
            Timber.d("Failed to load video poster")
        }
        return bitmap
    }

    /**
     * Inflate a view to send to a [WebPageTab] when it needs to display a video and has to
     * show a loading dialog. Inflates a progress view and returns it.
     *
     * @return A view that should be used to display the state
     * of a video's loading progress.
     */
    override fun getVideoLoadingProgressView(): View {
        // Not sure that's ever being used anymore
        Timber.d("getVideoLoadingProgressView")
        return LayoutInflater.from(activity).inflate(R.layout.video_loading_progress, null)
    }


    override fun onHideCustomView() {
        Timber.d("onHideCustomView")
        webBrowser.onHideCustomView()
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        Timber.d("onShowCustomView")
        webBrowser.onShowCustomView(view, callback)
    }


    override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) {
        Timber.d("onShowCustomView: $requestedOrientation")
        webBrowser.onShowCustomView(view, callback, requestedOrientation)
    }


    /**
     * Needed to display javascript console message in logcat.
     */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        // TODO: Collect those in the tab so that we could display them
        consoleMessage?.apply {
            val tag = "JavaScript";
            val log = "${messageLevel()} - ${message()} -- from line ${lineNumber()} of ${sourceId()}"
            when (messageLevel()) {
                ConsoleMessage.MessageLevel.DEBUG -> Timber.tag(tag).d(log)
                ConsoleMessage.MessageLevel.WARNING -> Timber.tag(tag).w(log)
                ConsoleMessage.MessageLevel.ERROR -> Timber.tag(tag).e(log)
                ConsoleMessage.MessageLevel.TIP -> Timber.tag(tag).i(log)
                ConsoleMessage.MessageLevel.LOG -> Timber.tag(tag).v(log)
                null -> Timber.tag(tag).d(log)
            }
        }
        return true
    }

}
