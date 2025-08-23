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

package jp.hazuki.yuzubrowser.adblock.filter.unified.element

import android.net.Uri
import jp.hazuki.yuzubrowser.adblock.filter.unified.Tag
import okhttp3.internal.publicsuffix.PublicSuffix
import java.util.*

class ElementContainer {
    private val filters = hashMapOf<String, ElementFilter>()

    operator fun plusAssign(filter: ElementFilter) {
        val key = Tag.createBest(filter.domain)
        this[key] = filter
    }

    private operator fun set(tag: String, filter: ElementFilter) {
        filter.next = filters[tag]
        filters[tag] = filter
    }

    operator fun get(url: Uri, isUseGeneric: Boolean): List<ElementFilter> {
        val host = (url.host ?: return emptyList()).lowercase(Locale.ROOT)
        val tldRemoved = host.removeEffectiveTld()

        val list = mutableListOf<ElementFilter>()
        Tag.create(host).forEach {
            var filter = filters[it]
            while (filter != null) {
                if (isUseGeneric || filter.domain != "") {
                    if (filter.isNot) {
                        if (!filter.isMatch(host, tldRemoved)) {
                            list += filter
                        }
                    } else {
                        if (filter.isMatch(host, tldRemoved)) {
                            list += filter
                        }
                    }

                }
                filter = filter.next
            }
        }
        return list
    }

    companion object {
        fun String.removeEffectiveTld(): String? {
            val plusOne = PublicSuffix.get().getEffectiveTldPlusOne(this) ?: return null

            val index = plusOne.length - plusOne.indexOf('.')

            return substring(0, length - index + 1)
        }
    }
}
