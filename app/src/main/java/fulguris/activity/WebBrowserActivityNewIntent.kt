package fulguris.activity

import android.app.SearchManager
import android.content.Intent
import android.view.Gravity
import android.webkit.URLUtil
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import fulguris.R
import fulguris.app
import fulguris.constant.INTENT_ORIGIN
import fulguris.di.configPrefs
import fulguris.enums.IncomingUrlAction
import fulguris.extensions.log
import fulguris.extensions.makeSnackbar
import fulguris.extensions.topPrivateDomain
import fulguris.settings.preferences.DomainPreferences
import fulguris.utils.QUERY_PLACE_HOLDER
import fulguris.utils.extractUrlFromText
import fulguris.utils.smartUrlFilter
import fulguris.view.UrlInitializer
import timber.log.Timber

/**
 * Returns the URL for a search [Intent].
 * If the query is empty, then a null URL will be returned.
 */
fun WebBrowserActivity.extractSearchFromIntent(intent: Intent): String? {
    val query = intent.getStringExtra(SearchManager.QUERY)
    val searchUrl = "${searchEngineProvider.provideSearchEngine().queryUrl}$QUERY_PLACE_HOLDER"

    return if (query?.isNotBlank() == true) {
        smartUrlFilter(query, true, searchUrl).first
    } else {
        null
    }
}

/**
 * Handle a new intent from the the main BrowserActivity.
 * TODO: That implementation is so ugly… try and improve that.
 * @param aIntent the intent to handle, may be null.
 * @param aIncognitoStartup True if the intent is received at incognito startup, meaning we need to close the initial tab.
 */
fun WebBrowserActivity.doOnNewIntent(aIntent: Intent?, aIncognitoStartup: Boolean = false)  {
    // Log intent details for debugging
    aIntent?.log("doOnNewIntent")

    var subject: String = app.getString(R.string.unknown)

    // Obtain a URL from the intent
    val url = if (aIntent?.action == Intent.ACTION_WEB_SEARCH) {
        // User performed a web search
        // Build search URL according to user preferences
        extractSearchFromIntent(aIntent)
    }
    else if (aIntent?.action == Intent.ACTION_SEND) {
        // TODO: Check if we received a valid URL, if so check the domain settings options
        // If it is not a URL we could fallback to a search I guess
        // See: https://github.com/Slion/Fulguris/issues/710
        // See: https://github.com/Slion/Fulguris/issues/628
        // User shared text with our app
        if ("text/plain" == aIntent.type || "text/x-uri" == aIntent.type) {
            // Get shared text
            val clue = aIntent.getStringExtra(Intent.EXTRA_TEXT)
            aIntent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { subject = it }

            Timber.d("ACTION_SEND - Processing shared text: $clue")

            // Try to extract URL from the text first (handles cases where URL is embedded in text)
            val extractedUrl = extractUrlFromText(clue)
            if (extractedUrl != null) {
                Timber.d("ACTION_SEND - Extracted URL: $extractedUrl")
                extractedUrl
            } else {
                // Fallback to trying to parse the entire text as URI
                setAddressBarText(subject)
                // Cancel other operation as we won't open a tab here
                null
            }
        } else {
            null
        }
    } else {
        // Most likely Intent.ACTION_VIEW
        aIntent?.dataString
    }

    // I believe this is used to debounce
    val tabHashCode = aIntent?.extras?.getInt(INTENT_ORIGIN, 0) ?: 0
    Timber.d("onNewIntent - URL: $url, tabHashCode: $tabHashCode")
    if (tabHashCode != 0 && url != null) {
        Timber.d("onNewIntent - Loading URL in existing tab with hashCode: $tabHashCode")
        tabsManager.getTabForHashCode(tabHashCode)?.loadUrl(url)
    } else if (url != null) {

        // Define a lambda we can reuse below
        val createNewTab = {
            tabsManager.newTab(UrlInitializer(url), true).iIntent = aIntent
            // Avoid showing two tabs when starting incognito mode
            if (aIncognitoStartup) {
                // Delete the default tab that was created at startup
                tabsManager.deleteTab(0)
            }
        }
        val uri = url.toUri()

        if (aIntent?.action != Intent.ACTION_WEB_SEARCH && uri.host != null) {

            // Will load defaults if domain does not exists yet
            val domainPreferences = DomainPreferences(app, uri.host!!)
            var action = domainPreferences.incomingUrlAction
            if (isIncognito() && action != IncomingUrlAction.BLOCK) {
                // In incognito mode we just open that tab unless blocked
                action = IncomingUrlAction.NEW_TAB
            }

            when (action) {
                IncomingUrlAction.NEW_TAB -> {
                    Timber.d("onNewIntent - Creating new tab as per domain settings: $url")
                    createNewTab()
                }

                IncomingUrlAction.INCOGNITO_TAB -> {
                    Timber.d("onNewIntent - Opening URL in incognito tab as per domain settings: $url")
                    // Open in incognito - need to start IncognitoActivity
                    val incognitoIntent = IncognitoActivity.createIntent(this, uri)
                    startActivity(incognitoIntent)
                }

                IncomingUrlAction.BLOCK -> {
                    Timber.d("onNewIntent - Blocking URL as per domain settings: $url")
                    // Display snackbar to inform user
                    runOnUiThread {
                        val host = uri.host ?: "unknown"
                        val domain = host.topPrivateDomain ?: host
                        makeSnackbar(getString(R.string.message_blocked_domain, domain), Snackbar.LENGTH_LONG, if (configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM)
                            .setAction(R.string.settings) {
                                showDomainSettings(domain)
                            }
                            .show()
                    }
                }

                IncomingUrlAction.ASK -> {
                    Timber.d("onNewIntent - Showing dialog for user to choose action: $url")
                    // Show dialog asking user what to do
                    runOnUiThread {
                        // Use String extension to extract effective TLD+1 (e.g., "example.com" from "www.example.com")
                        val host = uri.host ?: "unknown"
                        val domain = host.topPrivateDomain ?: host
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_title_incoming_url, domain))
                            .setMessage(getString(R.string.dialog_message_incoming_url, subject, url))
                            .setPositiveButton(R.string.action_open) { _, _ ->
                                createNewTab()
                            }
                            .setNeutralButton(R.string.incognito) { _, _ ->
                                val incognitoIntent = IncognitoActivity.createIntent(this, uri)
                                startActivity(incognitoIntent)
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                }
            }
        } else if (URLUtil.isFileUrl(url)) {
            Timber.d("onNewIntent - Creating new tab for file URL: $url")
            showBlockedLocalFileDialog {
                createNewTab()
            }
        } else {
            Timber.d("onNewIntent - Creating new tab for URL: $url")
            createNewTab()
        }
    }

    // TODO: Display something when URL is null?
}