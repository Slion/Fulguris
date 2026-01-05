package fulguris.settings.preferences

import fulguris.AccentTheme
import fulguris.AppTheme
import fulguris.BuildConfig
import fulguris.R
import fulguris.adblock.AbpUpdateMode
import fulguris.constant.DEFAULT_ENCODING
import fulguris.constant.Uris
import fulguris.device.ScreenSize
import fulguris.di.UserPrefs
import fulguris.search.SearchEngineProvider
import fulguris.search.engine.GoogleSearch
import fulguris.settings.NewTabPosition
import fulguris.utils.FileUtils
import fulguris.view.RenderingMode
import android.content.SharedPreferences
import fulguris.browser.SuggestionNumChoice
import fulguris.enums.LogLevel
import fulguris.enums.HeaderInfo
import fulguris.enums.IncomingViewAction
import fulguris.enums.LayerType
import fulguris.settings.preferences.delegates.booleanPreference
import fulguris.settings.preferences.delegates.enumPreference
import fulguris.settings.preferences.delegates.floatResPreference
import fulguris.settings.preferences.delegates.intPreference
import fulguris.settings.preferences.delegates.stringPreference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's preferences.
 *
 * Defaults specified here are only used while that settings entry has not been initialized.
 * Settings entry are typically initialized the first time you access the settings page it belongs to.
 * They also happen to be initialized with the default value specified in the preference XML rather than the default specified here.
 * It should be the same but still that's really poor design.
 * TODO: Fix that that at some point
 */
