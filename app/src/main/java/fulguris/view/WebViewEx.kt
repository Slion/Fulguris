package fulguris.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebView
import androidx.annotation.ColorInt
import androidx.core.text.parseAsHtml
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import fulguris.R
import fulguris.di.configPrefs
import fulguris.extensions.ihs
import fulguris.extensions.snackbar
import android.view.Gravity
import timber.log.Timber

/**
 * Specialising WebView could be useful at some point.
 * We may want to get rid of [WebPageTab].
 *
 * We used that to try debug our issue with ALT + TAB scrolling back to the top of the page.
 * We could not figure out that issue though.
 */
class WebViewEx : WebView {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initNestedScrollDetection()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initNestedScrollDetection()
    }

    //
    lateinit var proxy: WebPageTab

    /**
     * True when the current touch gesture started on a nested CSS scrollable element
     * (e.g. a div with overflow:auto/scroll whose content overflows).
     * Set from JavaScript via [NestedScrollBridge] on `touchstart`, reset on `touchend`/`touchcancel`.
     * Read from [PullRefreshLayout.canChildScrollUp] on the UI thread.
     */
    @Volatile
    var isTouchOnNestedScrollable: Boolean = false

    @SuppressLint("JavascriptInterface")
    private fun initNestedScrollDetection() {
        addJavascriptInterface(NestedScrollBridge(), NESTED_SCROLL_JS_INTERFACE)
        addJavascriptInterface(BlobDownloadBridge(), BLOB_JS_INTERFACE)
    }

    /**
     * Set this callback before calling evaluateJavascript with the blob-reading script.
     * It will be invoked on the main thread with the data URL ("data:<mime>;base64,...")
     * and the filename captured from the HTML5 download attribute (may be empty).
     */
    var onBlobDownload: ((dataUrl: String, filename: String) -> Unit)? = null

    /**
     * Map of blob: URL → filename, populated by [BlobDownloadBridge.onFilename]
     * when BlobHook.js intercepts a programmatic anchor click with a download attribute.
     * Read (and consumed) by [LightningDownloadListener] to show the correct name in the dialog.
     */
    val blobFilenames: MutableMap<String, String> = mutableMapOf()

    /**
     * Set to true when the user confirmed a blob download via the early dialog shown
     * from [BlobDownloadBridge.onConfirmDownload]. [LightningDownloadListener] checks
     * this to skip showing a duplicate dialog when onDownloadStart fires.
     */
    @Volatile
    var blobDownloadPreConfirmed: Boolean = false

    /**
     * JavaScript interface that lets injected page scripts tell us whether the current
     * touch gesture started on a nested scrollable CSS element.
     */
    inner class NestedScrollBridge {
        @JavascriptInterface
        fun setNestedScrollable(value: Boolean) {
            isTouchOnNestedScrollable = value
        }
    }

    /**
     * JavaScript interface for receiving blob data URLs produced by the blob-download script.
     */
    inner class BlobDownloadBridge {
        @JavascriptInterface
        fun onData(dataUrl: String, filename: String) {
            val cb = onBlobDownload ?: return
            onBlobDownload = null
            Handler(Looper.getMainLooper()).post { cb(dataUrl, filename) }
        }

        @JavascriptInterface
        fun onError(message: String) {
            onBlobDownload = null
            Timber.w("Blob download JS error: %s", message)
        }

        /**
         * Called by BlobHook.js when it intercepts a click on an anchor with a blob: href
         * and a download attribute. This fires BEFORE onDownloadStart, allowing the
         * download dialog to show the correct filename.
         */
        @JavascriptInterface
        fun onFilename(blobUrl: String, filename: String) {
            synchronized(blobFilenames) {
                blobFilenames[blobUrl] = filename
            }
        }

        /**
         * Called by BlobHook.js when a large Response.blob() download is in progress.
         * Reports progress (loaded/total bytes) so the browser can show a progress bar.
         */
        @JavascriptInterface
        fun onProgress(loaded: Long, total: Long) {
            val percent = if (total > 0) (loaded * 100 / total).toInt().coerceIn(0, 100) else 0
            Handler(Looper.getMainLooper()).post {
                (context as? fulguris.activity.WebBrowserActivity)?.onBlobDownloadProgress(percent)
            }
        }

        /**
         * Called by BlobHook.js when a Response with Content-Disposition: attachment
         * is about to be read as a blob. Shows the download confirmation dialog
         * BEFORE the body is fetched, so the user can decide immediately.
         *
         * @param callbackId  JS callback identifier (window[callbackId](true/false))
         * @param filename    filename extracted from headers or URL
         * @param totalBytes  body size as a string (to avoid JS number issues)
         * @param contentType MIME type from the response
         */
        @JavascriptInterface
        fun onConfirmDownload(callbackId: String, filename: String, totalBytes: String, contentType: String) {
            // Validate callbackId to prevent JS injection
            if (!callbackId.matches(Regex("^_bc\\d+$"))) return

            val size = totalBytes.toLongOrNull() ?: 0L
            val sizeStr = if (size > 0) Formatter.formatFileSize(context, size)
                else context.getString(R.string.unknown_file_size)
            val displayName = filename.ifBlank { "download" }

            // Detect file type for display
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val fileType = if (contentType.isNotBlank() && contentType != "application/octet-stream") {
                contentType
            } else if (ext.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: contentType.ifBlank { context.getString(R.string.unknown_file_type) }
            } else {
                contentType.ifBlank { context.getString(R.string.unknown_file_type) }
            }

            val message = context.getString(R.string.dialog_download_message, displayName, fileType, sizeStr)

            Handler(Looper.getMainLooper()).post {
                MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_download_outline)
                    .setTitle(R.string.dialog_download_title)
                    .setMessage(message.parseAsHtml())
                    .setPositiveButton(R.string.action_download) { _, _ ->
                        blobDownloadPreConfirmed = true
                        // Show pending snackbar immediately, before the body is fetched
                        (context as? android.app.Activity)?.let { activity ->
                            val gravity = if (activity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                            activity.snackbar(activity.getString(R.string.download_pending) + ' ' + displayName, gravity)
                        }
                        evaluateJavascript("window['$callbackId'](true)", null)
                    }
                    .setNegativeButton(R.string.action_cancel) { _, _ ->
                        evaluateJavascript("window['$callbackId'](false)", null)
                    }
                    .setOnCancelListener {
                        evaluateJavascript("window['$callbackId'](false)", null)
                    }
                    .show()
            }
        }
    }

    companion object {
        const val NESTED_SCROLL_JS_INTERFACE = "_fulgurisScroll"
        const val BLOB_JS_INTERFACE = "_fulgurisBlobDownload"
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {

        /*
        if (event?.keyCode == KeyEvent.KEYCODE_TAB) {
            Log.v("WebViewEx","Tab: " + event.action.toString())
        }
        */

        return super.dispatchKeyEvent(event)
    }

    /**
     * We use that to debug our beautiful color mess.
     */
    override fun setBackgroundColor(@ColorInt color: Int) {
        super<WebView>.setBackgroundColor(color)
    }


    /**
     * Start a print job, thus notably enabling saving a web page as PDF.
     */
    fun print() : PrintJob {
        val printManager: PrintManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAdapter: PrintDocumentAdapter = createPrintDocumentAdapter(title as String)
        val jobName = title as String
        val builder: PrintAttributes.Builder = PrintAttributes.Builder()
        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        return printManager.print(jobName, printAdapter, builder.build())
    }

    /**
     * We shied away from overriding [WebView.destroy] so here we have that function instead.
     */
    private fun destruction() {
        Timber.d("destruction")
        stopLoading()
        onPause()
        clearHistory()
        visibility = View.GONE
        removeAllViews()
        destroyDrawingCache()
        destroy()
    }

    /**
     * Tell if this object should be destroyed.
     */
    private var iNeedDestruction = false

    /**
     * Does just that.
     */
    fun destroyIfNeeded() {
        Timber.d("destroyIfNeeded: $iNeedDestruction")
        if (iNeedDestruction) {
            destruction()
        }
    }

    /**
     * Destroy self if no parent else mark this as needing destruction.
     * This is was needed to accommodate for WebView animation.
     */
    fun autoDestruction() {
        Timber.d("autoDestruction: $parent")
        if (parent!=null) {
            // There is probably still an animation running
            iNeedDestruction = true
        } else {
            destruction()
        }
    }

    /**
     *
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        //Timber.d("onGenericMotionEvent $event")
        return super.onGenericMotionEvent(event)
    }

    /**
     *
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        //Timber.d("onTouchEvent $event")
        return super.onTouchEvent(event)
    }

    /**
     *
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        //Timber.d("dispatchGenericMotionEvent $event")
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     *
     */
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        //Timber.d("dispatchTouchEvent $event")
        return super.dispatchTouchEvent(event)
    }

    /**
     * Overrides [WebView.loadUrl(String)].
     */
    override fun loadUrl(url: String) {
        Timber.i("$ihs : loadUrl: $url")
        super.loadUrl(url)
    }

    /**
     * Overrides [WebView.loadUrl(String, Map)].
     */
    override fun loadUrl(url: String, additionalHttpHeaders: MutableMap<String, String>) {
        Timber.i("$ihs : loadUrl: $url (${additionalHttpHeaders.size} headers)")
        super.loadUrl(url, additionalHttpHeaders)
    }

}