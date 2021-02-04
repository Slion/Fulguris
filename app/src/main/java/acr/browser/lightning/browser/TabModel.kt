package acr.browser.lightning.browser
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import java.io.ByteArrayOutputStream

/**
 * Tab model used to create a bundle from our tab.
 */
open class TabModel (
    var url : String,
    var title : String,
    var desktopMode: Boolean,
    var favicon : Bitmap?,
    var webView : Bundle?
)
{
    fun toBundle() : Bundle {
        return Bundle(ClassLoader.getSystemClassLoader()).let {
                it.putString(URL_KEY, url)
                it.putString(TAB_TITLE_KEY, title)
                it.putBundle(WEB_VIEW_KEY, webView)
                it.putBoolean(KEY_DESKTOP_MODE, desktopMode)
                favicon?.apply {
                    // Using PNG instead of WEBP as it is hopefully lossless
                    // Using WEBP results in the quality degrading reload after reload
                    // Maybe consider something like: https://stackoverflow.com/questions/8065050/convert-bitmap-to-byte-array-without-compress-method-in-android
                    val stream = ByteArrayOutputStream()
                    compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray = stream.toByteArray()
                    it.putByteArray(TAB_FAVICON_KEY, byteArray)
                }
                it
            }
        }

    companion object {
        const val KEY_DESKTOP_MODE = "DESKTOP_MODE"
        const val URL_KEY = "URL"
        const val TAB_TITLE_KEY = "TITLE"
        const val TAB_FAVICON_KEY = "FAVICON"
        const val WEB_VIEW_KEY = "WEB_VIEW"
    }
}

/**
 * Used to create a Tab Model from a bundle.
 */
class TabModelFromBundle (
        var bundle : Bundle
): TabModel(
        bundle.getString(URL_KEY)?:"",
        bundle.getString(TAB_TITLE_KEY)?:"",
        bundle.getBoolean(KEY_DESKTOP_MODE)?:false,
        bundle.getByteArray(TAB_FAVICON_KEY)?.let{BitmapFactory.decodeByteArray(it, 0, it.size)},
        bundle.getBundle(WEB_VIEW_KEY)
)