@Singleton
class UserPreferences @Inject constructor(
    @UserPrefs val preferences: SharedPreferences,
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
     * True if user scripts should be enabled and injected into web pages, false otherwise.
     * TODO: Consider making this a domain settings option
     */
    var extensionsEnabled by preferences.booleanPreference(R.string.pref_key_extensions_enabled, false)

    /**
     * True if the browser should block images from being loaded, false otherwise.
     */
    var loadImages by preferences.booleanPreference(R.string.pref_key_load_images, true)

    /**
     * The network engine implementation to use for handling network requests.
     * Values: "webview" (default), "okhttp", etc.
     */
    var networkEngine by preferences.stringPreference(R.string.pref_key_network_engine, "webview")

    /**
     * Network cache size in megabytes.
     * Default: 50 MB
     * Maximum: Dynamically calculated as 10% of available disk space
     * Minimum: 0 MB (disables caching)
     * Note: Stored as String because EditTextPreference stores text values as strings.
     *       Must be parsed to Int when used.
     */
    var networkCacheSize by preferences.stringPreference(R.string.pref_key_network_cache_size, "50")

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
     * Value of our incoming view action enum.
     * Defines how VIEW intents from other apps should be handled.
     *
     * @see IncomingViewAction
     */
    var incomingViewAction by preferences.enumPreference(R.string.pref_key_incoming_view_action, IncomingViewAction.NEW_TAB)

    /**
     * The URL of the selected homepage.
     */
    var homepage by preferences.stringPreference(HOMEPAGE, Uris.AboutBookmarks)

    /**
     * The URL of the selected incognito page.
     */
    var incognitoPage by preferences.stringPreference(INCOGNITO, Uris.AboutIncognito)


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
     * The index of the user agent choice that should be used by the browser.
     *
     * @see UserPreferences.userAgent
     */
    var userAgentChoice by preferences.stringPreference(R.string.pref_key_user_agent, USER_AGENT_DEFAULT)

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
     * Determine if we use hardware acceleration and WebGL.
     */
    var layerType by preferences.enumPreference(R.string.pref_key_layer_type, LayerType.Hardware)

    /**
     * The index of the rendering mode that should be used by the browser.
     */
    var renderingMode by preferences.enumPreference(R.string.pref_key_rendering_mode, RenderingMode.NORMAL)

    /**
     * True if the browser should extract the theme color from a website and color the UI with it,
     * false otherwise.
     */
    var colorModeEnabled by preferences.booleanPreference(R.string.pref_key_web_page_theme, true)

    /**
     * Define what text should be shown in the Android task switcher.
     * That's also the window title for WSA on Windows.
     */
    var taskLabel by preferences.enumPreference(R.string.pref_key_task_label, HeaderInfo.AppName)

    /**
     * True means favicon should be shown in task switcher.
     * False means we show the app icon.
     */
    var taskIcon by preferences.booleanPreference(R.string.pref_key_task_icon, false)

    /**
     * Define which text should be shown on our toolbar.
     */
    var toolbarLabel by preferences.enumPreference(R.string.pref_key_toolbar_label, HeaderInfo.Title)

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
     * Internal flag to track if theme picker dialog should be reopened after activity recreation.
     * Used to provide live theme preview while keeping the dialog open.
     */
    var openThemePicker by preferences.booleanPreference(R.string.pref_key_open_theme_picker, false)

    /**
     * Unused
     */
    var useAccent by preferences.enumPreference(R.string.pref_key_accent, AccentTheme.DEFAULT_ACCENT)

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
    var bookmarksAndTabsSwapped by preferences.booleanPreference(R.string.pref_key_swap_tabs_and_bookmarks, R.bool.pref_default_swap_drawer)

    /**
     * Disable gesture actions on drawer.
     */
    var lockedDrawers by preferences.booleanPreference(R.string.pref_key_locked_drawers, R.bool.pref_default_locked_drawers)

    /**
     * Use bottom sheets instead of drawers to display tabs and bookmarks.
     */
    var useBottomSheets by preferences.booleanPreference(R.string.pref_key_use_bottom_sheets, R.bool.pref_default_use_bottom_sheets)

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
     * The index of the search suggestion choice.
     *
     * @see SearchEngineProvider
     */
    var searchSuggestionChoice by preferences.intPreference(SEARCH_SUGGESTIONS, 1)

    /**
     * Tells if user has accepted our terms and conditions.
     */
    var acceptTerms by preferences.booleanPreference(R.string.pref_key_accept_terms, false)

    /**
     * User can disable Firebase Google Analytics.
     */
    var analytics by preferences.booleanPreference(R.string.pref_key_analytics, R.bool.pref_default_analytics)

    /**
     * User can disable Firebase Crash Report AKA Crashlytics.
     */
    var crashReport by preferences.booleanPreference(R.string.pref_key_crash_report, R.bool.pref_default_crash_report)

    /**
     * User can disable crash log.
     * Crash log typically write crash callstack to file system.
     */
    var crashLogs by preferences.booleanPreference(R.string.pref_key_crash_logs, R.bool.pref_default_crash_logs)

    /**
     * User can disable logs.
     * When enabled they are written over logcat through Timber.
     * Notably allows users to enabled logcat in release builds.
     */
    var logs by preferences.booleanPreference(R.string.pref_key_logs, R.bool.pref_default_logs)

    /**
     * Specify from which log level we should feed logcat.
     */
    var logLevel by preferences.enumPreference(R.string.pref_key_log_level, LogLevel.DEBUG)

    /**
     * Tells if the specified log level will be logged
     */
    fun isLog(aLevel: LogLevel) = logs && aLevel >= logLevel

    /**
     * Toggle visibility of close tab button on drawer tab list items.
     */
    var showCloseTabButton by preferences.booleanPreference(R.string.pref_key_tab_list_item_show_close_button, if (screenSize.isTablet())  R.bool.const_true else R.bool.pref_default_tab_list_item_show_close_button)

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
     * Define the locale language the user want us to use.
     * Empty string means use system default locale.
     */
    var locale by preferences.stringPreference(R.string.pref_key_locale, "")

    /**
     * Define behavior for blocklist updates (on, off, only on non-metered connections).
     * Update check is only happening at browser start.
     */
    var blockListAutoUpdate by preferences.enumPreference(R.string.pref_key_blocklist_auto_update, AbpUpdateMode.WIFI_ONLY)
    var blockListAutoUpdateFrequency by preferences.intPreference(R.string.pref_key_blocklist_auto_update_frequency, 7)

    /**
     * Modify filters may break some websites due to incomplete implementation.
     * Let the user decide whether to use them.
     */
    var modifyFilters by preferences.intPreference(R.string.pref_key_modify_filters, 0)

    ///
    /// Various tab event actions
    ///

    var onTabCloseShowSnackbar by preferences.booleanPreference(R.string.pref_key_on_tab_close_show_snackbar, R.bool.pref_default_on_tab_close_show_snackbar)
    var onTabCloseVibrate by preferences.booleanPreference(R.string.pref_key_on_tab_close_vibrate, R.bool.pref_default_on_tab_close_vibrate)
    var onTabChangeShowAnimation by preferences.booleanPreference(R.string.pref_key_on_tab_change_show_animation, R.bool.pref_default_on_tab_change_show_animation)
    var onTabChangeAnimationDuration by preferences.floatResPreference(R.string.pref_key_on_tab_change_animation_duration, R.integer.pref_default_animation_duration_tab_change)
    var onTabBackShowAnimation by preferences.booleanPreference(R.string.pref_key_on_tab_back_show_animation, R.bool.pref_default_on_tab_back_show_animation)
    var onTabBackAnimationDuration by preferences.floatResPreference(R.string.pref_key_on_tab_flip_animation_duration, R.integer.pref_default_animation_duration_flip)
    var onPageStartedShowAnimation by preferences.booleanPreference(R.string.pref_key_on_page_started_show_animation, R.bool.pref_default_on_tab_back_show_animation)

    /**
     * Force Zoom for Websites
     */
    var forceZoom by preferences.booleanPreference(R.string.pref_key_force_zoom, R.bool.pref_default_force_zoom)

    /**
     *
     */
    var imageUrlString by preferences.stringPreference(R.string.pref_key_image_url, "")

    /**
     * Define Suggestion number Choice
     */
    var suggestionChoice by preferences.enumPreference(R.string.pref_key_search_suggestions_number, SuggestionNumChoice.FIVE)

    /**
     * Define long press on the 'Tabs' icon opens a new tab.
     */
    var longClickTab by preferences.booleanPreference(R.string.pref_key_long_click_tab, R.bool.pref_default_long_click_tab)

    /**
     * Define if user wants to close the drawer after delete or create an tab automatically.
     */
    var closeDrawer by preferences.booleanPreference(R.string.pref_key_close_drawer, R.bool.pref_default_close_drawer)

    /**
     * Whether WebView scrollbars should be fading
     */
    var scrollbarFading by preferences.booleanPreference(R.string.pref_key_scrollbar_fading, R.bool.pref_default_scrollbar_fading)

    /**
     * The time it takes for WebView scrollbars to start fading away
     */
    var scrollbarDelayBeforeFade by preferences.floatResPreference(R.string.pref_key_scrollbar_delay_before_fade, R.integer.pref_default_scrollbar_delay_before_fade)

    /**
     * The time it takes for WebView scrollbars to fade away
     */
    var scrollbarFadeDuration by preferences.floatResPreference(R.string.pref_key_scrollbar_fade_duration, R.integer.pref_default_scrollbar_fade_duration)

    /**
     * WebView scrollbars size
     */
    var scrollbarSize by preferences.floatResPreference(R.string.pref_key_scrollbar_size, R.integer.pref_default_scrollbar_size)


}

