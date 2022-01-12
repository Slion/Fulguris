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
        USER_AGENT_DEFAULT -> {
            var userAgent = Regex(" Build/.+; wv").replace(WebSettings.getDefaultUserAgent(application),"")
            userAgent = Regex("Version/.+? ").replace(userAgent,"")
            userAgent
        }
        USER_AGENT_WINDOWS_DESKTOP -> WINDOWS_DESKTOP_USER_AGENT_PREFIX + webViewEngineVersionDesktop(application)
        USER_AGENT_LINUX_DESKTOP -> LINUX_DESKTOP_USER_AGENT
        USER_AGENT_MACOS_DESKTOP -> MACOS_DESKTOP_USER_AGENT
        USER_AGENT_ANDROID_MOBILE -> ANDROID_MOBILE_USER_AGENT_PREFIX + webViewEngineVersion(application)
        USER_AGENT_IOS_MOBILE -> IOS_MOBILE_USER_AGENT
        USER_AGENT_SYSTEM -> System.getProperty("http.agent") ?: " "
        USER_AGENT_WEB_VIEW -> WebSettings.getDefaultUserAgent(application)
        USER_AGENT_CUSTOM -> userAgentString.takeIf(String::isNotEmpty) ?: " "
        USER_AGENT_HIDE_DEVICE-> "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE})" +
                webViewEngineVersion(application)
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }

fun webViewEngineVersion(application: Application) =
        WebSettings.getDefaultUserAgent(application).substringAfter(")").replace("Version/.+? ".toRegex(), "")

fun webViewEngineVersionDesktop(application: Application) =
        webViewEngineVersion(application).replace(" Mobile ", " ")

    const val USER_AGENT_DEFAULT = "agent_default"
    const val USER_AGENT_HIDE_DEVICE = "agent_hide_device"
    const val USER_AGENT_WINDOWS_DESKTOP = "agent_windows_desktop"
    const val USER_AGENT_LINUX_DESKTOP = "agent_linux_desktop"
    const val USER_AGENT_MACOS_DESKTOP = "agent_macos_desktop"
    const val USER_AGENT_ANDROID_MOBILE = "agent_android_mobile"
    const val USER_AGENT_IOS_MOBILE = "agent_ios_mobile"
    const val USER_AGENT_SYSTEM = "agent_system"
    const val USER_AGENT_WEB_VIEW = "agent_web_view"
    const val USER_AGENT_CUSTOM = "agent_custom"

    val USER_AGENTS_ORDERED = arrayOf(
        USER_AGENT_DEFAULT,
        USER_AGENT_ANDROID_MOBILE,
        USER_AGENT_IOS_MOBILE,
        USER_AGENT_SYSTEM,
        USER_AGENT_WEB_VIEW,
        USER_AGENT_CUSTOM,
        USER_AGENT_HIDE_DEVICE,
        USER_AGENT_WINDOWS_DESKTOP,
        USER_AGENT_LINUX_DESKTOP,
        USER_AGENT_MACOS_DESKTOP,
    )
