package acr.browser.lightning.browser

import acr.browser.lightning.ssl.SslState
import android.view.View
import androidx.annotation.StringRes

/**
 * TODO: Find a proper name for that class
 * Though I guess that would mean sorting out our so called BrowserPresenter too.
 */
interface BrowserView {

    /**
     * Called when our current tab view needs to be changed.
     * Implementer typically will remove the currently bound tab view and hook the one provided here.
     *
     * [aView] is in fact a WebViewEx however this could change.
     * [aWasTabAdded] True if [aView] is a newly created tab.
     * [aPreviousTabClosed] True if the current foreground tab [aView] will replaced was closed.
     * [aGoingBack] True if we are going back rather than forward in our tab cycling.
     */
    fun setTabView(aView: View, aWasTabAdded: Boolean, aPreviousTabClosed: Boolean, aGoingBack: Boolean)

    fun updateUrl(url: String?, isLoading: Boolean)

    fun updateProgress(progress: Int)

    fun updateTabNumber(number: Int)

    /**
     * TODO: Define both in BrowserView and UIController
     * Sort out that mess.
     */
    fun updateSslState(sslState: SslState)

    fun closeBrowser()

    fun closeActivity()

    fun showBlockedLocalFileDialog(onPositiveClick: () -> Unit)

    fun showSnackbar(@StringRes resource: Int)

    fun setForwardButtonEnabled(enabled: Boolean)

    fun setBackButtonEnabled(enabled: Boolean)

    fun notifyTabViewRemoved(position: Int)

    fun notifyTabViewAdded()

    fun notifyTabViewChanged(position: Int)

    fun notifyTabViewInitialized()

    /**
     * Triggered whenever user reaches the maximum amount of tabs allowed by her license.
     * Should typically display a message warning the user about it.
     */
    fun onMaxTabReached()

    /**
     * Set the browser address bar text.
     */
    fun setAddressBarText(aText: String)

}
