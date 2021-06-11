package acr.browser.lightning.preference

import acr.browser.lightning.AppTheme
import acr.browser.lightning.BrowserApp
import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.browser.ProxyChoice
import acr.browser.lightning.browser.SearchBoxDisplayChoice
import acr.browser.lightning.browser.SearchBoxModel
import acr.browser.lightning.constant.DEFAULT_ENCODING
import acr.browser.lightning.constant.Uris
import acr.browser.lightning.device.ScreenSize
import acr.browser.lightning.di.UserPrefs
import acr.browser.lightning.preference.delegates.*
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.search.engine.GoogleSearch
import acr.browser.lightning.settings.NewTabPosition
import acr.browser.lightning.utils.FileUtils
import acr.browser.lightning.view.RenderingMode
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's preferences.
 */
@Singleton
class UserPreferences @Inject constructor(
    @UserPrefs preferences: SharedPreferences,
    screenSize: ScreenSize
) {

    /**
     * True if Web RTC is enabled in the browser, false otherwise.
     */
    var webRtcEnabled by preferences.booleanPreference(R.string.pref_key_webrtc, R.bool.pref_default_webrtc)

    /**
     * True if the browser should block ads, false otherwise.
     */
    var adBlockEnabled by preferences.booleanPreference(BLOCK_ADS, false)

    /**
     * True if the browser should block images from being loaded, false otherwise.
     */
    var loadImages by preferences.booleanPreference(R.string.pref_key_load_images, true)

    /**
     * True if the browser should clear the browser cache when the app is exited, false otherwise.
     */
    var clearCacheExit by preferences.booleanPreference(CLEAR_CACHE_EXIT, false)

    /**
     * True if the browser should allow websites to store and access cookies, false otherwise.
     */
    var cookiesEnabled by preferences.booleanPreference(R.string.pref_key_cookies, true)

    /**
     * True if cookies should be enabled in incognito mode, false otherwise.
     *
     * WARNING: Cookies will be shared between regular and incognito modes if this is enabled.
     */
    var incognitoCookiesEnabled by preferences.booleanPreference(R.string.pref_key_cookies_incognito, false)

    /**
     * The folder into which files will be downloaded.
     */
    var downloadDirectory by preferences.stringPreference(DOWNLOAD_DIRECTORY, FileUtils.DEFAULT_DOWNLOAD_PATH)

    /**
     * True if the browser should hide the navigation bar when scrolling, false if it should be
     * immobile.
     */
    var hideToolBarInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_hide_tool_bar, false)

    /**
     * True if the browser should hide the navigation bar when scrolling, false if it should be
     * immobile.
     */
    var hideToolBarInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_hide_tool_bar, true)

    /**
     */
    var showToolBarOnScrollUpInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_show_tool_bar_on_scroll_up, true)

    /**
     */
    var showToolBarOnScrollUpInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_show_tool_bar_on_scroll_up, false)

    /**
     */
    var showToolBarOnPageTopInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_show_tool_bar_on_page_top, false)

    /**
     */
    var showToolBarOnPageTopInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_show_tool_bar_on_page_top, true)

    /**
     * True if the system status bar should be hidden throughout the app, false if it should be
     * visible.
     */
    var hideStatusBarInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_hide_status_bar, false)

    /**
     * True if the system status bar should be hidden throughout the app, false if it should be
     * visible.
     */
    var hideStatusBarInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_hide_status_bar, true)

    /**
     * Defines if a new tab should be opened when user is doing a new search.
     */
    var searchInNewTab by preferences.booleanPreference(R.string.pref_key_search_in_new_tab, R.bool.pref_default_search_in_new_tab)

    /**
     * Defines if a new tab should be opened when user provided a new URL.
     */
    var urlInNewTab by preferences.booleanPreference(R.string.pref_key_url_in_new_tab, R.bool.pref_default_url_in_new_tab)

    /**
     * Defines if a new tab should be opened when user taps on homepage button.
     */
    var homepageInNewTab by preferences.booleanPreference(R.string.pref_key_homepage_in_new_tab, R.bool.pref_default_homepage_in_new_tab)

    /**
     * Defines if a new tab should be opened when user selects a bookmark.
     */
    var bookmarkInNewTab by preferences.booleanPreference(R.string.pref_key_bookmark_in_new_tab, R.bool.pref_default_bookmark_in_new_tab)

    /**
     * Value of our new tab position enum.
     * Defines where a new tab should be created in our tab list.
     *
     * @see NewTabPosition
     */
    var newTabPosition by preferences.enumPreference(R.string.pref_key_new_tab_position, NewTabPosition.AFTER_CURRENT_TAB)

    /**
     * True if desktop mode should be enabled by default for new tabs, false otherwise.
     */
    var desktopModeDefault by preferences.booleanPreference(R.string.pref_key_desktop_mode_default, false)

    /**
     * True if dark mode should be enabled by default for new tabs, false otherwise.
     */
    var darkModeDefault by preferences.booleanPreference(R.string.pref_key_dark_mode_default, false)

    /**
     * The URL of the selected homepage.
     */
    var homepage by preferences.stringPreference(HOMEPAGE, Uris.AboutHome)

    /**
     * True if the browser should allow execution of javascript, false otherwise.
     */
    var javaScriptEnabled by preferences.booleanPreference(R.string.pref_key_javascript, true)

    /**
     * True if the device location should be accessible by websites, false otherwise.
     *
     * NOTE: If this is enabled, permission will still need to be granted on a per-site basis.
     */
    var locationEnabled by preferences.booleanPreference(LOCATION, false)

    /**
     * True if the browser should load pages zoomed out instead of zoomed in so that the text is
     * legible, false otherwise.
     */
    var overviewModeEnabled by preferences.booleanPreference(R.string.pref_key_overview_mode, true)

    /**
     * True if the browser should allow websites to open new windows, false otherwise.
     */
    var popupsEnabled by preferences.booleanPreference(R.string.pref_key_support_multiple_window, true)

    /**
     * True if the app should remember which browser tabs were open and restore them if the browser
     * is automatically closed by the system.
     */
    var restoreTabsOnStartup by preferences.booleanPreference(R.string.pref_key_restore_tabs_on_startup, true)

    /**
     * True if the browser should save form input, false otherwise.
     */
    var savePasswordsEnabled by preferences.booleanPreference(SAVE_PASSWORDS, true)

    /**
     * The index of the chosen search engine.
     *
     * @see SearchEngineProvider
     */
    var searchChoice by preferences.intPreference(SEARCH, 1)

    /**
     * The custom URL which should be used for making searches.
     */
    var searchUrl by preferences.stringPreference(SEARCH_URL, GoogleSearch().queryUrl)

    /**
     * True if the browser should attempt to reflow the text on a web page after zooming in or out
     * of the page.
     */
    var textReflowEnabled by preferences.booleanPreference(R.string.pref_key_text_reflow, false)

    /**
     * The index of the text size that should be used in the browser.
     * Default to 50 to have 100% as default text size.
     */
    var browserTextSize by preferences.intPreference(R.string.pref_key_browser_text_size, 50)

    /**
     * True if the browser should fit web pages to the view port, false otherwise.
     */
    var useWideViewPortEnabled by preferences.booleanPreference(R.string.pref_key_wide_viewport, true)

    /**
     * The index of the user agent choice that should be used by the browser.
     *
     * @see UserPreferences.userAgent
     */
    var userAgentChoice by preferences.intPreference(USER_AGENT, 1)

    /**
     * The custom user agent that should be used by the browser.
     */
    var userAgentString by preferences.stringPreference(USER_AGENT_STRING, "")

    /**
     * True if the browser should clear the navigation history on app exit, false otherwise.
     */
    var clearHistoryExitEnabled by preferences.booleanPreference(CLEAR_HISTORY_EXIT, false)

    /**
     * True if the browser should clear the browser cookies on app exit, false otherwise.
     */
    var clearCookiesExitEnabled by preferences.booleanPreference(CLEAR_COOKIES_EXIT, false)

    /**
     * The index of the rendering mode that should be used by the browser.
     */
    var renderingMode by preferences.enumPreference(R.string.pref_key_rendering_mode, RenderingMode.NORMAL)

    /**
     * True if third party cookies should be disallowed by the browser, false if they should be
     * allowed.
     */
    var blockThirdPartyCookiesEnabled by preferences.booleanPreference(BLOCK_THIRD_PARTY, false)

    /**
     * True if the browser should extract the theme color from a website and color the UI with it,
     * false otherwise.
     */
    var colorModeEnabled by preferences.booleanPreference(R.string.pref_key_web_page_theme, true)

    /**
     * The index of the URL/search box display choice/
     *
     * @see SearchBoxModel
     */
    var urlBoxContentChoice by preferences.enumPreference(R.string.pref_key_tool_bar_text_display, SearchBoxDisplayChoice.TITLE)

    /**
     * True if the browser should invert the display colors of the web page content, false
     * otherwise.
     */
    var invertColors by preferences.booleanPreference(INVERT_COLORS, false)

    /**
     * The index of the reading mode text size.
     */
    var readingTextSize by preferences.intPreference(READING_TEXT_SIZE, 2)

    /**
     * The index of the theme used by the application.
     */
    var useTheme by preferences.enumPreference(R.string.pref_key_theme, AppTheme.DEFAULT)

    /**
     * The text encoding used by the browser.
     */
    var textEncoding by preferences.stringPreference(R.string.pref_key_default_text_encoding, DEFAULT_ENCODING)

    /**
     * True if the web page storage should be cleared when the app exits, false otherwise.
     */
    var clearWebStorageExitEnabled by preferences.booleanPreference(CLEAR_WEB_STORAGE_EXIT, false)

    /**
     * True if the browser should send a do not track (DNT) header with every GET request, false
     * otherwise.
     */
    var doNotTrackEnabled by preferences.booleanPreference(DO_NOT_TRACK, false)

    /**
     * True if the browser should save form data, false otherwise.
     */
    var saveDataEnabled by preferences.booleanPreference(R.string.pref_key_request_save_data, false)

    /**
     * True if the browser should attempt to remove identifying headers in GET requests, false if
     * the default headers should be left along.
     */
    var removeIdentifyingHeadersEnabled by preferences.booleanPreference(IDENTIFYING_HEADERS, false)

    /**
     * True if the bookmarks tab should be on the opposite side of the screen, false otherwise. If
     * the navigation drawer UI is used, the tab drawer will be displayed on the opposite side as
     * well.
     */
    var bookmarksAndTabsSwapped by preferences.booleanPreference(R.string.pref_key_swap_tabs_and_bookmarks, false)

    /**
     * Disable gesture actions on drawer.
     */
    var lockedDrawers by preferences.booleanPreference(R.string.pref_key_locked_drawers, R.bool.pref_default_locked_drawers)

    /**
     * Use bottom sheets instead of drawers to display tabs and bookmarks.
     */
    var useBottomSheets by preferences.booleanPreference(R.string.pref_key_use_bottom_sheets, R.bool.pref_default_use_bottom_sheets)

    /**
     *
     */
    var pullToRefreshInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_pull_to_refresh, R.bool.pref_default_portrait_pull_to_refresh)
    var pullToRefreshInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_pull_to_refresh, R.bool.pref_default_landscape_pull_to_refresh)

    /**
     * True if the app should use the navigation drawer UI, false if it should use the traditional
     * desktop browser tabs UI.
     */
    private var verticalTabBarInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_tab_bar_vertical, !screenSize.isTablet())
    private var verticalTabBarInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_tab_bar_vertical, !screenSize.isTablet())

    var verticalTabBar : Boolean = false
        get() = if (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_PORTRAIT) verticalTabBarInPortrait else verticalTabBarInLandscape
        private set


    /**
     *
     */
    private var toolbarsBottomInPortrait by preferences.booleanPreference(R.string.pref_key_portrait_toolbars_bottom, R.bool.pref_default_toolbars_bottom)
    private var toolbarsBottomInLandscape by preferences.booleanPreference(R.string.pref_key_landscape_toolbars_bottom, R.bool.pref_default_toolbars_bottom)

    var toolbarsBottom : Boolean = false
        get() = toolbarsBottom()
        private set

    fun toolbarsBottom(aConf: Configuration=Resources.getSystem().configuration) : Boolean {
        return if (aConf.orientation == Configuration.ORIENTATION_PORTRAIT) toolbarsBottomInPortrait else toolbarsBottomInLandscape
    }


    /**
     * Not an actual user preference. Just used to communicate between settings and browser activity.
     * Don't ask :)
     */
    var bookmarksChanged by preferences.booleanPreference(BOOKMARKS_CHANGED, false)

    /**
     * True if the status bar of the app should always be high contrast, false if it should follow
     * the theme of the app.
     */
    var useBlackStatusBar by preferences.booleanPreference(R.string.pref_key_black_status_bar, false)

    /**
     * The index of the proxy choice.
     */
    var proxyChoice by preferences.enumPreference(PROXY_CHOICE, ProxyChoice.NONE)

    /**
     * The proxy host used when [proxyChoice] is [ProxyChoice.MANUAL].
     */
    var proxyHost by preferences.stringPreference(USE_PROXY_HOST, "localhost")

    /**
     * The proxy port used when [proxyChoice] is [ProxyChoice.MANUAL].
     */
    var proxyPort by preferences.intPreference(USE_PROXY_PORT, 8118)

    /**
     * The index of the search suggestion choice.
     *
     * @see SearchEngineProvider
     */
    var searchSuggestionChoice by preferences.intPreference(SEARCH_SUGGESTIONS, 1)

    /**
     * The index of the ad blocking hosts file source.
     */
    var hostsSource by preferences.intPreference(HOSTS_SOURCE, 0)

    /**
     * The local file from which ad blocking hosts should be read, depending on the [hostsSource].
     */
    var hostsLocalFile by preferences.nullableStringPreference(HOSTS_LOCAL_FILE)

    /**
     * The remote URL from which ad blocking hosts should be read, depending on the [hostsSource].
     */
    var hostsRemoteFile by preferences.nullableStringPreference(HOSTS_REMOTE_FILE)

    /**
     * User can disable Firebase Google Analytics.
     */
    var analytics by preferences.booleanPreference(R.string.pref_key_analytics, true)

    /**
     * User can disable Firebase Crash Report AKA Crashlytics.
     */
    var crashReport by preferences.booleanPreference(R.string.pref_key_crash_report, true)

    /**
     * Toggle visibility of close tab button on drawer tab list items.
     */
    var showCloseTabButton by preferences.booleanPreference(R.string.pref_key_tab_list_item_show_close_button, if (screenSize.isTablet())  R.bool.const_true else R.bool.pref_default_tab_list_item_show_close_button)

    /**
     * Define viewport width for desktop mode in portrait
     */
    var desktopWidthInPortrait by preferences.intPreference(R.string.pref_key_portrait_desktop_width, BrowserApp.instance.resources.getInteger(R.integer.pref_default_portrait_desktop_width))

    /**
     * Define viewport width for desktop mode in landscape
     */
    var desktopWidthInLandscape by preferences.intPreference(R.string.pref_key_landscape_desktop_width, BrowserApp.instance.resources.getInteger(R.integer.pref_default_landscape_desktop_width))

    /**
     * Save sponsorship level.
     * Default level is defined by our build configuration.
     */
    var sponsorship by preferences.enumPreference(R.string.pref_key_sponsorship, BuildConfig.SPONSORSHIP)

    /**
     * Used to store version code to enable version update check and first run detection.
     */
    var versionCode by preferences.intPreference(R.string.pref_key_version_code, 0)

    /**
     * Define if user wants to show exit option in menu
     */
    var menuShowExit by preferences.booleanPreference(R.string.pref_key_menu_show_exit, R.bool.pref_default_menu_show_exit)

    /**
     * Define if user wants to show exit option in menu
     */
    var menuShowNewTab by preferences.booleanPreference(R.string.pref_key_menu_show_new_tab, R.bool.pref_default_menu_show_new_tab)

    /**
     * Define the locale language the user want us to use.
     * Empty string means use system default locale.
     */
    var locale by preferences.stringPreference(R.string.pref_key_locale, "")

}

