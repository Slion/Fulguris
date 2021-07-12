/*
 * Copyright 2020 Hazuki
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

import java.util.*

object Tag {
    fun create(url: String): List<String> {
        return url.toLowerCase(Locale.ENGLISH).getTagCandidates().also {
            it += ""
        }
    }

    fun createBest(pattern: String): String {
        var maxLength = 0
        var tag = ""

        val candidates = pattern.toLowerCase(Locale.ENGLISH).getTagCandidates()
        for (i in 0 until candidates.size) {
            val candidate = candidates[i]
            if (candidate.length > maxLength) {
                maxLength = candidate.length
                tag = candidate
            }
        }

        return tag
    }

    private fun String.getTagCandidates(): MutableList<String> {
        var start = 0
        val list = mutableListOf<String>()
        for (i in 0 until length) {
            when (get(i)) {
                // changed: allow tags to contain wildcard, but remove those tags (see isPrevent)
                //  purpose: tags are created for patterns containing wildcards (change in PatternMatchFilter)
                //  so no need to check against all patterns containing wildcards
                //  but we can't use something like google*ads as tag, and using google or ads is bad because the tag is not complete
                //   e.g. if url has google14/23ads, none of the tags google, ads, google*ads will match
//                in 'a'..'z', in '0'..'9', '%' -> continue
                in 'a'..'z', in '0'..'9', '%', '*' -> continue
                else -> {
                    if (i != start && i - start >= 3) {
                        val tag = substring(start, i)
                        if (!isPrevent(tag)) {
                            list += tag
                        }
                    }
                    start = i + 1
                }
            }
        }
        if (length != start && length - start >= 3) {
            val tag = substring(start, length)
            if (!isPrevent(tag)) {
                list += tag
            }
        }
        return list
    }

    private fun isPrevent(tag: String): Boolean {
        // added: remove if contains wildcard
        if (tag.contains('*'))
            return true
        return when (tag) {
            "http", "https", "html", "jpg", "png" -> true
            else -> false
        }
    }
}
