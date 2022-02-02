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

    fun writeWithTag(os: OutputStream, filters: List<Pair<String, UnifiedFilter>>) {
        writeHeader(os)
        writeAll(os, filters)
        writeHeader(os)
    }

    fun write(os: OutputStream, filters: List<UnifiedFilter>) {
        writeWithTag(os, filters.map { Pair(Tag.createBest(it), it) })
    }

    fun writeModifyFiltersWithTag(os: OutputStream, filters: List<Pair<String, UnifiedFilter>>) {
        writeHeader(os)
        writeAllModifyFilters(os, filters)
        writeHeader(os)
    }

    fun writeModifyFilters(os: OutputStream, filters: List<UnifiedFilter>) {
        writeModifyFiltersWithTag(os, filters.map { Pair(Tag.createBest(it), it) })
    }

    private fun writeHeader(os: OutputStream) {
        os.write(FILTER_CACHE_HEADER.toByteArray())
    }

    private fun writeAll(os: OutputStream, filters: List<Pair<String, UnifiedFilter>>) {
        os.write(filters.size.toByteArray(intBuf))

        filters.forEach {
            writeFilter(os, it)
        }
    }

    // would be easier to write all in the same file
    //  but would break compatibility with previously written lists, and that little simplification is not worth it
    private fun writeAllModifyFilters(os: OutputStream, filters: List<Pair<String, UnifiedFilter>>) {
        os.write(filters.size.toByteArray(intBuf))

        filters.forEach {
            val modify = it.second.modify ?: return@forEach // log if null?
            val modifyString = "" + modify.prefix + (if (modify.inverse) 1 else 0) + (modify.parameter ?: "")
            writeModify(os, modifyString)
            writeFilter(os, it)
        }
    }

    private fun writeModify(os: OutputStream, param: String) {
        val modifyBytes = param.toByteArray()
        os.write(modifyBytes.size.toShortByteArray(shortBuf))
        os.write(modifyBytes)
    }

    private fun writeFilter(os: OutputStream, pair: Pair<String, UnifiedFilter>) {
        val filter = pair.second
        val tagBytes = pair.first.toByteArray()
        // write simplified for simple domain filters
        //  increases write and read speeds
        // tag should be equal to pattern, otherwise it can't contain '.'
        if (filter.filterType == FILTER_TYPE_START_END && pair.first.contains('.')
            && filter.domains == null && !filter.ignoreCase && filter.modify == null)
            {
            os.write(FILTER_TYPE_START_END_DOMAIN and 0xff)
            os.write(filter.contentType.toShortByteArray(shortBuf))
            os.write(filter.thirdParty and 0xff)
            os.write(tagBytes.size.toShortByteArray(shortBuf))
            os.write(tagBytes)
            return
        }

        os.write(filter.filterType and 0xff)
        os.write(filter.contentType.toShortByteArray(shortBuf))
        os.write(filter.thirdParty and 0xff)

        val patternBytes = filter.pattern.toByteArray()
        os.writeVariableInt(patternBytes.size, shortBuf, intBuf)
        os.write(patternBytes)

        os.write(if (filter.ignoreCase) 1 else 0)

        // write tag, so it's not created again when loading -> 20-50% faster loading
        os.write(tagBytes.size.toShortByteArray(shortBuf))
        os.write(tagBytes)

        os.write(min(filter.domains?.size ?: 0, 255))
        filter.domains?.let { map ->
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
