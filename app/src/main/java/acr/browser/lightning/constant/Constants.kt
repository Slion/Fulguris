/*
 * Copyright 2014 A.C.R. Development
 */
@file:JvmName("Constants")

package acr.browser.lightning.constant

// Hardcoded user agents
const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36"
const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 11; Pixel 5 Build/RQ1A.210205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.152 Mobile Safari/537.36"

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
    const val Bookmarks = "bookmarks"
    const val History = "history"
    const val Downloads = "downloads"
    const val Noop = "noop"
    const val Blank = "blank"
}

object Uris {
    const val FulgurisHome = "${Schemes.Fulguris}://${Hosts.Home}"
    const val FulgurisStart = "${Schemes.Fulguris}://${Hosts.Start}"
    const val FulgurisBookmarks = "${Schemes.Fulguris}://${Hosts.Bookmarks}"
    const val FulgurisDownloads = "${Schemes.Fulguris}://${Hosts.Downloads}"
    const val FulgurisHistory = "${Schemes.Fulguris}://${Hosts.History}"
    const val FulgurisNoop = "${Schemes.Fulguris}://${Hosts.Noop}"
    // Custom local page schemes
    const val AboutHome = "${Schemes.About}:${Hosts.Home}"
    const val AboutBlank = "${Schemes.About}:${Hosts.Blank}"
    const val AboutBookmarks = "${Schemes.About}:${Hosts.Bookmarks}"
    const val AboutHistory = "${Schemes.About}:${Hosts.History}"
}






const val UTF8 = "UTF-8"

// Default text encoding we will use
const val DEFAULT_ENCODING = UTF8

// Allowable text encodings for the WebView
@JvmField
val TEXT_ENCODINGS = arrayOf("ISO-8859-1", UTF8, "GBK", "Big5", "ISO-2022-JP", "SHIFT_JS", "EUC-JP", "EUC-KR")

const val INTENT_ORIGIN = "URL_INTENT_ORIGIN"
