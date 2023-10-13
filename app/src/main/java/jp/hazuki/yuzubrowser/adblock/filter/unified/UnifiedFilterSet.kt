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

import fulguris.adblock.AbpBlockerManager.Companion.badfilterPrefixes
import fulguris.adblock.AbpBlockerManager.Companion.blockerPrefixes
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementFilter
import java.security.InvalidParameterException

class UnifiedFilterSet(
    val filterInfo: UnifiedFilterInfo,
    val elementDisableFilter: List<UnifiedFilter>,
    val elementList: List<ElementFilter>,
    val filters: FilterMap
)

// this is simply a Map<String, List<UnifiedFilter>> where i don't need to care about null
// does NOT contains element hide filters or allowlist
class FilterMap {
    private val map = (blockerPrefixes + badfilterPrefixes).associateWith { mutableListOf<UnifiedFilter>() }
    operator fun get(list: String) = map[list] ?: throw(InvalidParameterException("list $list does not exist"))
}
