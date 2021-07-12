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

class StartEndFilter(
    filter: String,
    contentType: Int,
    ignoreCase: Boolean,
    domains: DomainMap?,
    thirdParty: Int
) : UnifiedFilter(filter, contentType, ignoreCase, domains, thirdParty) {
    override val filterType: Int
        get() = FILTER_TYPE_START_END

    override fun check(url: Uri): Boolean {
        val urlStr = url.schemeSpecificPart
        val startIndex = urlStr.indexOf(pattern, ignoreCase = ignoreCase)
        if (startIndex > -1 && urlStr.checkIsDomainInSsp(startIndex)) {
            return if (pattern.length + startIndex == urlStr.length) {
                true
            } else {
                urlStr[pattern.length + startIndex].checkSeparator()
            }
        }
        return false
    }
}
