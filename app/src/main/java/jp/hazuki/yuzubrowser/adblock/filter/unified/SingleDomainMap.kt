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

class SingleDomainMap(override val include: Boolean, private val domain: String) : DomainMap {
    override val size: Int
        get() = 1

    override val wildcard = domain.contains('*')

    // see https://adblockplus.org/en/filter-cheatsheet: also matches subdomains
    override fun get(domain: String): Boolean? {
        // should match any tld (but only tld)
        //   so example.* should match example.com and example.co.uk, but not example.example2.com
        if (wildcard) {
            return if (matchWildcard(this.domain, domain)) include else null
        }

        return if (this.domain == domain || domain.endsWith(".${this.domain}")) include else null
    }

    override fun getKey(index: Int): String {
        if (index != 0) throw IndexOutOfBoundsException()
        return domain
    }

    override fun getValue(index: Int): Boolean {
        if (index != 0) throw IndexOutOfBoundsException()
        return include
    }

    override fun hashCode(): Int {
        var result = domain.hashCode()
        result = 31 * result + include.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SingleDomainMap
        if (domain != other.domain) return false
        return include == other.include
    }
}
