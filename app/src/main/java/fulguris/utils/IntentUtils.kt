package fulguris.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.MailTo
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import fulguris.BuildConfig
import fulguris.R
import fulguris.constant.INTENT_ORIGIN
import timber.log.Timber
import java.io.File
import java.net.URISyntaxException
import java.util.regex.Pattern

class IntentUtils(private val mActivity: Activity) {

    /**
     *
     * @param tab
     * @param url
     * @return
     */
    fun intentForUrl(tab: WebView?, url: String): Intent? {
        val intent: Intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (ex: URISyntaxException) {
            Timber.w("Browser", "Bad URI " + url + ": " + ex.message)
            return null
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.setComponent(null)
        intent.selector = null
        if (tab != null) {
            intent.putExtra(INTENT_ORIGIN, tab.hashCode())
        }
        return if (ACCEPTED_URI_SCHEMA.matcher(url).matches() && !isSpecializedHandlerAvailable(intent)) {
            null
        } else intent
    }

    /**
     *
     */
    private fun handleUnresolvableIntent(intent: Intent?): Intent? {
        val packageName = intent!!.getPackage()
        if (packageName != null) {
            val marketUri = Uri.parse("market://search?q=pname:$packageName")
            val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
            marketIntent.addCategory(Intent.CATEGORY_BROWSABLE)
            return marketIntent
        }
        return null
    }

    /**
     *
     */
    private fun startActivityForIntent(aIntent: Intent?): Boolean {
        var intent = aIntent
        if (mActivity.packageManager.resolveActivity(intent!!, 0) == null) {
            intent = handleUnresolvableIntent(intent)
        }
        try {
            if (mActivity.startActivityIfNeeded(intent!!, -1)) {
                return true
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            // TODO: 6/5/17 fix case where this could throw a FileUriExposedException due to file:// urls
        }
        return false
    }

    /**
     *
     */
    fun startActivityForUrl(tab: WebView?, url: String): Boolean {
        val intent = intentForUrl(tab, url)
        return startActivityForIntent(intent)
    }

    /**
     *
     */
    fun startActivityWithFallback(tab: WebView?, intent: Intent, onlyFallback: Boolean): Boolean {
        if (!onlyFallback && startActivityForIntent(intent)) {
            Timber.d("Intent successfully started.")
            // Intent was successfully handled
            return true
        } else {
            Timber.d("Failed to start intent, checking for fallback URL.")
            // Check for a fallback URL if the intent could not be started
            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
            if (!fallbackUrl.isNullOrEmpty()) {
                Timber.d("Fallback URL found: $fallbackUrl")
                tab?.loadUrl(fallbackUrl)
                return true
            }
            Timber.d("No fallback URL found.")
        }
        return false
    }

    /**
     * Search for intent handlers that are specific to this URL aka, specialized
     * apps like google maps or youtube
     */
    private fun isSpecializedHandlerAvailable(intent: Intent): Boolean {
        val pm = mActivity.packageManager
        val handlers = pm.queryIntentActivities(
            intent,
            PackageManager.GET_RESOLVED_FILTER
        )
        if (handlers.isEmpty()) {
            return false
        }
        for (resolveInfo in handlers) {
            val filter = resolveInfo.filter
                ?: // No intent filter matches this intent?
                // Error on the side of staying in the browser, ignore
                continue
            // NOTICE: Use of && instead of || will cause the browser
            // to launch a new intent for every URL, using OR only
            // launches a new one if there is a non-browser app that
            // can handle it.
            // Previously we checked the number of data paths, but it is unnecessary
            // filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0
            if (filter.countDataAuthorities() == 0) {
                // Generic handler, skip
                continue
            }
            return true
        }
        return false
    }

    /**
     * Handles URLs with special schemes such as mailto, tel, and intent by creating
     * and returning an appropriate Intent based on the scheme. This method also handles
     * file URLs by creating an Intent to open the file. If the URL does not match any
     * special scheme or if an error occurs (e.g., URISyntaxException), null is returned.
     *
     * @param activity The Activity context used to access package manager and resources.
     * @param url The URL to be handled. It can be a special scheme URL or a file URL.
     * @param view The WebView that may be used for additional context or actions.
     * @return An Intent that corresponds to the action required by the URL's scheme,
     * or null if the URL does not match a special scheme or an error occurs.
     */
    fun handleSpecialSchemes(activity: Activity?, url: String, view: WebView?): Intent? {
        val uri = Uri.parse(url)
        Timber.d("Handling special schemes for URL: $url")
        val scheme = uri.scheme!!.lowercase()
        Timber.d("Detected scheme: $scheme")
        return when (scheme) {
            "mailto" -> {
                Timber.d("Detected mailto scheme.")
                val mailTo = MailTo.parse(url)
                Utils.newEmailIntent(mailTo.to, mailTo.subject, mailTo.body, mailTo.cc)
            }

            "tel" -> {
                Timber.d("Detected tel scheme.")
                val intentTel = Intent(Intent.ACTION_DIAL)
                intentTel.setData(Uri.parse(url))
                intentTel
            }

            "intent" -> {
                return try {
                    Timber.d("Detected intent scheme.")
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.setComponent(null)
                    intent.selector = null
                    intent
                } catch (e: URISyntaxException) {
                    Timber.e("URISyntaxException for URL: $url", e)
                    null
                }
            }

            else -> {
                if (URLUtil.isFileUrl(url) && !url.isSpecialUrl()) {
                    Timber.d("Detected file URL.")
                    val file = File(url.replace("file://", ""))
                    return if (file.exists()) {
                        val newMimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(Utils.guessFileExtension(file.toString()))
                        val intentFile = Intent(Intent.ACTION_VIEW)
                        intentFile.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val contentUri = FileProvider.getUriForFile(activity!!, BuildConfig.APPLICATION_ID + ".fileprovider", file)
                        intentFile.setDataAndType(contentUri, newMimeType)
                        intentFile
                    } else {
                        Timber.d("File not found for URL: $url")
                        null
                    }
                }
                Timber.d("No special handling required for URL: $url")
                null
            }
        }
    }

    /**
     * Shares a URL to the system.
     *
     * @param url   the URL to share. If the URL is null
     * or a special URL, no sharing will occur.
     * @param title the title of the URL to share. This
     * is optional.
     */
    fun shareUrl(url: String?, title: String?, @StringRes aTitleId: Int = R.string.dialog_title_share) {
        if (url != null && !url.isSpecialUrl()) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain")
            if (title != null) {
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, url)
            mActivity.startActivity(Intent.createChooser(shareIntent, mActivity.getString(aTitleId)))
        }
    }

    companion object {
        private val ACCEPTED_URI_SCHEMA = Pattern.compile(
            "(?i)"
                    +  // switch on case insensitive matching
                    '('
                    +  // begin group for schema
                    "(?:http|https|file)://" + "|(?:inline|data|about|javascript):" + "|(?:.*:.*@)"
                    + ')' + "(.*)"
        )
    }
}
