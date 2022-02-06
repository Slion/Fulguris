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

import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.toInt
import jp.hazuki.yuzubrowser.adblock.filter.toShortInt
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import java.io.InputStream

class FilterReader(private val input: InputStream) {
    private val intBuf = ByteArray(4)
    private val shortBuf = ByteArray(2)
    var stringBuffer = ByteArray(32)

    fun checkHeader(): Boolean {
        val header = FILTER_CACHE_HEADER.toByteArray()
        val data = ByteArray(header.size)
        input.read(data)
        return header contentEquals data
    }

    fun readAll() = sequence {
        input.read(intBuf)
        val size = intBuf.toInt()

        loop@ for (loop in 0 until size)
            readFilter()?.let { yield(it) } ?: break@loop
    }

    fun readAllModifyFilters() = sequence {
        input.read(intBuf)
        val size = intBuf.toInt()

        loop@ for (loop in 0 until size) {
            val modify = readModify() ?: break@loop
            val filter = readFilter(modify) ?: break@loop
            yield(Pair(filter.first, filter.second))
        }
    }

    private fun readModify(): ModifyFilter? {
        val modifySize = input.readVariableInt(shortBuf, intBuf)
        if (modifySize == -1) return null
        if (stringBuffer.size < modifySize) {
            stringBuffer = ByteArray(modifySize)
        }
        if (input.read(stringBuffer, 0, modifySize) != modifySize) return null
        val modifyString = String(stringBuffer, 0, modifySize)

        val modifyParameter =
            if (modifyString.length > 2) modifyString.substring(2)
            else null
        return when (modifyString[0]) {
            MODIFY_PREFIX_REMOVEPARAM -> RemoveparamFilter(modifyParameter, modifyString[1] == '1')
            MODIFY_PREFIX_REMOVEPARAM_REGEX -> RemoveparamRegexFilter(modifyParameter ?: return null, modifyString[1] == '1')
            MODIFY_PREFIX_REDIRECT -> RedirectFilter(modifyParameter)
            MODIFY_PREFIX_REQUEST_HEADER -> RequestHeaderFilter(modifyParameter ?: return null, modifyString[1] == '1')
            MODIFY_PREFIX_RESPONSE_HEADER -> ResponseHeaderFilter(modifyParameter ?: return null, modifyString[1] == '1')
            else -> null
        }
    }

    private fun readFilter(modify: ModifyFilter? = null): Pair<String, UnifiedFilter>? {
        val type = input.read()
        if (type < 0) return null

        if (input.read(shortBuf) != 2) return null
        val contentType = shortBuf.toShortInt()
        val thirdParty = when (input.read()) {
            0 -> 0
            1 -> 1
            2 -> 2
            3 -> 3
            0xff -> -1
            else -> return null
        }

        val patternSize = input.readVariableInt(shortBuf, intBuf)
        if (patternSize == -1) return null
        if (stringBuffer.size < patternSize) {
            stringBuffer = ByteArray(patternSize)
        }
        if (input.read(stringBuffer, 0, patternSize) != patternSize) return null
        val pattern = String(stringBuffer, 0, patternSize)

        // startEndFilter with domain as pattern, gets special treatment for accelerated read/write
        if (type == FILTER_TYPE_START_END_DOMAIN)
            return(Pair(pattern,StartEndFilter(pattern, contentType, false, null, thirdParty, modify)))

        val ignoreCase = when (input.read()) {
            0 -> false
            1 -> true
            else -> return null
        }

        val tagSize = input.readVariableInt(shortBuf, intBuf)
        if (tagSize == -1) return null
        if (stringBuffer.size < tagSize) {
            stringBuffer = ByteArray(tagSize)
        }
        if (input.read(stringBuffer, 0, tagSize) != tagSize) return null
        val tag = String(stringBuffer, 0, tagSize)

        val domainsSize = input.read()
        if (domainsSize == -1) return null

            val domains = when (domainsSize) {
                0 -> null
                1 -> {
                    val containerInclude = when (input.read()) {
                        0 -> false
                        1 -> true
                        else -> return null
                    }
                    val textSize = input.readVariableInt(shortBuf, intBuf)
                    if (textSize == -1) return null
                    if (stringBuffer.size < textSize) {
                        stringBuffer = ByteArray(textSize)
                    }
                    if (input.read(stringBuffer, 0, textSize) != textSize) return null
                    val domain = String(stringBuffer, 0, textSize)
                    val include = when (input.read()) {
                        0 -> false
                        1 -> true
                        else -> return null
                    }

                    if (containerInclude != include) return null

                    SingleDomainMap(include, domain)
                }
                else -> {
                    val map = ArrayDomainMap(domainsSize, false)
                    map.include = when (input.read()) {
                        0 -> false
                        1 -> true
                        else -> return null
                    }
                    for (i in 0 until domainsSize) {
                        val textSize = input.readVariableInt(shortBuf, intBuf)
                        if (textSize == -1) return null
                        if (stringBuffer.size < textSize) {
                            stringBuffer = ByteArray(textSize)
                        }
                        if (input.read(stringBuffer, 0, textSize) != textSize) return null
                        val domain = String(stringBuffer, 0, textSize)
                        if (domain.contains('*'))
                            map.wildcard = true
                        val include = when (input.read()) {
                            0 -> false
                            1 -> true
                            else -> return null
                        }
                        map[domain] = include
                    }
                    map
                }
            }

        val filter = when (type) {
            FILTER_TYPE_CONTAINS -> ContainsFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            FILTER_TYPE_HOST -> HostFilter(pattern, contentType, domains, thirdParty, modify)
            FILTER_TYPE_CONTAINS_HOST -> ContainsHostFilter(pattern, contentType, domains, thirdParty, modify)
            FILTER_TYPE_START -> StartsWithFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            FILTER_TYPE_END -> EndWithFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            FILTER_TYPE_START_END -> StartEndFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            FILTER_TYPE_JVM_REGEX -> RegexFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            FILTER_TYPE_JVM_REGEX_HOST -> RegexHostFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            FILTER_TYPE_PATTERN -> PatternMatchFilter(pattern, contentType, ignoreCase, domains, thirdParty, modify)
            else -> return null
        }
        return Pair(tag, filter)
    }
}
