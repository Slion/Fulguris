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

package jp.hazuki.yuzubrowser.adblock.filter.unified

import android.content.Context
import com.google.re2j.PatternSyntaxException
import jp.hazuki.yuzubrowser.adblock.filter.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal const val FILTER_TYPE_CONTAINS = 1
internal const val FILTER_TYPE_HOST = 2
internal const val FILTER_TYPE_CONTAINS_HOST = 3
internal const val FILTER_TYPE_START = 4
internal const val FILTER_TYPE_END = 5
internal const val FILTER_TYPE_START_END = 6
internal const val FILTER_TYPE_RE2_REGEX = 7
internal const val FILTER_TYPE_RE2_REGEX_HOST = 8
internal const val FILTER_TYPE_JVM_REGEX = 9
internal const val FILTER_TYPE_JVM_REGEX_HOST = 10
internal const val FILTER_TYPE_PATTERN = 11

internal const val FILTER_DIR = "adblock_filter"
internal const val FILTER_CACHE_HEADER = "YZBABPFI\u0000\u0001\u0001"
internal const val ELEMENT_FILTER_CACHE_HEADER = "YZBABPEF\u0000\u0001\u0001"

fun createRegexFilter(filter: String, contentType: Int, ignoreCase: Boolean, domains: DomainMap?, thirdParty: Int): UnifiedFilter? {
    try {
        Re2Filter(filter, contentType, ignoreCase, domains, thirdParty)
    } catch (e: PatternSyntaxException) {
        try {
            RegexFilter(filter, contentType, ignoreCase, domains, thirdParty)
        } catch (e: java.util.regex.PatternSyntaxException) {
        }
    }
    return null
}

internal fun Context.getFilterDir(): File {
    return getDir(FILTER_DIR, Context.MODE_PRIVATE)
}

fun writeFilter(file: File, list: List<UnifiedFilter>): Boolean {
    try {
        file.outputStream().use {
            val writer = FilterWriter()
            writer.write(it, list)
        }
        return true
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return false
}

internal fun OutputStream.writeVariableInt(num: Int, shortBuf: ByteArray, intBuf: ByteArray) {
    if (shortBuf.size != 2) throw LengthException()
    if (intBuf.size != 4) throw LengthException()

    if (num < 0xffff) {
        write(num.toShortByteArray(shortBuf))
    } else {
        write(0xff)
        write(0xff)
        write(num.toByteArray(intBuf))
    }
}

internal fun InputStream.readVariableInt(shortBuf: ByteArray, intBuf: ByteArray): Int {
    if (shortBuf.size != 2) throw LengthException()
    if (intBuf.size != 4) throw LengthException()

    if (read(shortBuf) != 2) return -1
    var result = shortBuf.toShortInt()
    if (result == 0xffff) {
        if (read(intBuf) != 4) return -1
        result = intBuf.toInt()
    }
    return result
}
