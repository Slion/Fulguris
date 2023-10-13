/*
 * Copyright (C) 2017-2021 Hazuki
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

package fulguris.adblock

import fulguris.R
import fulguris.adblock.AbpBlockerManager.Companion.blockerPrefixes
import fulguris.adblock.AbpBlockerManager.Companion.isModify
import fulguris.adblock.parser.HostsFileParser
import fulguris.extensions.toast
import fulguris.settings.preferences.UserPreferences
import fulguris.settings.preferences.userAgent
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.FILTER_DIR
import jp.hazuki.yuzubrowser.adblock.filter.unified.StartEndFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.ElementWriter
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max

// this is a slightly modified part of jp.hazuki.yuzubrowser.adblock.service/AbpUpdateService.kt
class AbpListUpdater @Inject constructor(val context: Context) {

    val okHttpClient by lazy {
        OkHttpClient().newBuilder()
            .callTimeout(5, TimeUnit.MINUTES)
            .connectTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    @Inject internal lateinit var userPreferences: UserPreferences

    val abpDao = AbpDao(context)

    fun updateAll(forceUpdate: Boolean): Boolean {
        var result = false
        runBlocking {

            var nextUpdateTime = Long.MAX_VALUE
            val now = System.currentTimeMillis()
            abpDao.getAll().forEach {
                if (forceUpdate || (needsUpdate(it) && it.enabled)) {
                    val localResult = updateInternal(it, forceUpdate)
                    if (localResult && it.expires > 0) {
                        val nextTime = it.expires * AN_HOUR + now
                        if (nextTime < nextUpdateTime) nextUpdateTime = nextTime
                    }
                    result = result or localResult
                }
            }
        }
        return result
    }

    fun removeFiles(entity: AbpEntity) {
        val dir = getFilterDir()
        val writer = FilterWriter()
        (blockerPrefixes + ABP_PREFIX_DISABLE_ELEMENT_PAGE).forEach {
            writer.write(dir.getFilterFile(it, entity), listOf())
        }

        val elementWriter = ElementWriter()
        elementWriter.write(dir.getFilterFile(ABP_PREFIX_ELEMENT, entity), listOf())
    }

    fun updateAbpEntity(entity: AbpEntity, forceUpdate: Boolean) = runBlocking {
        updateInternal(entity, forceUpdate)
    }

    private suspend fun updateInternal(entity: AbpEntity, forceUpdate: Boolean = false): Boolean {
        return when {
            entity.url.startsWith("http") -> updateHttp(entity, forceUpdate)
            entity.url.startsWith("file") -> updateFile(entity)
            else -> false
        }
    }

    private fun getFilterDir() = context.getDir(FILTER_DIR, Context.MODE_PRIVATE)

    private suspend fun updateHttp(entity: AbpEntity, forceUpdate: Boolean): Boolean {
        // don't update if auto-update settings don't allow
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (!forceUpdate
            && ((userPreferences.blockListAutoUpdate == AbpUpdateMode.WIFI_ONLY && cm.isActiveNetworkMetered)
                    || userPreferences.blockListAutoUpdate == AbpUpdateMode.NONE))
            return false

        val request = try {
            Request.Builder()
                .url(entity.url)
                .header("User-Agent", userPreferences.userAgent(context.applicationContext as Application))
                .get()
        } catch (e: IllegalArgumentException) {
            return false
        }

        if (!forceUpdate) {
            entity.lastModified?.let {
                val dir = getFilterDir()
                if ((blockerPrefixes + ABP_PREFIX_DISABLE_ELEMENT_PAGE + ABP_PREFIX_ELEMENT).map { prefix ->
                    dir.getFilterFile(prefix, entity).exists() }.any()
                )
                    request.addHeader("If-Modified-Since", it)
            }
        }

        val call = okHttpClient.newCall(request.build())
        try {
            val response = call.execute()

            if (response.code == 304) {
                entity.lastLocalUpdate = System.currentTimeMillis()
                abpDao.update(entity)
                return false
            }
            if (!response.isSuccessful) {
                Handler(Looper.getMainLooper()).post {
                    context.toast(context.getString(R.string.blocklist_update_error_code, entity.title, response.code.toString()))
                }
                return false
            }
            response.body?.run {
                val charset = contentType()?.charset() ?: Charsets.UTF_8
                source().inputStream().bufferedReader(charset).use { reader ->
                    if (decode(reader, charset, entity)) {
                        entity.lastLocalUpdate = System.currentTimeMillis()
                        entity.lastModified = response.header("Last-Modified")
                        abpDao.update(entity)
                        return true
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                context.toast(context.getString(R.string.blocklist_update_error, entity.title))
            }
        }
        return false
    }

    private suspend fun updateFile(entity: AbpEntity): Boolean {
        val path = Uri.parse(entity.url).path ?: return false
        val file = File(path)
        if (file.lastModified() < entity.lastLocalUpdate) return false

        try {
            file.inputStream().bufferedReader().use { reader ->
                return decode(reader, Charsets.UTF_8, entity)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private suspend fun decode(reader: BufferedReader, charset: Charset, entity: AbpEntity): Boolean {
        val decoder = AbpFilterDecoder()
        val dir = getFilterDir()
        val writer = FilterWriter()

        if (!decoder.checkHeader(reader, charset)) {
            // no adblock plus format, try hosts reader
            val parser = HostsFileParser()
            // use StartEndFilter, which also matches subdomains
            //  not strictly according to hosts rules, but uBlock does the same (and it makes sense)
            val hostsList = parser.parseInput(reader).map {
                StartEndFilter(it.name, ContentRequest.TYPE_ALL, false, null, -1)
            }
            if (hostsList.isEmpty())
                return false
            entity.lastLocalUpdate = System.currentTimeMillis()
            writer.write(dir.getFilterFile(ABP_PREFIX_DENY, entity), hostsList)
            abpDao.update(entity)

            return true
        }

        val set = decoder.decode(reader, entity.url)

        val info = set.filterInfo
        if (entity.title == null) // only update title if there is none
            entity.title = info.title
        entity.expires = info.expires ?: -1
        entity.homePage = info.homePage
        entity.version = info.version
        entity.lastUpdate = info.lastUpdate
        info.redirectUrl?.let { entity.url = it }
        entity.lastLocalUpdate = System.currentTimeMillis()
        blockerPrefixes.forEach {
            if (isModify(it))
                writer.writeModifyFilters(dir.getFilterFile(it, entity), set.filters[it])
            else
                writer.write(dir.getFilterFile(it, entity), set.filters[it])
        }
        writer.write(dir.getFilterFile(ABP_PREFIX_DISABLE_ELEMENT_PAGE,entity), set.elementDisableFilter)

        val elementWriter = ElementWriter()
        elementWriter.write(dir.getFilterFile(ABP_PREFIX_ELEMENT, entity), set.elementList)

        abpDao.update(entity)
        return true
    }

    private fun FilterWriter.write(file: File, list: List<UnifiedFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    write(it, list)
                }
            } catch (e: IOException) {
//                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    private fun FilterWriter.writeModifyFilters(file: File, list: List<UnifiedFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    writeModifyFilters(it, list)
                }
            } catch (e: IOException) {
//                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    private fun ElementWriter.write(file: File, list: List<ElementFilter>) {
        if (list.isNotEmpty()) {
            try {
                file.outputStream().buffered().use {
                    write(it, list)
                }
            } catch (e: IOException) {
//                ErrorReport.printAndWriteLog(e)
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    fun needsUpdate(entity: AbpEntity): Boolean {
        val now = System.currentTimeMillis()
        if (now - entity.lastLocalUpdate >= max(entity.expires * AN_HOUR, A_DAY * userPreferences.blockListAutoUpdateFrequency)) {
            return true
        }
        return false
    }

    companion object {
        private const val AN_HOUR = 60 * 60 * 1000L
        private const val A_DAY = 24 * AN_HOUR

    }

}
