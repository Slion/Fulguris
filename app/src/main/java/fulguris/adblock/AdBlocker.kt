package fulguris.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * The ad blocking interface.
 */
interface AdBlocker {

    /**
     * a method that determines if the given URL is an ad or not. It performs a search of the URL's
     * domain on the blocked domain hash set.
     *
     * @param url the URL to check for being an ad.
     * @return true if it is an ad, false if it is not an ad.
     */
    // not used in the new blocker
    //fun isAd(url: String): Boolean

    // this is for element hiding only, which is currently not working and disabled
    //fun loadScript(uri: Uri): String?

    fun shouldBlock(request: WebResourceRequest, pageUrl: String): WebResourceResponse?
}
