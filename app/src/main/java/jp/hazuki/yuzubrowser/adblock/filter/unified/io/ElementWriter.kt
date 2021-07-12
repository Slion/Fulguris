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

import jp.hazuki.yuzubrowser.adblock.filter.toByteArray
import jp.hazuki.yuzubrowser.adblock.filter.unified.ELEMENT_FILTER_CACHE_HEADER
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.ElementFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.writeVariableInt
import java.io.OutputStream

class ElementWriter {

    fun write(os: OutputStream, filters: List<ElementFilter>) {
        writeHeader(os)
        writeAll(os, filters)
    }

    private fun writeHeader(os: OutputStream) {
        os.write(ELEMENT_FILTER_CACHE_HEADER.toByteArray())
    }

    private fun writeAll(os: OutputStream, filters: List<ElementFilter>) {
        val intBuf = ByteArray(4)
        val shortBuf = ByteArray(2)

        os.write(filters.size.toByteArray(intBuf))

        filters.forEach {
            os.write(it.type and 0xff)
            os.write(if (it.isHide) 1 else 0)
            os.write(if (it.isNot) 1 else 0)
            val patternBytes = it.domain.toByteArray()
            os.writeVariableInt(patternBytes.size, shortBuf, intBuf)
            os.write(patternBytes)
            val selectorBytes = it.selector.toByteArray()
            os.writeVariableInt(selectorBytes.size, shortBuf, intBuf)
            os.write(selectorBytes)
        }
    }
}
