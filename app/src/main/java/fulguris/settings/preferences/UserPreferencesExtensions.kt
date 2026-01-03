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
import timber.log.Timber
import androidx.annotation.RequiresApi

/**
 * Return the user agent chosen by the user or the custom user agent entered by the user.
 */
fun UserPreferences.userAgent(application: Application): String =
    when (val choice = userAgentChoice) {
        // WebSettings default identifies us as WebView and as WebView Google is preventing us to login to its services.
        // Clearly we don't want that so we just modify default user agent by removing the WebView specific parts.
        // That should make us look like Chrome, which we are really.
        // We also reduce fingerprinting by removing device-specific details.
        USER_AGENT_DEFAULT -> {
            var userAgent = WebSettings.getDefaultUserAgent(application)
            //Timber.d("WebSettings.getDefaultUserAgent: $userAgent")
            // Remove WebView specific parts: Build info and wv marker
            userAgent = Regex(" Build/.+; wv").replace(userAgent, "")
            // Remove Version/x.x part
            userAgent = Regex("Version/.+? ").replace(userAgent, "")
            // Replace device name with generic "K" while keeping actual Android version
            // Actually Chrome and DDG hardcode Android 10 - tested on newer and older Android versions
            //userAgent = Regex("Android ([^;)]+);[^)]*\\)").replace(userAgent, "Android $1; K)")
            userAgent = Regex("Android ([^;)]+);[^)]*\\)").replace(userAgent, "Android 10; K)")
            // Zero out Chrome build numbers (keep major version only)
            // Chrome major version however is not hardcoded and varies depending on the actual WebView version on the device
            userAgent = Regex("Chrome/(\\d+)\\.\\d+\\.\\d+\\.\\d+").replace(userAgent, "Chrome/$1.0.0.0")
            // Remove "Mobile " to reduce fingerprinting
            //userAgent = userAgent.replace(" Mobile ", " ")
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

/**
 * Configure Client Hints to match our reduced User-Agent for privacy.
 * Client Hints are HTTP headers that provide information about the user's device.
 * We reduce them to match our privacy-focused User-Agent string.
 * This uses androidx.webkit.WebSettingsCompat for compatibility.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Suppress("DEPRECATION")
@android.annotation.SuppressLint("RequiresFeature")
fun WebSettings.setReducedClientHints() {
    // Check if User-Agent Metadata (Client Hints) is supported
    if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
        Timber.d("Client Hints not supported on this device/WebView version")
        return
    }

    try {
        // Get the current UserAgentMetadata from WebSettingsCompat
        val metadata = androidx.webkit.WebSettingsCompat.getUserAgentMetadata(this)

        // Extract Chrome major version from the first brand (use getMajorVersion() method)
        val chromeMajorVersion = metadata.brandVersionList.firstOrNull()?.getMajorVersion() ?: "120"

        // Build reduced brand list matching our User-Agent
        val reducedBrandList = listOf(
            androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Chromium")
                .setMajorVersion(chromeMajorVersion)
                .setFullVersion("$chromeMajorVersion.0.0.0")
                .build(),
            androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Google Chrome")
                .setMajorVersion(chromeMajorVersion)
                .setFullVersion("$chromeMajorVersion.0.0.0")
                .build(),
            androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                .setBrand("Not_A Brand")
                .setMajorVersion("99")
                .setFullVersion("99.0.0.0")
                .build()
        )

        //Timber.v("Client Hints original brand list: ${metadata.brandVersionList}")
        //Timber.v("Client Hints reduced brand list: $reducedBrandList")

        // Create reduced metadata matching our User-Agent: Android 10, model "K"
        val reducedMetadata = androidx.webkit.UserAgentMetadata.Builder()
            .setPlatform("Android")
            // Hardcode Android 10.0.0 to match our reduced User-Agent
            .setPlatformVersion("10.0.0")
            // Zero out the full version to match our reduced UA
            .setFullVersion("$chromeMajorVersion.0.0.0")
            // Generic model "K" matching our User-Agent
            .setModel("K")
            // Don't expose bitness
            .setBitness(0)
            // Remove architecture info for privacy
            .setArchitecture("")
            .setWow64(false)
            .setMobile(true)
            .setBrandVersionList(reducedBrandList)
            .build()

        androidx.webkit.WebSettingsCompat.setUserAgentMetadata(this, reducedMetadata)
        //Timber.v("Client Hints configured for privacy: Android 10, model K, Chrome $chromeMajorVersion.0.0.0")
    } catch (e: Exception) {
        Timber.e(e, "Failed to set Client Hints")
    }
}
