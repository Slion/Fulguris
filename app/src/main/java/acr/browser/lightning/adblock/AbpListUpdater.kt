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

package acr.browser.lightning.adblock

import acr.browser.lightning.adblock.parser.HostsFileParser
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.preferences.UserPreferences
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
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
import javax.inject.Inject
import kotlin.math.max

// this is a slightly modified part of jp.hazuki.yuzubrowser.adblock.service/AbpUpdateService.kt
class AbpListUpdater @Inject constructor(val context: Context) {

    //@Inject internal lateinit var okHttpClient: OkHttpClient
    val okHttpClient = OkHttpClient() // any problems if not injecting?

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var logger: Logger

    val abpDao = AbpDao(context)

    fun updateAll(forceUpdate: Boolean): Boolean {
        var result = false
        runBlocking {

            var nextUpdateTime = Long.MAX_VALUE
            val now = System.currentTimeMillis()
            abpDao.getAll().forEach {
                if (forceUpdate || (it.isNeedUpdate() && it.enabled)) {
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
        writer.write(dir.getAbpBlackListFile(entity), listOf())
        writer.write(dir.getAbpWhiteListFile(entity), listOf())
        writer.write(dir.getAbpWhitePageListFile(entity), listOf())
        writer.write(dir.getAbpModifyListFile(entity), listOf())
        writer.write(dir.getAbpModifyExceptionListFile(entity), listOf())

        val elementWriter = ElementWriter()
        elementWriter.write(dir.getAbpElementListFile(entity), listOf())
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
                .get()
        } catch (e: IllegalArgumentException) {
            return false
        }

        if (!forceUpdate) {
            entity.lastModified?.let {
                val dir = getFilterDir()

                if (dir.getAbpBlackListFile(entity).exists() ||
                    dir.getAbpWhiteListFile(entity).exists() ||
                    dir.getAbpWhitePageListFile(entity).exists() ||
                    dir.getAbpModifyListFile(entity).exists() ||
                    dir.getAbpModifyExceptionListFile(entity).exists())
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
            //  TODO: adjust hosts parser? accepts really a lot of not really suitable lines as hosts
            //   no real problem, but they clutter the list (mostly slows down loading)
            val parser = HostsFileParser(logger)
            // TODO: HostFilter or StartEndFilter?
            //  HostFilter is exact host match, StartEndFilter also matches subdomains
            //  if StartEndFilter is the choice, we could remove unnecessary subdomains (e.g. ads.example.com if example.com is on list)
            //   or rather do it when loading / creating joint lists?
            val hostsList = parser.parseInput(reader).map {StartEndFilter(it.name,0xffff, false, null, -1)}
            if (hostsList.isEmpty())
                return false
            entity.lastLocalUpdate = System.currentTimeMillis()
            writer.write(dir.getAbpBlackListFile(entity), hostsList)
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
        entity.lastLocalUpdate = System.currentTimeMillis()
        writer.write(dir.getAbpBlackListFile(entity), set.blackList)
        writer.write(dir.getAbpWhiteListFile(entity), set.whiteList)
        writer.write(dir.getAbpWhitePageListFile(entity), set.elementDisableFilter)
        writer.writeModifyFilters(dir.getAbpModifyListFile(entity), set.modifyList)
        writer.writeModifyFilters(dir.getAbpModifyExceptionListFile(entity), set.modifyExceptionList)

        val elementWriter = ElementWriter()
        elementWriter.write(dir.getAbpElementListFile(entity), set.elementList)

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

    private fun FilterWriter.writeModifyFilters(file: File, list: List<Pair<UnifiedFilter, String>>) {
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

    private fun AbpEntity.isNeedUpdate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLocalUpdate >= max(expires * AN_HOUR, A_DAY * userPreferences.blockListAutoUpdateFrequency)) {
            return true
        }
        return false
    }

    companion object {
        private const val AN_HOUR = 60 * 60 * 1000
        private const val A_DAY = 24 * AN_HOUR

    }

}
