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

package jp.hazuki.yuzubrowser.adblock.filter.unified.io

import jp.hazuki.yuzubrowser.adblock.filter.toByteArray
import jp.hazuki.yuzubrowser.adblock.filter.toShortByteArray
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.FILTER_CACHE_HEADER
import jp.hazuki.yuzubrowser.adblock.filter.unified.FILTER_TYPE_START_END
import jp.hazuki.yuzubrowser.adblock.filter.unified.writeVariableInt
import java.io.OutputStream
import kotlin.math.min

class FilterWriter {
    private val intBuf = ByteArray(4)
    private val shortBuf = ByteArray(2)

    fun write(os: OutputStream, filters: List<UnifiedFilter>) {
        writeHeader(os)
        writeAll(os, filters)
        writeHeader(os)
    }

    // second part in pair is the modify filter
    fun writeModifyFilters(os: OutputStream, filters: List<UnifiedFilter>) {
        writeHeader(os)
        writeAllModifyFilters(os, filters)
        writeHeader(os)
    }

    private fun writeHeader(os: OutputStream) {
        os.write(FILTER_CACHE_HEADER.toByteArray())
    }

    private fun writeAll(os: OutputStream, filters: List<UnifiedFilter>) {
        os.write(filters.size.toByteArray(intBuf))

        filters.forEach {
            writeFilter(os, it)
        }
    }

    private fun writeAllModifyFilters(os: OutputStream, filters: List<UnifiedFilter>) {
        os.write(filters.size.toByteArray(intBuf))

        // catch and log error if modify is null?
        filters.forEach {
            writeFilter(os, it)
            val modifyString = "" + it.modify!!.prefix + (if (it.modify!!.inverse) 1 else 0) + it.modify!!.parameter
            writeModify(os, modifyString)
        }
    }

    private fun writeModify(os: OutputStream, param: String) {
        val modifyBytes = param.toByteArray()
        os.write(modifyBytes.size.toShortByteArray(shortBuf))
        os.write(modifyBytes)
    }

    private fun writeFilter(os: OutputStream, it: UnifiedFilter) {
        // write simplified for simple domain filters
        //  increases write and read speeds
        if (it.filterType == FILTER_TYPE_START_END && !it.pattern.contains('/')
            && !it.pattern.endsWith('.') && it.domains == null && !it.ignoreCase) {
            os.write(FILTER_TYPE_START_END_DOMAIN and 0xff)
            os.write(it.contentType.toShortByteArray(shortBuf))
            os.write(it.thirdParty and 0xff)
            val patternBytes = it.pattern.toByteArray()
            os.write(patternBytes.size.toShortByteArray(shortBuf))
            os.write(patternBytes)
            return
        }

        os.write(it.filterType and 0xff)
        os.write(it.contentType.toShortByteArray(shortBuf))
        os.write(it.thirdParty and 0xff)

        val patternBytes = it.pattern.toByteArray()
        os.writeVariableInt(patternBytes.size, shortBuf, intBuf)
        os.write(patternBytes)

        os.write(if (it.ignoreCase) 1 else 0)

        // also write best tag
        //  no need to create tags when loading -> loading is ca 20-50% faster
        val tagBytes = Tag.createBest(it).toByteArray()
        os.write(tagBytes.size.toShortByteArray(shortBuf))
        os.write(tagBytes)

        os.write(min(it.domains?.size ?: 0, 255))
        it.domains?.let { map ->
            os.write(if (map.include) 1 else 0)
            for (i in 0 until min(map.size, 255)) {
                val key = map.getKey(i).toByteArray()
                os.write(key.size.toShortByteArray(shortBuf))
                os.write(key)
                os.write(if (map.getValue(i)) 1 else 0)
            }
        }

    }
}
