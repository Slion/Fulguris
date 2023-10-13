/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("UrlUtils")

package fulguris.utils

import fulguris.app
import fulguris.constant.FILE
import fulguris.constant.Schemes
import fulguris.constant.Uris
import fulguris.html.bookmark.BookmarkPageFactory
import fulguris.html.download.DownloadPageFactory
import fulguris.html.history.HistoryPageFactory
import fulguris.html.homepage.HomePageFactory
import fulguris.html.incognito.IncognitoPageFactory
import android.net.Uri
import android.os.Environment
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.UnsupportedEncodingException
import java.util.*
import java.util.regex.Pattern

/**
 * Attempts to determine whether user input is a URL or search terms.  Anything with a space is
 * passed to search if [canBeSearch] is true.
 *
 * Converts to lowercase any mistakenly upper-cased scheme (i.e., "Http://" converts to
 * "http://")
 *
 * @param canBeSearch if true, will return a search url if it isn't a valid  URL. If false,
 * invalid URLs will return null.
 * @return original or modified URL.
 */
fun smartUrlFilter(url: String, canBeSearch: Boolean, searchUrl: String): Pair<String,Boolean> {
    var inUrl = url.trim()
    val hasSpace = inUrl.contains(' ')
    val matcher = ACCEPTED_URI_SCHEMA.matcher(inUrl)
    if (matcher.matches()) {
        // force scheme to lowercase
        val scheme = requireNotNull(matcher.group(1)) { "matches() implies this is non null" }
        val lcScheme = scheme.lowercase(Locale.getDefault())
        if (lcScheme != scheme) {
            inUrl = lcScheme + matcher.group(2)
        }
        if (hasSpace && Patterns.WEB_URL.matcher(inUrl).matches()) {
            inUrl = inUrl.replace(" ", URL_ENCODED_SPACE)
        }
        return Pair(inUrl,false)
    }
    if (!hasSpace) {
        if (Patterns.WEB_URL.matcher(inUrl).matches()) {
            return Pair(URLUtil.guessUrl(inUrl),false)
        }
    }

    return if (canBeSearch) {
        Pair(URLUtil.composeSearchUrl(inUrl, searchUrl, QUERY_PLACE_HOLDER),true)
    } else {
        Pair("",false)
    }
}

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isBookmarkUri(): Boolean =
    this == Uris.FulgurisBookmarks || this == Uris.AboutBookmarks

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isHomeUri(): Boolean =
    this == Uris.FulgurisHome || this == Uris.AboutHome

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isIncognitoUri(): Boolean =
    this == Uris.FulgurisIncognito || this == Uris.AboutIncognito

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isHistoryUri(): Boolean =
    this == Uris.FulgurisHistory || this == Uris.AboutHistory


/**
 * Returns whether the given url is the bookmarks/history page or a normal website
 * TODO: Review every usage of that one
 */
fun String?.isSpecialUrl(): Boolean =
    this != null
            && (this.startsWith(FILE + app.filesDir)
            && (this.endsWith(BookmarkPageFactory.FILENAME)
            || this.endsWith(DownloadPageFactory.FILENAME)
            || this.endsWith(HistoryPageFactory.FILENAME)
            || this.endsWith(HomePageFactory.FILENAME)
            || this.endsWith(IncognitoPageFactory.FILENAME))
            // TODO: That's somehow causing History page to be restored as Home page
            /*|| this.startsWith(Schemes.Fulguris + "://")*/)

/**
 * Check if this URL is using the specified scheme.
 */
fun String?.isScheme(aScheme: String): Boolean =
    this != null
            && this.startsWith("$aScheme:")


/**
 * Check if this URL is using any application specific schemes.
 */
fun String?.isAppScheme(): Boolean =
    isScheme(Schemes.Fulguris)
            || isScheme(Schemes.About)

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isBookmarkUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(BookmarkPageFactory.FILENAME)

/**
 * Determines if the url is a url for the bookmark page.
 *
 * @return true if the url is a bookmark url, false otherwise.
 */
fun String?.isDownloadsUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(DownloadPageFactory.FILENAME)

/**
 * Determines if the url is a url for the history page.
 *
 * @return true if the url is a history url, false otherwise.
 */
fun String?.isHistoryUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(HistoryPageFactory.FILENAME)

/**
 * Determines if the url is a url for the start page.
 *
 * @return true if the url is a start page url, false otherwise.
 */
fun String?.isStartPageUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(HomePageFactory.FILENAME)

/**
 * Determines if the url is a url for the incognito page.
 *
 * @return true if the url is a incognito page url, false otherwise.
 */
fun String?.isIncognitoPageUrl(): Boolean =
    this != null && this.startsWith(FILE) && this.endsWith(IncognitoPageFactory.FILENAME)

private val ACCEPTED_URI_SCHEMA = Pattern.compile("(?i)((?:http|https|file)://|(?:inline|data|about|javascript|fulguris):|(?:.*:.*@))(.*)")
const val QUERY_PLACE_HOLDER = "%s"
private const val URL_ENCODED_SPACE = "%20"

private const val contentDispositionType = "(inline|attachment)\\s*;"
private const val contentDispositionFileNameAsterisk = "\\s*filename\\*\\s*=\\s*(utf-8|iso-8859-1)'[^']*'(\\S*)"
private val contentDispositionPattern = Pattern.compile("$contentDispositionType\\s*filename\\s*=\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|[^;]*)\\s*(?:;$contentDispositionFileNameAsterisk)?", Pattern.CASE_INSENSITIVE)
private val fileNameAsteriskContentDispositionPattern = Pattern.compile(contentDispositionType + contentDispositionFileNameAsterisk, Pattern.CASE_INSENSITIVE)
private const val ENCODED_FILE_NAME_GROUP = 5
private const val ENCODING_GROUP = 4
private const val QUOTED_FILE_NAME_GROUP = 3
private const val UNQUOTED_FILE_NAME = 2
private const val ALTERNATIVE_FILE_NAME_GROUP = 3
private const val ALTERNATIVE_ENCODING_GROUP = 2
private val encodedSymbolPattern = Pattern.compile("%[0-9a-f]{2}|[0-9a-z!#$&+-.^_`|~]", Pattern.CASE_INSENSITIVE)

private val GENERIC_CONTENT_TYPES = arrayOf(
    "application/octet-stream",
    "binary/octet-stream",
    "application/unknown"
)

/**
 * This function and all the ones it is using below were taken from there:
 * https://github.com/mozilla-mobile/android-components/blob/master/components/support/utils/src/main/java/mozilla/components/support/utils/DownloadUtils.kt
 */
//@Suppress("DEPRECATION")
fun guessFileName(
    url: String?,
    contentDisposition: String?,
    mimeType: String?,
    destinationDirectory: String?
): String {
    // Split fileName between base and extension
    // Add an extension if filename does not have one
    val extractedFileName = extractFileNameFromUrl(contentDisposition, url)
    val sanitizedMimeType = sanitizeMimeType(mimeType)

    val fileName = if (extractedFileName.contains('.')) {
        if (GENERIC_CONTENT_TYPES.contains(mimeType)) {
            extractedFileName
        } else {
            changeExtension(extractedFileName, sanitizedMimeType)
        }
    } else {
        extractedFileName + createExtension(sanitizedMimeType)
    }

    return destinationDirectory?.let {
        uniqueFileName(Environment.getExternalStoragePublicDirectory(destinationDirectory), fileName)
    } ?: fileName
}

// Some site add extra information after the mimetype, for example 'application/pdf; qs=0.001'
// we just want to extract the mimeType and ignore the rest.
fun sanitizeMimeType(mimeType: String?): String? {
    return (if (mimeType != null) {
        if (mimeType.contains(";")) {
            mimeType.substringBefore(";")
        } else {
            mimeType
        }
    } else {
        null
    })?.trim()
}

/**
 * Checks if the file exists so as not to overwrite one already in the destination directory
 */
fun uniqueFileName(directory: File, fileName: String): String {
    var fileExtension = ".${fileName.substringAfterLast(".")}"

    // Check if an extension was found or not
    if (fileExtension == ".$fileName") { fileExtension = "" }

    val baseFileName = fileName.replace(fileExtension, "")

    var potentialFileName = File(directory, fileName)
    var copyVersionNumber = 1

    while (potentialFileName.exists()) {
        potentialFileName = File(directory, "$baseFileName($copyVersionNumber)$fileExtension")
        copyVersionNumber += 1
    }

    return potentialFileName.name
}

private fun extractFileNameFromUrl(contentDisposition: String?, url: String?): String {
    var filename: String? = null

    // Extract file name from content disposition header field
    if (contentDisposition != null) {
        filename = parseContentDisposition(contentDisposition)?.substringAfterLast('/')
    }

    // If all the other http-related approaches failed, use the plain uri
    if (filename == null) {
        // If there is a query string strip it, same as desktop browsers
        val decodedUrl: String? = Uri.decode(url)?.substringBefore('?')
        if (decodedUrl?.endsWith('/') == false) {
            filename = decodedUrl.substringAfterLast('/')
        }
    }

    // Finally, if couldn't get filename from URI, get a generic filename
    if (filename == null) {
        filename = "unknown"
    }

    return filename
}

private fun parseContentDisposition(contentDisposition: String): String? {
    return try {
        parseContentDispositionWithFileName(contentDisposition)
            ?: parseContentDispositionWithFileNameAsterisk(contentDisposition)
    } catch (ex: IllegalStateException) {
        // This function is defined as returning null when it can't parse the header
        null
    } catch (ex: UnsupportedEncodingException) {
        // Do nothing
        null
    }
}

private fun parseContentDispositionWithFileName(contentDisposition: String): String? {
    val m = contentDispositionPattern.matcher(contentDisposition)
    return if (m.find()) {
        val encodedFileName = m.group(ENCODED_FILE_NAME_GROUP)
        val encoding = m.group(ENCODING_GROUP)
        if (encodedFileName != null && encoding != null) {
            decodeHeaderField(encodedFileName, encoding)
        } else {
            // Return quoted string if available and replace escaped characters.
            val quotedFileName = m.group(QUOTED_FILE_NAME_GROUP)
            quotedFileName?.replace("\\\\(.)".toRegex(), "$1")
                ?: m.group(UNQUOTED_FILE_NAME)
        }
    } else {
        null
    }
}

private fun parseContentDispositionWithFileNameAsterisk(contentDisposition: String): String? {
    val alternative = fileNameAsteriskContentDispositionPattern.matcher(contentDisposition)

    return if (alternative.find()) {
        val encoding = alternative.group(ALTERNATIVE_ENCODING_GROUP) ?: return null
        val fileName = alternative.group(ALTERNATIVE_FILE_NAME_GROUP) ?: return null
        decodeHeaderField(fileName, encoding)
    } else {
        null
    }
}

private fun decodeHeaderField(field: String, encoding: String): String {
    val m = encodedSymbolPattern.matcher(field)
    val stream = ByteArrayOutputStream()

    while (m.find()) {
        val symbol = m.group()

        if (symbol.startsWith("%")) {
            stream.write(symbol.substring(1).toInt(radix = 16))
        } else {
            stream.write(symbol[0].code)
        }
    }

    return stream.toString(encoding)
}

/**
 * Compare the filename extension with the mime type and change it if necessary.
 */
private fun changeExtension(filename: String, mimeType: String?): String {
    var extension: String? = null
    val dotIndex = filename.lastIndexOf('.')

    if (mimeType != null) {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        // Compare the last segment of the extension against the mime type.
        // If there's a mismatch, discard the entire extension.
        val typeFromExt = mimeTypeMap.getMimeTypeFromExtension(filename.substringAfterLast('.'))
        if (typeFromExt?.equals(mimeType, ignoreCase = true) != false) {
            extension = mimeTypeMap.getExtensionFromMimeType(mimeType)?.let { ".$it" }
            // Check if the extension needs to be changed
            if (extension != null && filename.endsWith(extension, ignoreCase = true)) {
                return filename
            }
        }
    }

    return if (extension != null) {
        filename.substring(0, dotIndex) + extension
    } else {
        filename
    }
}

/**
 * Guess the extension for a file using the mime type.
 */
private fun createExtension(mimeType: String?): String {
    var extension: String? = null

    if (mimeType != null) {
        extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { ".$it" }
    }
    if (extension == null) {
        extension = if (mimeType?.startsWith("text/", ignoreCase = true) == true) {
            // checking startsWith to ignoring encoding value such as "text/html; charset=utf-8"
            if (mimeType.startsWith("text/html", ignoreCase = true)) {
                ".html"
            } else {
                ".txt"
            }
        } else {
            // If there's no mime type assume binary data
            ".bin"
        }
    }

    return extension
}
