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

class PlaneElementFilter(domain: String, isHide: Boolean, isNot: Boolean, selector: String) :
    ElementFilter(domain, isHide, isNot, selector) {

    override val type: Int
        get() = TYPE_PLANE

    override fun isMatch(domain: String, tldRemoved: String?): Boolean {
        return this.domain.isEmpty() || domain.endsWith(this.domain)
    }
}
