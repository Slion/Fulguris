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

package jp.hazuki.yuzubrowser.adblock.filter.mining

import android.webkit.WebResourceResponse
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.unified.ContainsFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.ContainsHostFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.HostFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.StartsWithFilter
import java.io.IOException
import java.io.InputStream

class MiningProtector {
    val dummy = WebResourceResponse("text/plain", "UTF-8", EmptyInputStream())

    private val blackList = arrayListOf(
        // host block list
        HostFilter("cnhv.co", 0xffff, false, null, 1),
        HostFilter("coinhive.com", 0xffff, false, null, 1),
        HostFilter("coin-hive.com", 0xffff, false, null, 1),
        HostFilter("gus.host", 0xffff, false, null, 1),
        ContainsHostFilter("jsecoin.com", 0xffff, false, null, 1),
        HostFilter("static.reasedoper.pw", 0xffff, false, null, 1),
        HostFilter("mataharirama.xyz", 0xffff, false, null, 1),
        HostFilter("listat.biz", 0xffff, false, null, 1),
        HostFilter("lmodr.biz", 0xffff, false, null, 1),
        ContainsHostFilter("crypto-loot.com", 0xffff, false, null, 1),
        ContainsHostFilter("2giga.link", 0xffff, false, null, 1),
        HostFilter("coinerra.com", 0xffff, false, null, 1),
        HostFilter("coin-have.com", 0xffff, false, null, 1),
        ContainsHostFilter("afminer.com", 0xffff, false, null, 1),
        ContainsHostFilter("coinblind.com", 0xffff, false, null, 1),
        ContainsFilter("monerominer.rocks", 0xffff, null, 1),
        ContainsHostFilter("cloudcoins.co", 0xffff, false, null, 1),
        HostFilter("coinlab.biz", 0xffff, false, null, 1),
        HostFilter("papoto.com", 0xffff, false, null, 1),
        HostFilter("rocks.io", 0xffff, false, null, 1),
        ContainsHostFilter("adminer.com", 0xffff, false, null, 1),
        ContainsHostFilter("ad-miner.com", 0xffff, false, null, 1),
        HostFilter("party-nngvitbizn.now.sh", 0xffff, false, null, 1),
        ContainsHostFilter("bitporno.com", 0xffff, false, null, 1),
        HostFilter("cryptoloot.pro", 0xffff, false, null, 1),
        HostFilter("load.jsecoin.com", 0xffff, false, null, 1),
        HostFilter("miner.pr0gramm.com", 0xffff, false, null, 1),
        HostFilter("minemytraffic.com", 0xffff, false, null, 1),
        HostFilter("ppoi.org", 0xffff, false, null, 1),
        HostFilter("projectpoi.com", 0xffff, false, null, 1),
        HostFilter("api.inwemo.com", 0xffff, false, null, 1),
        HostFilter("jsccnn.com", 0xffff, false, null, 1),
        HostFilter("jscdndel.com", 0xffff, false, null, 1),
        HostFilter("coinhiveproxy.com", 0xffff, false, null, 1),
        HostFilter("coinnebula.com", 0xffff, false, null, 1),
        HostFilter("cdn.cloudcoins.co", 0xffff, false, null, 1),
        HostFilter("go.megabanners.cf", 0xffff, false, null, 1),
        HostFilter(" cryptoloot.pro", 0xffff, false, null, 1),
        HostFilter("bjorksta.men", 0xffff, false, null, 1),
        HostFilter("crypto.csgocpu.com", 0xffff, false, null, 1),
        HostFilter("noblock.pro", 0xffff, false, null, 1),
        HostFilter("1q2w3.me", 0xffff, false, null, 1),
        HostFilter("minero.pw", 0xffff, false, null, 1),
        HostFilter("webmine.cz", 0xffff, false, null, 1),

        // url block list
        StartsWithFilter("kisshentai.net/Content/js/c-hive.js", 0xffff, true, null, 1),
        StartsWithFilter("kiwifarms.net/js/Jawsh/xmr/xmr.min.js", 0xffff, true, null, 1),
        StartsWithFilter("anime.reactor.cc/js/ch/cryptonight.wasm", 0xffff, true, null, 1),
        StartsWithFilter("cookiescript.info/libs/", 0xffff, true, null, 1),
        StartsWithFilter("cookiescriptcdn.pro/libs/", 0xffff, true, null, 1),
        StartsWithFilter("baiduccdn1.com/lib/", 0xffff, true, null, 1)
    )

    fun isBlock(contentRequest: ContentRequest): Boolean {
        if (contentRequest.url.host == contentRequest.pageUrl.host) return false

        return blackList.any { it.isMatch(contentRequest) }
    }

    private class EmptyInputStream : InputStream() {
        @Throws(IOException::class)
        override fun read(): Int = -1
    }
}
