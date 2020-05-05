package acr.browser.lightning.preference

import acr.browser.lightning.constant.DESKTOP_USER_AGENT
import acr.browser.lightning.constant.MOBILE_USER_AGENT
import android.app.Application
import android.webkit.WebSettings

/**
 * Return the user agent chosen by the user or the custom user agent entered by the user.
 */
fun UserPreferences.userAgent(application: Application): String =
    when (val choice = userAgentChoice) {
        // WebSettings default identifies us as WebView and as WebView Google is preventing use to login to its services.
        // Clearly we don't want that so we just modify default user agent by removing the WebView specific parts.
        // That should make us look like Chrome, which we are really.
        1 -> Regex(" Build/.+; wv").replace(WebSettings.getDefaultUserAgent(application),"")
        2 -> DESKTOP_USER_AGENT
        3 -> MOBILE_USER_AGENT
        4 -> userAgentString.takeIf(String::isNotEmpty) ?: " "
        5 -> WebSettings.getDefaultUserAgent(application)
        6 -> System.getProperty("http.agent") ?: " "
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }
