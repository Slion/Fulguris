package acr.browser.lightning.browser

import acr.browser.lightning.ssl.SslState
import android.view.View
import androidx.annotation.StringRes

interface BrowserView {

    fun setTabView(view: View)

    fun removeTabView()

    fun updateUrl(url: String?, isLoading: Boolean)

    fun updateProgress(progress: Int)

    fun updateTabNumber(number: Int)

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

}