// SL: Looks like those are the actual shared property keys thus overriding what ever was defined in our XML
// TODO: That does not make sense, we need to sort this out
private const val BLOCK_ADS = "AdBlock"
private const val CLEAR_CACHE_EXIT = "cache"
private const val DOWNLOAD_DIRECTORY = "downloadLocation"
private const val HOMEPAGE = "home"
private const val LOCATION = "location"
private const val SAVE_PASSWORDS = "passwords"
private const val SEARCH = "search"
private const val SEARCH_URL = "searchurl"
private const val USER_AGENT = "agentchoose"
private const val USER_AGENT_STRING = "userAgentString"
private const val CLEAR_HISTORY_EXIT = "clearHistoryExit"
private const val CLEAR_COOKIES_EXIT = "clearCookiesExit"
private const val BLOCK_THIRD_PARTY = "thirdParty"
private const val INVERT_COLORS = "invertColors"
private const val READING_TEXT_SIZE = "readingTextSize"
private const val CLEAR_WEB_STORAGE_EXIT = "clearWebStorageExit"
private const val DO_NOT_TRACK = "doNotTrack"
private const val IDENTIFYING_HEADERS = "removeIdentifyingHeaders"
private const val BOOKMARKS_CHANGED = "bookmarksChanged"
private const val PROXY_CHOICE = "proxyChoice"
private const val USE_PROXY_HOST = "useProxyHost"
private const val USE_PROXY_PORT = "useProxyPort"
private const val SEARCH_SUGGESTIONS = "searchSuggestionsChoice"
private const val HOSTS_SOURCE = "hostsSource"
private const val HOSTS_LOCAL_FILE = "hostsLocalFile"
private const val HOSTS_REMOTE_FILE = "hostsRemoteFile"