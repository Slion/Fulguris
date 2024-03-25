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
 * All portions of the code written by Stéphane Lenclud are Copyright © 2024 Stéphane Lenclud.
 * All Rights Reserved.
 *
 * Copyright 8/4/2015 Anthony Restaino
 */

package fulguris.utils

import fulguris.database.history.HistoryRepository
import fulguris.utils.Utils.trimCache
import android.content.Context
import android.webkit.*
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import io.reactivex.Scheduler
import timber.log.Timber
import java.io.File

/**
 * Some web utility functions
 */
object WebUtils {

    /**
     * Provide a list of cookies either using newest API which provides all cookie attributes or using legacy method which only provides cookie name and value
     */
    fun getCookies(url: String): List<String> {
        val cm = CookieManager.getInstance()

        return if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO) /*&& false*/) {
            // New way provides all cookies attributes
            CookieManagerCompat.getCookieInfo(cm,url)
        } else {
            // Legacy method provides only cookie name and value
            cm.getCookie(url)?.apply{Timber.v("Raw cookies: $this")}?.split(';') ?: emptyList()
        }
    }

    fun clearCookies(callback: ValueCallback<Boolean>? = null) {
        val c = CookieManager.getInstance()
        c.removeAllCookies {
            Timber.i("removeAllCookies: $it")
            callback?.onReceiveValue(it)
        }
    }

    fun clearWebStorage() {
        WebStorage.getInstance().deleteAllData()
    }

    fun clearHistory(
        context: Context,
        historyRepository: HistoryRepository,
        databaseScheduler: Scheduler
    ) {
        historyRepository.deleteHistory()
            .subscribeOn(databaseScheduler)
            .subscribe()
        val webViewDatabase = WebViewDatabase.getInstance(context)
        webViewDatabase.clearHttpAuthUsernamePassword()
        trimCache(context)
    }

    fun clearCache(view: WebView?, context: Context) {
        if (view == null) return
        view.clearCache(true)
        deleteCache(context)
    }

    private fun deleteCache(context: Context) {
        try {
            val dir = context.cacheDir
            deleteDir(dir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        return if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
            dir.delete()
        } else if (dir != null && dir.isFile) {
            dir.delete()
        } else {
            false
        }
    }
}