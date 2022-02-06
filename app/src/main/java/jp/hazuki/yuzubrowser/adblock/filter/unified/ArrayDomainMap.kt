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

import androidx.collection.SimpleArrayMap

class ArrayDomainMap(size: Int, override var wildcard: Boolean) : SimpleArrayMap<String, Boolean>(size), DomainMap {

    override val size: Int
        get() = size()

    // see https://adblockplus.org/en/filter-cheatsheet: also matches subdomains
    override fun get(domain: String): Boolean? {
        if (wildcard) {
            // check all entries separately, return null if none matches
            for (i in 0 until size)
                if (matchWildcard(keyAt(i), domain)) return valueAt(i)
            return null
        }
        var d = domain
        while (d.contains('.')) {
            getOrDefault(d, null)?.let { return it }
            d = d.substringAfter('.')
        }
        return getOrDefault(domain, null)
    }

    operator fun set(domain: String, value: Boolean) {
        put(domain, value)
    }

    override var include: Boolean = false


    override fun getKey(index: Int): String {
        return keyAt(index)
    }

    override fun getValue(index: Int): Boolean {
        return valueAt(index)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + wildcard.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArrayDomainMap
        if (size != other.size) return false
        if (wildcard != other.wildcard) return false
        return super.equals(other as? SimpleArrayMap<String, Boolean>)
    }
}
