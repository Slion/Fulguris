package acr.browser.lightning.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView

/**
 * Specialising  WebView could be useful at some point.
 * We may want to get rid of LightningView.
 *
 * We used that to try debug our issue with ALT + TAB scrolling back to the top of the page.
 * We could not figure out that issue though.
 */
class WebViewEx : WebView {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {

        /*
        if (event?.keyCode == KeyEvent.KEYCODE_TAB) {
            Log.v("WebViewEx","Tab: " + event.action.toString())
        }
        */

        return super.dispatchKeyEvent(event)
    }

}