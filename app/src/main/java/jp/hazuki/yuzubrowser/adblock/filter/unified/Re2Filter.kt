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
import com.google.re2j.Pattern

class Re2Filter(
    filter: String,
    contentType: Int,
    ignoreCase: Boolean,
    domains: DomainMap?,
    thirdParty: Int,
) : UnifiedFilter(filter, contentType, ignoreCase, domains, thirdParty) {
    private val regex = Pattern.compile(pattern, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)

    override val filterType: Int
        get() = FILTER_TYPE_RE2_REGEX

    override fun check(url: Uri): Boolean {
        return regex.matches(url.toString())
    }

    override val isRegex: Boolean
        get() = true
}
