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

package jp.hazuki.yuzubrowser.adblock.filter.unified.io

import jp.hazuki.yuzubrowser.adblock.filter.toInt
import jp.hazuki.yuzubrowser.adblock.filter.unified.ELEMENT_FILTER_CACHE_HEADER
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.PlaneElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.TldRemovedElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.readVariableInt
import java.io.InputStream

class ElementReader(private val input: InputStream) {

    fun checkHeader(): Boolean {
        val header = ELEMENT_FILTER_CACHE_HEADER.toByteArray()
        val data = ByteArray(header.size)
        input.read(data)
        return header contentEquals data
    }

    fun readAll() = sequence {
        val intBuf = ByteArray(4)
        val shortBuf = ByteArray(2)

        input.read(intBuf)

        val size = intBuf.toInt()
        val list = ArrayList<ElementFilter>(size)
        var patternBuffer = ByteArray(32)

        loop@ for (loop in 0 until size) {
            val filterType = input.read()
            if (filterType < 0) break

            val isHide = when (input.read()) {
                0 -> false
                1 -> true
                else -> break@loop
            }

            val isNot = when (input.read()) {
                0 -> false
                1 -> true
                else -> break@loop
            }

            val patternSize = input.readVariableInt(shortBuf, intBuf)
            if (patternSize == -1) break
            if (patternBuffer.size < patternSize) {
                patternBuffer = ByteArray(patternSize)
            }
            if (input.read(patternBuffer, 0, patternSize) != patternSize) break
            val pattern = String(patternBuffer, 0, patternSize)

            val selectorSize = input.readVariableInt(shortBuf, intBuf)
            if (selectorSize == -1) break
            if (patternBuffer.size < selectorSize) {
                patternBuffer = ByteArray(selectorSize)
            }
            if (input.read(patternBuffer, 0, selectorSize) != selectorSize) break
            val selector = String(patternBuffer, 0, selectorSize)

            val filter = when (filterType) {
                ElementFilter.TYPE_PLANE -> PlaneElementFilter(
                    pattern,
                    isHide,
                    isNot,
                    selector,
                )
                ElementFilter.TYPE_TLD_REMOVED -> TldRemovedElementFilter(
                    pattern,
                    isHide,
                    isNot,
                    selector,
                )
                else -> break@loop
            }
            yield(filter)
        }
    }
}
