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

import android.net.Uri

class PatternMatchFilter(
    filter: String,
    contentType: Int,
    ignoreCase: Boolean,
    domains: DomainMap?,
    thirdParty: Int,
    modify: ModifyFilter? = null
) : UnifiedFilter(filter, contentType, ignoreCase, domains, thirdParty, modify) {
    override val filterType: Int
        get() = FILTER_TYPE_PATTERN

    // remove, not necessary after adjustment of Tag to us use tags containing '*'
    //override val isRegex = filter.contains('*')

    private val isStartWith = filter.startsWith("||")

    override fun check(url: Uri): Boolean {
        val c = check(url.toString())
        return c
    }

    fun check(url: String): Boolean {
        val max = url.length
        var pPtr = 0
        var i = 0
        val ignoreCase = ignoreCase
        var isFirst = false

        if (isStartWith) {
            isFirst = true
            pPtr = 1
            val start = url.indexOf(':')
            if (start < 0 || start + 1 >= max || url[start + 1] != '/') return false
            i = start + 2
            while (url[i] == '/') i++
        }

        loop@ while (i < max) {
            if (!isFirst) {
                i = when (pattern[pPtr]) {
                    '|' -> {
                        if (pPtr == 0 && i == 0) {
                            pPtr++
                            continue@loop
                        } else if (pPtr == pattern.lastIndex && i == max - 1) {
                            return true
                        }
                        val start = url.indexOf('|', i)
                        if (start < 0) return false
                        start
                    }
                    '*' -> {
                        if (pattern.length <= ++pPtr) return true
                        continue@loop
                    }
                    '^' -> {
                        val start = url.searchSeparator(i)
                        when {
                            start < 0 -> return false
                            url.length == start + 1 -> {
                                return pPtr == pattern.lastIndex
                            }
                            else -> start + 1
                        }
                    }
                    else -> {
                        val start = url.searchStart(pattern[pPtr], i, ignoreCase)
                        if (pPtr == pattern.length) return true
                        when {
                            start < 0 -> return false
                            url.length == start + 1 -> return pPtr == pattern.lastIndex
                            else -> start + 1
                        }
                    }
                }
            }

            var j = i
            val end = j + pattern.length - pPtr - 1
            for (k in pPtr + 1 until pattern.length) {
                val np = pattern[k]
                if (np == '|' && j == max && k == pattern.length - 1) return true
                if (j >= end || j >= max) {
                    i++
                    continue@loop
                }
                val nu = url[j]
                if (nu != np) {
                    when (np) {
                        '*' -> {
                            if (k + 1 == pattern.length) return true
                            isFirst = false
                            pPtr = k + 1
                            i = j
                            continue@loop
                        }
                        '^' -> if (!nu.checkSeparator()) continue@loop
                        else -> if (!ignoreCase || nu.toUpperCase() != np.toUpperCase()) {
                            if (isStartWith) {
                                val next = url.nextPoint(i)
                                if (next < 0) return false
                                i = next + 1
                            }
                            continue@loop
                        }
                    }
                }
                j++
            }
            if (j == end) return true
        }
        return false
    }

    private fun String.searchStart(c: Char, start: Int, ignoreCase: Boolean): Int {
        for (i in start until length) {
            val c2 = this[i]
            if (c == c2) return i
            if (ignoreCase && c.toUpperCase() == c2.toUpperCase()) return i
        }
        return -1
    }

    private fun String.searchSeparator(start: Int): Int {
        for (i in start until length) {
            val it = this[i].toInt()
            if (it in 0..0x24 || it in 0x26..0x2c || it == 0x2f || it in 0x3a..0x40 ||
                it in 0x5b..0x5e || it == 0x60 || it in 0x7b..0x7f) {
                return i
            }
        }
        return -1
    }

    private fun String.nextPoint(start: Int): Int {
        for (i in start until length) {
            when (this[i]) {
                '.' -> return i
                '/' -> return -1
            }
        }
        return -1
    }
}
