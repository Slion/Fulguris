package fulguris.view

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.annotation.ColorInt
import timber.log.Timber

/**
 * Specialising  WebView could be useful at some point.
 * We may want to get rid of [WebPageTab].
 *
 * We used that to try debug our issue with ALT + TAB scrolling back to the top of the page.
 * We could not figure out that issue though.
 */
class WebViewEx : WebView {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {

    }

    //
    lateinit var proxy: WebPageTab

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

}