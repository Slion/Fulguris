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

import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import java.io.File

internal const val ABP_PREFIX_DENY = "b_"
internal const val ABP_PREFIX_ALLOW = "w_"
internal const val ABP_PREFIX_DISABLE_ELEMENT_PAGE = "wp_"
internal const val ABP_PREFIX_ELEMENT = "e_"

internal fun File.getAbpBlackListFile(entity: AbpEntity): File {
    return File(this, ABP_PREFIX_DENY + entity.entityId)
}

internal fun File.getAbpWhiteListFile(entity: AbpEntity): File {
    return File(this, ABP_PREFIX_ALLOW + entity.entityId)
}

internal fun File.getAbpWhitePageListFile(entity: AbpEntity): File {
    return File(this, ABP_PREFIX_DISABLE_ELEMENT_PAGE + entity.entityId)
}

internal fun File.getAbpElementListFile(entity: AbpEntity): File {
    return File(this, ABP_PREFIX_ELEMENT + entity.entityId)
}
