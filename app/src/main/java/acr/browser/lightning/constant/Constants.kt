/*
 * Copyright 2014 A.C.R. Development
 */
@file:JvmName("Constants")

package acr.browser.lightning.constant

// Hardcoded user agents
const val WINDOWS_DESKTOP_USER_AGENT_PREFIX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
const val LINUX_DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:85.0) Gecko/20100101 Firefox/85.0"
const val MACOS_DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 11_2_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.2 Safari/605.1.15"
const val ANDROID_MOBILE_USER_AGENT_PREFIX = "Mozilla/5.0 (Linux; Android 11; Pixel 5 Build/RQ1A.210205.004; wv)"
const val IOS_MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"

// URL Schemes
const val HTTP = "http://"
const val HTTPS = "https://"
const val FILE = "file://"
const val FOLDER = "folder://"

object Schemes {
    const val Fulguris = "fulguris"
    const val About = "about"
}

object Hosts {
    const val Home = "home"
    const val Start = "start"
    const val Incognito = "incognito"
    const val Bookmarks = "bookmarks"
    const val History = "history"
    const val Downloads = "downloads"
    const val Noop = "noop"
    const val Blank = "blank"
}

object Uris {
    const val FulgurisHome = "${Schemes.Fulguris}://${Hosts.Home}"
    const val FulgurisStart = "${Schemes.Fulguris}://${Hosts.Start}"
    const val FulgurisIncognito = "${Schemes.Fulguris}://${Hosts.Incognito}"
    const val FulgurisBookmarks = "${Schemes.Fulguris}://${Hosts.Bookmarks}"
    const val FulgurisDownloads = "${Schemes.Fulguris}://${Hosts.Downloads}"
    const val FulgurisHistory = "${Schemes.Fulguris}://${Hosts.History}"
    const val FulgurisNoop = "${Schemes.Fulguris}://${Hosts.Noop}"
    // Custom local page schemes
    const val AboutHome = "${Schemes.About}:${Hosts.Home}"
    const val AboutIncognito = "${Schemes.About}:${Hosts.Incognito}"
    const val AboutBlank = "${Schemes.About}:${Hosts.Blank}"
    const val AboutBookmarks = "${Schemes.About}:${Hosts.Bookmarks}"
    const val AboutHistory = "${Schemes.About}:${Hosts.History}"
}

object PrefKeys {
    const val HideStatusBar = "pref_key_hide_status_bar"
    const val HideToolBar = "pref_key_hide_tool_bar"
    const val ShowToolBarWhenScrollUp = "pref_key_show_tool_bar_on_scroll_up"
    const val ShowToolBarOnPageTop = "pref_key_show_tool_bar_on_page_top"
    const val DesktopWidth = "pref_key_desktop_width"
    const val PullToRefresh = "pref_key_pull_to_refresh"
    const val TabBarVertical = "pref_key_tab_bar_vertical"
    const val TabBarInDrawer = "pref_key_tab_bar_in_drawer"
    const val ToolbarsBottom = "pref_key_toolbars_bottom"
}


const val UTF8 = "UTF-8"

// Default text encoding we will use
const val DEFAULT_ENCODING = UTF8

// Allowable text encodings for the WebView
@JvmField
val TEXT_ENCODINGS = arrayOf(UTF8, "Big5", "Big5-HKSCS", "CESU-8", "EUC-JP", "EUC-KR", "GB18030", "GB2312", "GBK", "IBM-Thai", "IBM00858", "IBM01140", "IBM01141", "IBM01142", "IBM01143", "IBM01144", "IBM01145", "IBM01146", "IBM01147", "IBM01148", "IBM01149", "IBM037", "IBM1026", "IBM1047", "IBM273", "IBM277", "IBM278", "IBM280", "IBM284", "IBM285", "IBM290", "IBM297", "IBM420", "IBM424", "IBM437", "IBM500", "IBM775", "IBM850", "IBM852", "IBM855", "IBM857", "IBM860", "IBM861", "IBM862", "IBM863", "IBM864", "IBM865", "IBM866", "IBM868", "IBM869", "IBM870", "IBM871", "IBM918", "ISO-2022-CN", "ISO-2022-JP", "ISO-2022-JP-2", "ISO-2022-KR", "ISO-8859-1", "ISO-8859-13", "ISO-8859-15", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4", "ISO-8859-5", "ISO-8859-6", "ISO-8859-7", "ISO-8859-8", "ISO-8859-9", "JIS_X0201", "JIS_X0212-1990", "KOI8-R", "KOI8-U", "Shift_JIS", "TIS-620", "US-ASCII", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-32", "UTF-32BE", "UTF-32LE", "windows-1250", "windows-1251", "windows-1252", "windows-1253", "windows-1254", "windows-1255", "windows-1256", "windows-1257", "windows-1258", "windows-31j", "x-Big5-HKSCS-2001", "x-Big5-Solaris", "x-COMPOUND_TEXT", "x-euc-jp-linux", "x-EUC-TW", "x-eucJP-Open", "x-IBM1006", "x-IBM1025", "x-IBM1046", "x-IBM1097", "x-IBM1098", "x-IBM1112", "x-IBM1122", "x-IBM1123", "x-IBM1124", "x-IBM1166", "x-IBM1364", "x-IBM1381", "x-IBM1383", "x-IBM300", "x-IBM33722", "x-IBM737", "x-IBM833", "x-IBM834", "x-IBM856", "x-IBM874", "x-IBM875", "x-IBM921", "x-IBM922", "x-IBM930", "x-IBM933", "x-IBM935", "x-IBM937", "x-IBM939", "x-IBM942", "x-IBM942C", "x-IBM943", "x-IBM943C", "x-IBM948", "x-IBM949", "x-IBM949C", "x-IBM950", "x-IBM964", "x-IBM970", "x-ISCII91", "x-ISO-2022-CN-CNS", "x-ISO-2022-CN-GB", "x-iso-8859-11", "x-JIS0208", "x-JISAutoDetect", "x-Johab", "x-MacArabic", "x-MacCentralEurope", "x-MacCroatian", "x-MacCyrillic", "x-MacDingbat", "x-MacGreek", "x-MacHebrew", "x-MacIceland", "x-MacRoman", "x-MacRomania", "x-MacSymbol", "x-MacThai", "x-MacTurkish", "x-MacUkraine", "x-MS932_0213", "x-MS950-HKSCS", "x-MS950-HKSCS-XP", "x-mswin-936", "x-PCK", "x-SJIS_0213", "x-UTF-16LE-BOM", "X-UTF-32BE-BOM", "X-UTF-32LE-BOM", "x-windows-50220", "x-windows-50221", "x-windows-874", "x-windows-949", "x-windows-950", "x-windows-iso2022jp")

const val INTENT_ORIGIN = "URL_INTENT_ORIGIN"
