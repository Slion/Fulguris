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
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter

class FilterContainer {
    private val filters = hashMapOf<String, ContentFilter>()
    // set to false if there is at least one pattern that is not a domain
    //  of true, checking is much faster
    private var domainOnly = true

    // not having to create best tag accelerates loading lists and filling FilterContainer by 20-50%
    fun addWithTag(p: Pair<String, ContentFilter>) {
        // normally, created tags will only consist of %, numbers and letters
        //  only tag specifically created for patterns that are a domain will contain '.'
        if (domainOnly && !p.first.contains('.'))
            domainOnly = false
        this[p.first] = p.second
    }

    // check whether any filters with the domain as pattern match
    //  having this separate gives no clear advantage or disadvantage when using easylist
    //  but for domain-only lists it helps a lot
    //  plus, it allows for faster loading from files
    private fun getDomain(request: ContentRequest): ContentFilter? {
        var domain = request.url.host ?: return null
        var filter: ContentFilter?
        while (domain.contains('.')) {
            filter = filters[domain]
            while (filter != null) {
                if (filter.isMatch(request))
                    return filter
                filter = filter.next
            }
            domain = domain.substringAfter('.')
        }
        return null
    }

    private operator fun set(tag: String, filter: ContentFilter) {
        filter.next = filters[tag]
        filters[tag] = filter
    }

    operator fun get(request: ContentRequest): ContentFilter? {
        getDomain(request)?.let { return it }
        // no need for further checks if list only contains domain filters
        if (domainOnly) return null

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

    fun getAll(request: ContentRequest): MutableList<ContentFilter> {
        val list = mutableListOf<ContentFilter>()
        list += getAllDomain(request)
        // no need for further checks if list only contains domain filters
        if (domainOnly) return list

        request.tags.forEach {
            var filter = filters[it]
            while (filter != null) {
                if (filter.isMatch(request)) {
                    list.add(filter)
                }
                filter = filter.next
            }
        }
        return list
    }

    private fun getAllDomain(request: ContentRequest): List<ContentFilter> {
        var domain = request.url.host ?: return listOf()
        val list = mutableListOf<ContentFilter>()
        var filter: ContentFilter?
        while (domain.contains('.')) {
            filter = filters[domain]
            while (filter != null) {
                if (filter.isMatch(request))
                    list.add(filter)
                filter = filter.next
            }
            domain = domain.substringAfter('.')
        }
        return list
    }

    fun clear() {
        filters.clear()
    }

    // only used in tests
    operator fun plusAssign(filter: ContentFilter) {
        val key = Tag.createBest(filter as UnifiedFilter)
        addWithTag(Pair(key, filter))
    }

    // only used in tests
    fun filters(): Map<String, ContentFilter> {
        return filters
    }
}
