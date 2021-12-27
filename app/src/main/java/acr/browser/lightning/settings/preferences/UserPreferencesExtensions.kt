package acr.browser.lightning.settings.preferences

import acr.browser.lightning.constant.*
import android.app.Application
import android.os.Build
import android.webkit.WebSettings

/**
 * Return the user agent chosen by the user or the custom user agent entered by the user.
 */
fun UserPreferences.userAgent(application: Application): String =
    when (val choice = userAgentChoice) {
        // WebSettings default identifies us as WebView and as WebView Google is preventing us to login to its services.
        // Clearly we don't want that so we just modify default user agent by removing the WebView specific parts.
        // That should make us look like Chrome, which we are really.
        1 -> "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE})" +
                WebSettings.getDefaultUserAgent(application).substringAfter(")")
        2 -> WINDOWS_DESKTOP_USER_AGENT
        3 -> LINUX_DESKTOP_USER_AGENT
        4 -> MACOS_DESKTOP_USER_AGENT
        5 -> ANDROID_MOBILE_USER_AGENT
        6 -> IOS_MOBILE_USER_AGENT
        7 -> System.getProperty("http.agent") ?: " "
        8 -> WebSettings.getDefaultUserAgent(application)
        9 -> userAgentString.takeIf(String::isNotEmpty) ?: " "
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }
