/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.adblock

import fulguris.adblock.AbpBlockerManager.Companion.MIME_TYPE_UNKNOWN
import fulguris.adblock.AbpBlockerManager.Companion.getMimeTypeFromExtension
import android.net.Uri
import android.webkit.WebResourceRequest
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest

fun WebResourceRequest.getContentType(pageUri: Uri): Int {
    var type = 0
    val scheme = url.scheme
//    var isPage = false
    val accept by lazy { requestHeaders["Accept"] }

    if (isForMainFrame) {
        if (url == pageUri) {
//            isPage = true
            type = ContentRequest.TYPE_DOCUMENT
        }
    } else if (accept != null && accept!!.contains("text/html"))
        type = ContentRequest.TYPE_SUB_DOCUMENT

    if (scheme == "ws" || scheme == "wss") {
        type = type or ContentRequest.TYPE_WEB_SOCKET
    }

    if (requestHeaders["X-Requested-With"] == "XMLHttpRequest") {
        type = type or ContentRequest.TYPE_XHR
    }

    val path = url.path ?: url.toString()
    val lastDot = path.lastIndexOf('.')
    if (lastDot >= 0) {
        when (val extension = path.substring(lastDot + 1).lowercase().substringBefore('?')) {
            "js" -> return type or ContentRequest.TYPE_SCRIPT
            "css" -> return type or ContentRequest.TYPE_STYLE_SHEET
            "otf", "ttf", "ttc", "woff", "woff2" -> return type or ContentRequest.TYPE_FONT
            "php" -> Unit
            else -> {
                val mimeType = getMimeTypeFromExtension(extension)
                if (mimeType != MIME_TYPE_UNKNOWN) {
                    return otherIfNone(type or mimeType.getFilterType())
                }
            }
        }
    }

    // why was this here?
    // breaks many $~document filters, because if there is no file extension (which is very common for main frame urls)
    //  then type_document ALWAYS has type_other and thus is blocked by $~document
//    if (isPage) {
//        return type or ContentRequest.TYPE_OTHER
//    }

    return if (accept != null && accept != "*/*") {
        val mimeType = accept!!.split(',')[0]
        otherIfNone(type or mimeType.getFilterType())
    } else {
        type or ContentRequest.TYPE_OTHER or ContentRequest.TYPE_MEDIA or ContentRequest.TYPE_IMAGE or
            ContentRequest.TYPE_FONT or ContentRequest.TYPE_STYLE_SHEET or ContentRequest.TYPE_SCRIPT
    }
}

fun String.getFilterType(): Int {
    return when (this) {
        "application/javascript",
        "application/x-javascript",
        "text/javascript",
        "application/json" -> ContentRequest.TYPE_SCRIPT
        "text/css" -> ContentRequest.TYPE_STYLE_SHEET
        else -> when {
            startsWith("text/") -> 0 // don't add other for all text documents!
            startsWith("image/") -> ContentRequest.TYPE_IMAGE
            startsWith("video/") || startsWith("audio/") -> ContentRequest.TYPE_MEDIA
            startsWith("font/") -> ContentRequest.TYPE_FONT
            else -> ContentRequest.TYPE_OTHER
        }
    }
}

// text documents might have no type -> at least give them other, or filtering doesn't work
fun otherIfNone(type: Int) =
    if (type == 0) ContentRequest.TYPE_OTHER else type
