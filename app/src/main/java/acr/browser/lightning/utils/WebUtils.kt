package acr.browser.lightning.utils

import acr.browser.lightning.database.history.HistoryRepository
import acr.browser.lightning.utils.Utils.trimCache
import android.content.Context
import android.webkit.*
import io.reactivex.Scheduler
import java.io.File

/**
 * Copyright 8/4/2015 Anthony Restaino
 */
object WebUtils {
    fun clearCookies() {
        val c = CookieManager.getInstance()
        c.removeAllCookies(null)
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