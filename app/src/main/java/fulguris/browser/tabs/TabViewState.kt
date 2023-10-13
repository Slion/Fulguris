package fulguris.browser.tabs

import fulguris.view.WebPageTab
import android.graphics.Bitmap
import android.graphics.Color
import timber.log.Timber

/**
 * @param id The unique id of the tab.
 * @param title The title of the tab.
 * @param favicon The favicon of the tab.
 * @param isForeground True if the tab is in the foreground, false otherwise.
 */
data class TabViewState(
    val id: Int = 0,
    val title: String = "",
    val favicon: Bitmap = createDefaultBitmap(),
    val isForeground: Boolean = false,
    val themeColor: Int = Color.TRANSPARENT,
    val isFrozen: Boolean = true
) {
    init {
        // TODO: This is called way too many times from displayTabs() through asTabViewState
        // Find a way to improve this
        //Timber.v("init")
    }
}

/**
 * We used a function to be able to log
 */
private fun createDefaultBitmap() : Bitmap {
    Timber.w("createDefaultBitmap - ideally that should never be called")
    return Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
}


/**
 * Converts a [WebPageTab] to a [TabViewState].
 */
fun WebPageTab.asTabViewState() = TabViewState(
    id = id,
    title = title,
    favicon = favicon,
    isForeground = isForeground,
    themeColor = htmlMetaThemeColor,
    isFrozen = isFrozen
)
