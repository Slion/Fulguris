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
const val ABOUT = "about:"
const val FOLDER = "folder://"

// Custom local page schemes
const val SCHEME_HOMEPAGE = "${ABOUT}home"
const val SCHEME_BLANK = "${ABOUT}blank"
const val SCHEME_BOOKMARKS = "${ABOUT}bookmarks"

const val UTF8 = "UTF-8"

// Default text encoding we will use
const val DEFAULT_ENCODING = UTF8

// Allowable text encodings for the WebView
@JvmField
val TEXT_ENCODINGS = arrayOf("ISO-8859-1", UTF8, "GBK", "Big5", "ISO-2022-JP", "SHIFT_JS", "EUC-JP", "EUC-KR")

const val INTENT_ORIGIN = "URL_INTENT_ORIGIN"
