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

package jp.hazuki.yuzubrowser.adblock.filter

import java.io.IOException

internal fun StringBuilder.escapeRegexSymbols(): StringBuilder {
    val newBuilder = StringBuilder()
    var start = 0
    forEachIndexed { index, c ->
        when (c) {
            '\\', '+', '.', '?', '{', '}', '(', ')', '[', ']', '$', '-', '|' -> {
                newBuilder.append(this, start, index).append('\\').append(c)
                start = index + 1
            }
        }
    }
    newBuilder.append(this, start, length)
    return newBuilder
}

internal fun Long.toByteArray(): ByteArray {
    val bytes = ByteArray(8)
    bytes[0] = and(0xff).toByte()
    bytes[1] = ushr(0x08).and(0xff).toByte()
    bytes[2] = ushr(0x10).and(0xff).toByte()
    bytes[3] = ushr(0x18).and(0xff).toByte()
    bytes[4] = ushr(0x20).and(0xff).toByte()
    bytes[5] = ushr(0x28).and(0xff).toByte()
    bytes[6] = ushr(0x30).and(0xff).toByte()
    bytes[7] = ushr(0x38).and(0xff).toByte()
    return bytes
}

internal fun Long.toByteArray(bytes: ByteArray): ByteArray {
    if (bytes.size != 8) throw LengthException()

    bytes[0] = and(0xff).toByte()
    bytes[1] = ushr(0x08).and(0xff).toByte()
    bytes[2] = ushr(0x10).and(0xff).toByte()
    bytes[3] = ushr(0x18).and(0xff).toByte()
    bytes[4] = ushr(0x20).and(0xff).toByte()
    bytes[5] = ushr(0x28).and(0xff).toByte()
    bytes[6] = ushr(0x30).and(0xff).toByte()
    bytes[7] = ushr(0x38).and(0xff).toByte()
    return bytes
}

internal fun ByteArray.toLong(): Long {
    if (size != 8) throw LengthException()

    return this[0].toLong().and(0xff) or
            this[1].toLong().and(0xff).shl(0x08) or
            this[2].toLong().and(0xff).shl(0x10) or
            this[3].toLong().and(0xff).shl(0x18) or
            this[4].toLong().and(0xff).shl(0x20) or
            this[5].toLong().and(0xff).shl(0x28) or
            this[6].toLong().and(0xff).shl(0x30) or
            this[7].toLong().and(0xff).shl(0x38)
}

internal fun Int.toByteArray(): ByteArray {
    val bytes = ByteArray(4)
    bytes[0] = and(0xff).toByte()
    bytes[1] = ushr(0x08).and(0xff).toByte()
    bytes[2] = ushr(0x10).and(0xff).toByte()
    bytes[3] = ushr(0x18).and(0xff).toByte()
    return bytes
}

internal fun Int.toByteArray(bytes: ByteArray): ByteArray {
    if (bytes.size != 4) throw LengthException("bytes size is ${bytes.size}")

    bytes[0] = and(0xff).toByte()
    bytes[1] = ushr(0x08).and(0xff).toByte()
    bytes[2] = ushr(0x10).and(0xff).toByte()
    bytes[3] = ushr(0x18).and(0xff).toByte()
    return bytes
}

internal fun ByteArray.toInt(): Int {
    if (size != 4) throw LengthException()

    return this[0].toInt().and(0xff) or
            this[1].toInt().and(0xff).shl(0x08) or
            this[2].toInt().and(0xff).shl(0x10) or
            this[3].toInt().and(0xff).shl(0x18)
}

internal fun Int.toShortByteArray(): ByteArray {
    val bytes = ByteArray(2)
    bytes[0] = and(0xff).toByte()
    bytes[1] = ushr(0x08).and(0xff).toByte()
    return bytes
}

internal fun Int.toShortByteArray(bytes: ByteArray): ByteArray {
    if (bytes.size != 2) throw LengthException()

    bytes[0] = and(0xff).toByte()
    bytes[1] = ushr(0x08).and(0xff).toByte()
    return bytes
}

internal fun ByteArray.toShortInt(): Int {
    if (size != 2) throw LengthException()

    return (this[0].toInt().and(0xff) or
            this[1].toInt().and(0xff).shl(0x08))
}

internal class LengthException(message: String? = null) : IOException(message)