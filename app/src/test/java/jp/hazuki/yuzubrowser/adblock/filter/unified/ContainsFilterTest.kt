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

package jp.hazuki.yuzubrowser.adblock.filter.unified

import android.net.Uri
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.mock

class ContainsFilterTest {

    @Test
    fun check() {
        val uri = mock(Uri::class.java)
        whenever(uri.toString()).thenReturn("http://blog.livedoor.jp/dqnplus/settings/lite2/ads.js")

        assertThat(ContainsFilter("/lite2/ads.js", ContentRequest.TYPE_ALL, true, null, -1).check(uri)).isEqualTo(true)
    }
}
