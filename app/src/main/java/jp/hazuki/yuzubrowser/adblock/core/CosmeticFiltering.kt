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
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementContainer

class CosmeticFiltering(
    private val disables: FilterContainer,
    private val filters: ElementContainer,
) {
    private var cacheUrl: Uri? = null
    private var cache: String? = null

    fun loadScript(url: Uri): String? {
        if (cacheUrl == url) return cache

        val request = ContentRequest(url, url, TYPE, false)
        val result = disables[request]

        val isUseGeneric = when (result?.filterType ?: 0) {
            ContentRequest.TYPE_ELEMENT_HIDE -> return null
            ContentRequest.TYPE_ELEMENT_GENERIC_HIDE -> false
            else -> true
        }

        val results = filters[url, isUseGeneric]
        if (results.isEmpty()) return null

        val builder = StringBuilder()
        val plain = results.asSequence().filter { !it.isHide }.map { it.selector }.toSet()
        val selector = results.filter { it.isHide && !plain.contains(it.selector) }

        builder.append("(function(){const b=[")
        selector.forEach {
            builder.append('\'').append(it.selector).append("',")
        }
        builder.append("];b.forEach(a=>{document.querySelectorAll(a)?.forEach(n=>{n.remove()})})})()")

        val script = builder.toString()

        cacheUrl = url
        cache = script

        return script
    }

    companion object {
        private const val TYPE = ContentRequest.TYPE_ELEMENT_GENERIC_HIDE or
            ContentRequest.TYPE_ELEMENT_HIDE
    }
}
