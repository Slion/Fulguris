/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */
 
package fulguris.browser
import fulguris.extensions.createDefaultFavicon
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import fulguris.app
import java.io.ByteArrayOutputStream

/**
 * Tab model used to create a bundle from our tab.
 * Used to persist information belonging to a tab.
 */
open class TabModel (
    var url : String,
    var title : String,
    var desktopMode: Boolean,
    var darkMode: Boolean,
    var favicon : Bitmap,
    // Find in page search query
    var searchQuery: String,
    // Define if find in page search was active
    var searchActive: Boolean,
    // Actual WebView persisted bundle
    var webView : Bundle?
)
{
    fun toBundle() : Bundle {
        return Bundle(ClassLoader.getSystemClassLoader()).let {
                it.putString(URL_KEY, url)
                it.putString(TAB_TITLE_KEY, title)
                it.putBundle(WEB_VIEW_KEY, webView)
                it.putBoolean(KEY_DESKTOP_MODE, desktopMode)
                it.putBoolean(KEY_DARK_MODE, darkMode)
                it.putString(KEY_SEARCH_QUERY, searchQuery)
                it.putBoolean(KEY_SEARCH_ACTIVE, searchActive)
                favicon.apply {
                    // Crashlytics was showing our bitmap compression can lead to java.lang.IllegalStateException: Can't compress a recycled bitmap
                    // Therefore we now check if it was recycled before going ahead with compression.
                    // Otherwise we can still proceed without favicon anyway.
                    if (!isRecycled)
                    {
                        // Using PNG instead of WEBP as it is hopefully lossless
                        // Using WEBP results in the quality degrading reload after reload
                        // Maybe consider something like: https://stackoverflow.com/questions/8065050/convert-bitmap-to-byte-array-without-compress-method-in-android
                        val stream = ByteArrayOutputStream()
                        compress(Bitmap.CompressFormat.PNG, 100, stream)
                        val byteArray = stream.toByteArray()
                        it.putByteArray(TAB_FAVICON_KEY, byteArray)
                    }
                }
                it
            }
        }

    companion object {
        const val KEY_SEARCH_ACTIVE = "SEARCH_ACTIVE"
        const val KEY_SEARCH_QUERY = "SEARCH_QUERY"
        const val KEY_DARK_MODE = "DARK_MODE"
        const val KEY_DESKTOP_MODE = "DESKTOP_MODE"
        const val URL_KEY = "URL"
        const val TAB_TITLE_KEY = "TITLE"
        const val TAB_FAVICON_KEY = "FAVICON"
        const val WEB_VIEW_KEY = "WEB_VIEW"
    }
}

/**
 * Used to create a Tab Model from a bundle.
 */
class TabModelFromBundle (
        var bundle : Bundle
): TabModel(
        bundle.getString(URL_KEY)?:"",
        bundle.getString(TAB_TITLE_KEY)?:"",
        bundle.getBoolean(KEY_DESKTOP_MODE),
        bundle.getBoolean(KEY_DARK_MODE),
        bundle.getByteArray(TAB_FAVICON_KEY)?.let{BitmapFactory.decodeByteArray(it, 0, it.size)}
            // That was needed for smooth transition was previous model where favicon could be null
            // Past that transition it is just defensive code and should not execute anymore
            ?:app.createDefaultFavicon(),
        bundle.getString(KEY_SEARCH_QUERY)?:"",
        bundle.getBoolean(KEY_SEARCH_ACTIVE),
        bundle.getBundle(WEB_VIEW_KEY)
)
