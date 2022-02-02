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

internal class ContainsHostFilter(
    filter: String,
    contentType: Int,
    domains: DomainMap?,
    thirdParty: Int,
    modify: ModifyFilter? = null
) : UnifiedFilter(filter, contentType, true, domains, thirdParty, modify) {
    override val filterType: Int
        get() = FILTER_TYPE_CONTAINS_HOST

    override fun check(url: Uri): Boolean {
        return url.host?.contains(pattern) ?: url.toString().contains(pattern)
    }
}
