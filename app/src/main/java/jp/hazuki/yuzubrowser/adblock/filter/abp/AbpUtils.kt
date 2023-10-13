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

package jp.hazuki.yuzubrowser.adblock.filter.abp

import fulguris.adblock.AbpBlockerManager.Companion.blockerPrefixes
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import java.io.File
import java.security.InvalidParameterException

// prefixes for files
internal const val ABP_PREFIX_DENY = "b_"
internal const val ABP_PREFIX_ALLOW = "w_"
internal const val ABP_PREFIX_DISABLE_ELEMENT_PAGE = "wp_"
internal const val ABP_PREFIX_ELEMENT = "e_"
internal const val ABP_PREFIX_MODIFY_EXCEPTION = "me_"
internal const val ABP_PREFIX_MODIFY = "m_"
internal const val ABP_PREFIX_IMPORTANT = "i_"
internal const val ABP_PREFIX_IMPORTANT_ALLOW = "ia_"
internal const val ABP_PREFIX_REDIRECT = "r_"
internal const val ABP_PREFIX_REDIRECT_EXCEPTION = "re_"
// badfilter is an additional prefix for files
internal const val ABP_PREFIX_BADFILTER = "bf_"

// prefixes for modify filters used inside the modify files
internal const val MODIFY_PREFIX_REDIRECT = 'r'
internal const val MODIFY_PREFIX_REMOVEPARAM = 'p'
internal const val MODIFY_PREFIX_REMOVEPARAM_REGEX = 'x'
internal const val MODIFY_PREFIX_REQUEST_HEADER = 'q'
internal const val MODIFY_PREFIX_RESPONSE_HEADER = 'a'

internal fun File.getFilterFile(prefix: String, entity: AbpEntity): File {
    if (prefix !in (blockerPrefixes + ABP_PREFIX_ELEMENT + ABP_PREFIX_DISABLE_ELEMENT_PAGE))
        throw(InvalidParameterException("prefix $prefix is invalid"))
    return File(this, prefix + entity.entityId)
}
