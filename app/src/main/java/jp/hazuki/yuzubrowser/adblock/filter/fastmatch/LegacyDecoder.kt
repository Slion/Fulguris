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

package jp.hazuki.yuzubrowser.adblock.filter.fastmatch

import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import java.util.regex.Pattern

class LegacyDecoder {

    private val factory = FastMatcherFactory()

    fun singleDecode(line: String): UnifiedFilter? {
        if (line.length > 2) {
            if (line[0] == '[' && line[line.length - 1] == ']') {
                return createRegexFilter(line.substring(1, line.length - 1), 0xffff, false, null, -1)
            }
            val space = line.indexOf(' ')
            if (space > 0) {
                val ip = line.substring(0, space)
                if (IP_ADDRESS.matcher(ip).matches() && line.length > space + 1
                        || ip == "h" || ip == "host") {
                    return HostFilter(line.substring(space + 1), 0xffff, false, null, -1)
                } else if (ip == "c") {
                    return ContainsHostFilter(line.substring(space + 1), 0xffff, false, null, -1)
                }
            }
            val matcher = HOST.matcher(line)
            if (matcher.matches()) {
                val host = matcher.group()
                return createRegexFilter(factory.fastCompile(host), 0xffff, false, null, -1)
            }

            return ContainsFilter(line, 0xffff, null, -1)
        }
        return null
    }

    fun release() {
        factory.release()
    }

    companion object {
        private val IP_ADDRESS = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+$")
        private val HOST = Pattern.compile("^https?://([0-9a-z.\\-]+)/?$")
    }
}
