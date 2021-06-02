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

package jp.hazuki.yuzubrowser.adblock.core

import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.Tag

class FilterContainer {
    private val filters = hashMapOf<String, ContentFilter>()

    operator fun plusAssign(filter: ContentFilter) {
        val key = when {
            filter.isRegex -> ""
            else -> Tag.createBest(filter.pattern)
        }
        this[key] = filter
    }

    // not having to create best tag accelerates loading by some 20-50%
    fun addWithTag(p: Pair<String, ContentFilter>) {
        this[p.first] = p.second
    }

    fun removeTag(tag: String) {
        filters.remove(tag)
    }

    private operator fun set(tag: String, filter: ContentFilter) {
        // added: ignore duplicate filters
        //  no noticeable slowdown
        var f = filters[tag]
        while (f != null) {
            if (f == filter)
                return
            f = f.next
        }

        filter.next = filters[tag]
        filters[tag] = filter
    }

    operator fun get(request: ContentRequest): ContentFilter? {
//        Tag.create(request.url.toString()).forEach {
        request.tags.forEach {
            var filter = filters[it]
            while (filter != null) {
                if (filter.isMatch(request)) {
                    return filter
                }
                filter = filter.next
            }
        }
        return null
    }
}
