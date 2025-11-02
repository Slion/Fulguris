package fulguris.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.MailTo
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import fulguris.BuildConfig
import fulguris.R
import timber.log.Timber
import java.io.File
import java.net.URISyntaxException


    /**
     * Provide the intent corresponding to the give URL.
     * Returns null if the candidate intent would do noop such as when the corresponding app is not available.
     * Exclusively used to launch app associated with a given URL.
     * Notably needed to make hooks with Google login work if I recall well.
     *
     * @param tab
     * @param url
     * @return
     */
    fun Activity.intentForUrl(tab: WebView?, uri: Uri): Intent? {

        // If it's a special scheme provide it
        intentForScheme(uri)?.let {return it}

        // For simple http/https URLs, create a clean intent like Chrome does
        // instead of using Intent.parseUri which might add unwanted extras
        val intent = if (uri.scheme == "http" || uri.scheme == "https") {
            Timber.d("intentForUrl: Creating simple VIEW intent for http(s) URL")
            Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                // Don't add EXTRA_REFERRER or NEW_TASK flags as they might cause GitHub app
                // to show browser picker instead of handling the OAuth directly
            }
        } else {
            // For other schemes, use parseUri to handle intent:// URLs properly
            try {
                Timber.d("intentForUrl: Using parseUri for non-http(s) URL")
                val parsedIntent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                parsedIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                parsedIntent.setComponent(null)
                parsedIntent.selector = null
                parsedIntent
            } catch (ex: URISyntaxException) {
                Timber.w(ex, "Bad URI: $uri")
                return null
            }
        }

        Timber.d("intentForUrl: Intent details:")
        Timber.d("  Action: ${intent.action}")
        Timber.d("  Data: ${intent.data}")
        Timber.d("  Type: ${intent.type}")
        Timber.d("  Package: ${intent.`package`}")
        Timber.d("  Component: ${intent.component}")
        Timber.d("  Categories: ${intent.categories}")
        Timber.d("  Flags: ${intent.flags}")
        Timber.d("  Extras: ${intent.extras?.keySet()?.joinToString()}")

        // Allows us to not ask to launch an app that's not installed on the device
        // To test it you could for instance visit https://t.me/durov while Telegram app is not installed and launch app option set to ASK
        // Skipping HTTP and HTTPS schemes can be useful to debug but is not the expected behaviour
        return if (/*uri.scheme=="http" || uri.scheme=="https" ||*/ !isSpecializedHandlerAvailable(intent)) {
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
    private fun Activity.startActivityForIntent(aIntent: Intent?): Boolean {
        var intent = aIntent
        Timber.d("startActivityForIntent: intent=${intent?.data}")

        if (packageManager.resolveActivity(intent!!, 0) == null) {
            Timber.d("startActivityForIntent: No activity found to handle intent, trying fallback")
            intent = handleUnresolvableIntent(intent)
        }

        try {
            // Use regular startActivity() instead of startActivityIfNeeded() to match Chrome's behavior
            // startActivityIfNeeded() only starts if the current activity can't handle it,
            // which might confuse apps like GitHub that check the calling activity
            Timber.d("startActivityForIntent: Launching activity with startActivity()")
            startActivity(intent!!)
            return true
        } catch (exception: Exception) {
            Timber.e(exception, "startActivityForIntent: Failed to start activity")
            exception.printStackTrace()
            // TODO: 6/5/17 fix case where this could throw a FileUriExposedException due to file:// urls
        }
        return false
    }

    /**
     *
     */
    fun Activity.startActivityForUrl(tab: WebView?, url: Uri): Boolean {
        val intent = intentForUrl(tab, url)
        return startActivityForIntent(intent)
    }

    /**
     * TODO: Review and test that fallback logic
     */
    fun Activity.startActivityWithFallback(tab: WebView?, intent: Intent, onlyFallback: Boolean): Boolean {
        Timber.d("startActivityWithFallback")
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
     * Search for intent handlers that are specific to this URL, such as specialized apps like google maps or youtube.
     * Notably avoid asking user if she wants to launch an app when we don't have one.
     * Filters out browsers to only detect truly specialized handlers.
     */
    private fun Activity.isSpecializedHandlerAvailable(intent: Intent): Boolean {
        Timber.d("isSpecializedHandlerAvailable: Checking intent for URL: ${intent.data}")
        val pm = packageManager
        val handlers = pm.queryIntentActivities(
            intent,
            PackageManager.GET_RESOLVED_FILTER
        )
        Timber.d("isSpecializedHandlerAvailable: Found ${handlers.size} handler(s)")
        if (handlers.isEmpty()) {
            Timber.d("isSpecializedHandlerAvailable: No handlers found, returning false")
            return false
        }

        // Create a test intent to identify browsers
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
        val browserResolveInfos = pm.queryIntentActivities(browserIntent, 0)
        Timber.d("isSpecializedHandlerAvailable: Found ${browserResolveInfos.size} apps that handle http://")
        browserResolveInfos.forEach {
            Timber.d("isSpecializedHandlerAvailable: Browser candidate: ${it.activityInfo.packageName}/${it.activityInfo.name}")
        }
        val browsers = browserResolveInfos
            .map { it.activityInfo.packageName }
            .toSet()
        Timber.d("isSpecializedHandlerAvailable: Identified ${browsers.size} unique browser package(s): $browsers")

        for (resolveInfo in handlers) {
            val packageName = resolveInfo.activityInfo.packageName
            val activityName = resolveInfo.activityInfo.name
            Timber.d("isSpecializedHandlerAvailable: Checking handler: $packageName/$activityName")

            val filter = resolveInfo.filter
            if (filter == null) {
                Timber.d("isSpecializedHandlerAvailable: No filter for $packageName, skipping")
                continue
            }

            Timber.d("isSpecializedHandlerAvailable: Filter has ${filter.countDataAuthorities()} authorities, ${filter.countDataPaths()} paths")

            // Skip if this is a browser
            if (packageName in browsers) {
                Timber.d("isSpecializedHandlerAvailable: $packageName is a browser, skipping")
                continue
            }

            // NOTICE: Use of && instead of || will cause the browser
            // to launch a new intent for every URL, using OR only
            // launches a new one if there is a non-browser app that
            // can handle it.
            // Previously we checked the number of data paths, but it is unnecessary
            // filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0
            if (filter.countDataAuthorities() == 0) {
                Timber.d("isSpecializedHandlerAvailable: $packageName has no authorities (generic handler), skipping")
                continue
            }

            Timber.d("isSpecializedHandlerAvailable: Found specialized handler: $packageName/$activityName, returning true")
            return true
        }
        Timber.d("isSpecializedHandlerAvailable: No specialized non-browser handlers found, returning false")
        return false
    }

    /**
     * Handles URLs with special schemes such as mailto, tel, intent and file by
     * providing corresponding Intent. If the URL does not match any
     * special scheme or if an error occurs (e.g., URISyntaxException), null is returned.
     *
     * @param url The URL to be handled. It can be a special scheme URL or a file URL.
     * @return An Intent that corresponds to the action required by the URL's scheme,
     * or null if the URL does not match a special scheme or an error occurs.
     */
    private fun Activity.intentForScheme(uri: Uri): Intent? {
        val url = uri.toString()
        Timber.d("Handling special schemes for URL: $uri")
        val scheme = uri.scheme!!.lowercase()
        Timber.d("Detected scheme: $scheme")
        return when (scheme) {
            "mailto" -> {
                Timber.d("Detected mailto scheme")
                val mailTo = MailTo.parse(url)
                Utils.newEmailIntent(mailTo.to, mailTo.subject, mailTo.body, mailTo.cc)
            }

            "tel" -> {
                Timber.d("Detected tel scheme")
                Intent(Intent.ACTION_DIAL).setData(uri)
            }

            "intent" -> {
                return try {
                    Timber.d("Detected intent scheme")
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.setComponent(null)
                    intent.selector = null
                    intent
                } catch (e: URISyntaxException) {
                    Timber.e(e,"URISyntaxException for URL: $url")
                    null
                }
            }

            else -> {
                if (URLUtil.isFileUrl(url) && !url.isSpecialUrl()) {
                    Timber.d("Detected file URL")
                    val file = File(url.replace("file://", ""))
                    return if (file.exists()) {
                        val newMimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(Utils.guessFileExtension(file.toString()))
                        val intentFile = Intent(Intent.ACTION_VIEW)
                        intentFile.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file)
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
    fun Activity.shareUrl(url: String?, title: String?, @StringRes aTitleId: Int = R.string.dialog_title_share) {
        if (url != null && !url.isSpecialUrl()) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain")
            if (title != null) {
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, url)
            startActivity(Intent.createChooser(shareIntent, getString(aTitleId)))
        }
    }


