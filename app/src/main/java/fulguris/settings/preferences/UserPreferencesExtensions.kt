package fulguris.settings.preferences

import fulguris.R
import android.app.Application
import android.os.Build
import android.webkit.WebSettings
import fulguris.constant.ANDROID_MOBILE_USER_AGENT_PREFIX
import fulguris.constant.IOS_MOBILE_USER_AGENT
import fulguris.constant.LINUX_DESKTOP_USER_AGENT
import fulguris.constant.MACOS_DESKTOP_USER_AGENT
import fulguris.constant.WINDOWS_DESKTOP_USER_AGENT_PREFIX

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
        USER_AGENT_CUSTOM -> userAgentString.replace(Regex("[\r\n]"), "").takeIf { it.isNotEmpty() } ?: " "
        USER_AGENT_HIDE_DEVICE -> "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE})" +
                webViewEngineVersion(application)
        else -> throw UnsupportedOperationException("Unknown userAgentChoice: $choice")
    }

// On WSA our WebView user-agent looks like that:
// Mozilla/5.0 (Linux; Android 12; Subsystem for Android(TM) Build/SQ3A.220705.003.A1; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/104.0.5112.97 Safari/537.36

fun webViewEngineVersion(application: Application) =
        WebSettings.getDefaultUserAgent(application).substringAfter("wv)").replace("Version/.+? ".toRegex(), "")

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

// use LinkedHashMap because it keeps insertion order
//  in tests, order was also kept for Map. But this is not guaranteed
val USER_AGENTS_ORDERED = linkedMapOf(
    USER_AGENT_DEFAULT to R.string.agent_default,
    USER_AGENT_HIDE_DEVICE to R.string.agent_hide_device,
    USER_AGENT_WINDOWS_DESKTOP to R.string.agent_windows_desktop,
    USER_AGENT_LINUX_DESKTOP to R.string.agent_linux_desktop,
    USER_AGENT_MACOS_DESKTOP to R.string.agent_macos_desktop,
    USER_AGENT_ANDROID_MOBILE to R.string.agent_android_mobile,
    USER_AGENT_IOS_MOBILE to R.string.agent_ios_mobile,
    USER_AGENT_SYSTEM to R.string.agent_system,
    USER_AGENT_WEB_VIEW to R.string.agent_web_view,
    USER_AGENT_CUSTOM to R.string.agent_custom,
)
