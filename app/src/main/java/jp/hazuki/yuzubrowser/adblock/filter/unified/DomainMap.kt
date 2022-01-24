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

import okhttp3.internal.publicsuffix.PublicSuffix

interface DomainMap {
    val size: Int

    val include: Boolean

    val wildcard: Boolean

    operator fun get(domain: String): Boolean?

    fun getKey(index: Int): String

    fun getValue(index: Int): Boolean

    fun matchWildcard(filterDomain: String, domain: String): Boolean {
        // for arrayDomainMaps some domains may have wildcard, but then not all need to have
        if (!filterDomain.contains('*'))
            return (filterDomain == domain || domain.endsWith(filterDomain))

        // return false if no chance to match, to avoid slow public suffix list wherever possible
        val filterBeforeWildcard = filterDomain.substringBefore(".*")
        if (!domain.startsWith(filterBeforeWildcard) && !domain.contains(".$filterBeforeWildcard"))
            return false

        // domain without suffix matches -> check if '.*' part is a TLD
        // use PublicSuffix.getEffectiveTldPlusOne to check
        val publicSuffix = PublicSuffix.get()
        val fakeDomain = "example" + domain.substringAfterLast(filterBeforeWildcard)
        return publicSuffix.getEffectiveTldPlusOne(fakeDomain) == fakeDomain
    }
}