// SL: Looks like those are the actual shared property keys thus overriding what ever was defined in our XML
// TODO: Remove those at some point and put new keys in resources
// TODO: That does not make sense, we need to sort this out
private const val BLOCK_ADS = "AdBlock"
private const val CLEAR_CACHE_EXIT = "cache"
private const val DOWNLOAD_DIRECTORY = "downloadLocation"
private const val HOMEPAGE = "home"
private const val INCOGNITO = "incognito"
private const val SAVE_PASSWORDS = "passwords"
private const val SEARCH = "search"
private const val SEARCH_URL = "searchurl"
private const val USER_AGENT_STRING = "userAgentString"
private const val CLEAR_HISTORY_EXIT = "clearHistoryExit"
private const val CLEAR_COOKIES_EXIT = "clearCookiesExit"
private const val INVERT_COLORS = "invertColors"
private const val READING_TEXT_SIZE = "readingTextSize"
private const val CLEAR_WEB_STORAGE_EXIT = "clearWebStorageExit"
private const val DO_NOT_TRACK = "doNotTrack"
private const val IDENTIFYING_HEADERS = "removeIdentifyingHeaders"
private const val BOOKMARKS_CHANGED = "bookmarksChanged"
private const val SEARCH_SUGGESTIONS = "searchSuggestionsChoice"
