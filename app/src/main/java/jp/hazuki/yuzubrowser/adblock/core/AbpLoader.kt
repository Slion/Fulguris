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

package jp.hazuki.yuzubrowser.adblock.core

import fulguris.adblock.AbpBlockerManager.Companion.isModify
import jp.hazuki.yuzubrowser.adblock.filter.abp.ABP_PREFIX_ELEMENT
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.ElementReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import java.io.File
import java.io.IOException

class AbpLoader(private val abpDir: File, private val entityList: List<AbpEntity>) {

    fun loadAll(prefix: String) = sequence {
        entityList.forEach {
            if (!it.enabled) return@forEach
            try {
                val file = File(abpDir, prefix + it.entityId)
                if (!file.exists()) {
                    return@forEach }
                file.inputStream().buffered().use { ins ->
                    val reader = FilterReader(ins)
                    if (reader.checkHeader()) {
                        if (isModify(prefix))
                            yieldAll(reader.readAllModifyFilters())
                        else
                            yieldAll(reader.readAll())
                    }
                }
            } catch (e: IOException) {
//                ErrorReport.printAndWriteLog(e)
            }
        }
    }

    fun loadAllElementFilter() = sequence {
        entityList.forEach {
            if (!it.enabled) return@forEach

            try {
                val file = File(abpDir, ABP_PREFIX_ELEMENT + it.entityId)
                if (!file.exists()) return@forEach
                file.inputStream().buffered().use { ins ->
                    val reader = ElementReader(ins)
                    if (reader.checkHeader()) {
                        yieldAll(reader.readAll())
                    }
                }
            } catch (e: IOException) {
//                ErrorReport.printAndWriteLog(e)
            }
        }
    }
}
