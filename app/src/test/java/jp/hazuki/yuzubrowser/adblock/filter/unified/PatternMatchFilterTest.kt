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

import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PatternMatchFilterTest {

    @Test
    fun testCheck() {
        /** wildcards test */
        val wildcards = PatternMatchFilter("example.com/ads/banner*.gif", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(wildcards.check("http://example.com/ads/banner123.gif")).isEqualTo(true)

        val lastWildcard = PatternMatchFilter("test.*", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(lastWildcard.check("test.com")).isEqualTo(true)

        val firstWildcard = PatternMatchFilter("*.test.com", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(firstWildcard.check("browser.test.com")).isEqualTo(true)

        val wildcard = PatternMatchFilter("*.test.*", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(wildcard.check("browser.test.com")).isEqualTo(true)
        assertThat(wildcard.check("test.com")).isEqualTo(false)

        /** Matching at beginning test */
        val start = PatternMatchFilter("|http://baddomain.example/", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(start.check("http://baddomain.example/banner.gif")).isEqualTo(true)
        assertThat(start.check("http://gooddomain.example/analyze?http://baddomain.example")).isEqualTo(false)

        /** Matching at end test */
        val endWith = PatternMatchFilter("swf|", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(endWith.check("http://example.com/annoyingflash.swf")).isEqualTo(true)
        assertThat(endWith.check("http://example.com/swf/index.html")).isEqualTo(false)

        /** beginning of the domain name test */
        val domainName = PatternMatchFilter("||example.com/banner.gif", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(domainName.check("http://example.com/banner.gif")).isEqualTo(true)
        assertThat(domainName.check("https://example.com/banner.gif")).isEqualTo(true)
        assertThat(domainName.check("http://www.example.com/banner.gif")).isEqualTo(true)
        assertThat(domainName.check("http://badexample.com/banner.gif")).isEqualTo(false)
        assertThat(domainName.check(" http://gooddomain.example/analyze?http://example.com/banner.gif")).isEqualTo(false)

        val domainWildcard = PatternMatchFilter("||example.com/*/banner.gif", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(domainWildcard.check("http://example.com/test/banner.gif")).isEqualTo(true)
        assertThat(domainWildcard.check("http://example.com/banner.gif")).isEqualTo(false)
        assertThat(domainWildcard.check("http://www.example.com/test/banner.gif")).isEqualTo(true)

        /** Marking separator characters test */
        val separator = PatternMatchFilter("http://example.com^", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(separator.check("http://example.com/")).isEqualTo(true)
        assertThat(separator.check("http://example.com:8000/")).isEqualTo(true)
        assertThat(separator.check("http://example.com.ar/")).isEqualTo(false)

        val firstSeparator = PatternMatchFilter("^adsite", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(firstSeparator.check("browser.test.com/adsite")).isEqualTo(true)
        assertThat(firstSeparator.check("browser.test.com/ads")).isEqualTo(false)

        val secondSeparator = PatternMatchFilter("p^adsite", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(secondSeparator.check("browser.test.jp.adsite")).isEqualTo(false)
        assertThat(secondSeparator.check("browser.test.jp/adsite")).isEqualTo(true)

        val endSeparator = PatternMatchFilter("adsite^", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(endSeparator.check("browser.test.com/adsite/ad")).isEqualTo(true)
        assertThat(endSeparator.check("ads.test.com/adsite.ad/")).isEqualTo(false)

        val midSeparator = PatternMatchFilter("adsite^ad", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(midSeparator.check("browser.test.com/adsite/ad")).isEqualTo(true)

        val fieldTest1 = PatternMatchFilter("/www/delivery/*", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(fieldTest1.check("https://www.amazon.co.jp/")).isEqualTo(false)

        val fT2 = PatternMatchFilter("||ad-stir.com^", 0, false, null, -1)
        assertThat(fT2.check("http://js.ad-stir.com/js/nativeapi.js")).isEqualTo(true)

        /** real pattern test */
        val realJandan = PatternMatchFilter("||jandan.net^*/moyu.png", ContentRequest.TYPE_ALL, false, null, -1)
        assertThat(realJandan.check("https://i.jandan.net/")).isEqualTo(false)
    }
}
