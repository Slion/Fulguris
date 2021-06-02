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

import android.net.Uri
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter

data class Blocker(
//    internal val allowPages: FilterContainer,
    private val exclusionList: FilterContainer,
    private val blockList: FilterContainer
) {

    fun isBlock(request: ContentRequest): ContentFilter? {
//        val pageRequest = request.copy(url = request.pageUrl)

//        var filter = allowPages[pageRequest]
//        if (filter != null) return null

//        filter = allows[request]
        if (exclusionList[request] != null) return null

        return blockList[request]
    }

//    fun isWhitePage(url: Uri): Boolean {
//        val request = ContentRequest(url, url, ContentRequest.TYPE_DOCUMENT, false)
//        return allowPages[request] != null
//    }
}
