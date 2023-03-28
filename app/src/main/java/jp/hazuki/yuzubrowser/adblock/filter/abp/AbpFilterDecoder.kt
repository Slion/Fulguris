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

package jp.hazuki.yuzubrowser.adblock.filter.abp

import acr.browser.lightning.adblock.RES_EMPTY
import acr.browser.lightning.adblock.RES_NOOP_MP4
import androidx.core.net.toUri
import jp.hazuki.yuzubrowser.adblock.*
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.ModifyFilter.Companion.REMOVEHEADER_NOT_ALLOWED
import jp.hazuki.yuzubrowser.adblock.filter.unified.ModifyFilter.Companion.RESPONSEHEADER_ALLOWED
import jp.hazuki.yuzubrowser.adblock.filter.unified.element.*
import java.io.BufferedReader
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern

class AbpFilterDecoder {
    private val elementFilterRegex = Pattern.compile(ELEMENT_FILTER_REGEX)

    fun checkHeader(reader: BufferedReader, charset: Charset): Boolean {
        reader.mark(1024)
        when (charset) {
            Charsets.UTF_8, Charsets.UTF_16, Charsets.UTF_16LE, Charsets.UTF_16BE -> {
                if (reader.read() == 0xfeff) { // Skip BOM
                    reader.mark(1024)
                } else {
                    reader.reset()
                }
            }
        }
        val header = reader.readLine() ?: return false
        if (header.isNotEmpty()) {
            return if (header[0] == '!') {
                reader.reset()
                true
            } else {
                header.startsWith(HEADER)
            }
        }
        return false
    }

    // taken from jp.hazuki.yuzubrowser.core.utility.extensions.forEachLine
    inline fun BufferedReader.forEachLine(block: (String) -> Unit) {
        while (true) {
            block(readLine() ?: return)
        }
    }

    fun decode(reader: BufferedReader, url: String?): UnifiedFilterSet {
        val info = DecoderInfo()
        val elementDisableFilter = mutableListOf<UnifiedFilter>()
        val elementFilter = mutableListOf<ElementFilter>()
        val filterLists = FilterMap()
        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val trimmedLine = line.trim()
            when {
                trimmedLine[0] == '!' -> trimmedLine.decodeComment(url, info)
                else -> {
                    val matcher = elementFilterRegex.matcher(trimmedLine)
                    if (matcher.matches() && !trimmedLine.contains("##^responseheader")) { // exception necessary to process responseheader correctly
                        return@forEachLine
                        // currently the element filters are not used, so there is no reason to decode them
/*                        decodeElementFilter(
                            matcher.group(1),
                            matcher.group(2),
                            matcher.group(3),
                            elementFilter,
                        )
*/
                    } else {
                        trimmedLine.decodeFilter(elementDisableFilter, filterLists)
                        // log if no new filters were added
                    }
                }
            }
        }
        return UnifiedFilterSet(info, elementDisableFilter, elementFilter, filterLists)
    }

    private fun decodeElementFilter(
        domains: String?,
        type: String?,
        body: String?,
        elementFilterList: MutableList<ElementFilter>,
    ) {
        if (body?.startsWith("+js") != false) return

        if (domains == null && type == "@") return

        var domainList = domains?.run {
            splitToSequence(',')
                .map { it.trim() }
                .toList()
        } ?: emptyList()

        if (domainList.size >= 2) {
            domainList.forEach {
                if (it.startsWith('~')) return
            }
        }

        if (domainList.isEmpty()) {
            domainList = listOf("")
        } else {
            var starIndex = domainList.indexOf("*")
            while (starIndex != -1) {
                domainList = domainList.subList(0, starIndex) + "" +
                    domainList.subList(starIndex, domainList.size)

                starIndex = domainList.indexOf("*")
            }
        }

        val isHide = when (type) {
            "@" -> false
            null -> true
            else -> return
        }

        domainList.forEach {
            var domain = it
            val isNot = domain.startsWith('~')
            if (isNot) {
                domain = domain.substring(1)
            }

            elementFilterList += if (domain.endsWith('*')) {
                domain = domain.substring(0, it.length - 1)
                TldRemovedElementFilter(domain, isHide, isNot, body.sanitizeSelector())
            } else {
                PlaneElementFilter(domain, isHide, isNot, body.sanitizeSelector())
            }
        }
    }

    private fun String.sanitizeSelector() = trim().replace("\\", "\\\\").replace("'", "\'")

    private fun String.decodeFilter(
        elementFilterList: MutableList<UnifiedFilter>,
        filterLists: FilterMap
    ) {
        var contentType = 0
        var ignoreCase = true
        var domain: String? = null
        var thirdParty = NO_PARTY_PREFERENCE
        var filter = this
        var elementFilter = false
        var modify: ModifyFilter? = null
        var badfilter = ""
        var important = false
        val blocking = if (filter.startsWith("@@")) {
            filter = substring(2)
            false
        } else {
            true
        }
        // re-write uBo responseheader to allow easier parsing
        val responseHeaderStart = filter.indexOf("##^responseheader(")
        if (responseHeaderStart > -1) {
            val end = filter.indexOf(')', responseHeaderStart)
            val header = filter.substring(responseHeaderStart + 18, end)
            if (!RESPONSEHEADER_ALLOWED.contains(header))
                return
            val other = filter.substringAfter(header)
            filter = if (other.length > 2 && other[2] == '$')
                filter.substring(0, responseHeaderStart) + "\$removeheader=" + header + "," + other.substring(2)
            else
                filter.substring(0, responseHeaderStart) + "\$removeheader=" + header
        }

        val optionsIndex = filter.lastIndexOf('$')
        if (optionsIndex >= 0) {
        val options = filter.substring(optionsIndex + 1).split(',').toMutableList()
        // move denyallow to last position, domains need to be before denyallow
        for (i in 0 until options.size) {
            if (options[i].startsWith("denyallow")) {
                options.add(options[i])
                options.removeAt(i)
                break
            }
        }
/*      don't care about specifics of $all for now, just use content type
            // all is equal to: document, popup, inline-script, inline-font
            //  but on mobile / webview there are no popups anyway (all opened in the same window/tab)
            if (options.contains("all")) {
                options.remove("all")
                contentType = contentType or ContentRequest.TYPE_DOCUMENT or ContentRequest.TYPE_STYLE_SHEET or ContentRequest.TYPE_IMAGE or ContentRequest.TYPE_OTHER or ContentRequest.TYPE_SCRIPT or ContentRequest.TYPE_XHR or ContentRequest.TYPE_FONT or ContentRequest.TYPE_MEDIA or ContentRequest.TYPE_WEB_SOCKET
                when {
                    options.contains("~inline-font") && options.contains("~inline-script") -> Unit // ignore both
                    options.contains("~inline-font") -> { // ignore inline-font only
                        options.add("inline-script")
                    }
                    options.contains("~inline-script") -> { // ignore inline-script only
                        options.add("inline-font")
                    }
                    else -> options.add("csp=font-src *; script-src 'unsafe-eval' * blob: data:") // take both
                }
                options.remove("~inline-font")
                options.remove("~inline-script")
            }
*/
            options.forEach {
                var option = it
                var value: String? = null
                val separatorIndex = option.indexOf('=')
                if (separatorIndex >= 0) {
                    value = option.substring(separatorIndex + 1)
                    option = option.substring(0, separatorIndex)
                }
                if (option.isEmpty() || (option.startsWith("_") && option.matches("^_+$".toRegex()))) return@forEach

                val inverse = option[0] == '~'
                if (inverse) {
                    option = option.substring(1)
                }

                option = option.lowercase()
                val type = option.getOptionBit()
                if (type == -1) return

                when {
                    type > 0x00ff_ffff -> {
                        elementFilter = true
                    }
                    type > 0 -> {
                        contentType = if (inverse) {
                            if (contentType == 0) contentType = ContentRequest.TYPE_ALL
                            contentType and (type.inv())
                        } else {
                            contentType or type
                        }
                    }
                    type == 0 -> {
                        when (option) {
                            "match-case" -> ignoreCase = inverse
                            "domain" -> {
                                if (value == null) return
                                domain = value.lowercase()
                            }
                            "third-party", "3p" -> thirdParty = if (inverse) FIRST_PARTY else THIRD_PARTY
                            "first-party", "1p" -> thirdParty = if (inverse) THIRD_PARTY else FIRST_PARTY
                            "strict3p" -> thirdParty = if (inverse) STRICT_FIRST_PARTY else STRICT_THIRD_PARTY
                            "strict1p" -> thirdParty = if (inverse) STRICT_THIRD_PARTY else STRICT_FIRST_PARTY
                            "sitekey" -> Unit
                            "removeparam", "queryprune" -> {
                                modify = if (value == null || value.isEmpty()) RemoveparamFilter(null, false)
                                else {
                                    if (value.startsWith('~'))
                                        getRemoveparamFilter(value.substring(1), true)
                                    else
                                        getRemoveparamFilter(value, false)
                                }
                            }
                            "csp" -> {
                                modify = if (value == null) ResponseHeaderFilter("Content-Security-Policy", false)
                                else ResponseHeaderFilter("Content-Security-Policy: ${value.substringAfter('=')}", false)
                                contentType = contentType or (ContentRequest.TYPE_DOCUMENT and ContentRequest.TYPE_SUB_DOCUMENT) // uBo documentation: It can be applied to main document and documents in frames
                            }
                            "inline-font" -> {
                                // header value from uBlock source, src/js/background.js
                                modify = ResponseHeaderFilter("Content-Security-Policy: font-src *", false)
                                contentType = contentType or (ContentRequest.TYPE_DOCUMENT and ContentRequest.TYPE_SUB_DOCUMENT)
                            }
                            "inline-script" -> {
                                // header value from uBlock source, src/js/background.js
                                modify = ResponseHeaderFilter("Content-Security-Policy: script-src 'unsafe-eval' * blob: data:", false)
                                contentType = contentType or (ContentRequest.TYPE_DOCUMENT and ContentRequest.TYPE_SUB_DOCUMENT)
                            }
                            "removeheader" -> {
                                value = value?.lowercase() ?: return
                                val request = value.startsWith("request:")
                                val header = if (request) value.substringAfter("request:") else value
                                if (header in REMOVEHEADER_NOT_ALLOWED) return
                                modify = if (request) RequestHeaderFilter(header, true)
                                else ResponseHeaderFilter(header, true)
                            }
                            "redirect-rule" -> modify = RedirectFilter(value)
                            "redirect" -> {
                                // create block filter in addition to redirect
                                this.removeOption("redirect").decodeFilter(elementFilterList, filterLists)
                                modify = RedirectFilter(value)
                            }
                            "empty" -> {
                                this.removeOption("empty").decodeFilter(elementFilterList, filterLists)
                                modify = RedirectFilter(RES_EMPTY)
                            }
                            "mp4" -> {
                                this.removeOption("mp4").decodeFilter(elementFilterList, filterLists)
                                modify = RedirectFilter(RES_NOOP_MP4)
                                contentType = contentType or ContentRequest.TYPE_MEDIA // uBo documentation: media type will be assumed
                            }
                            "important" -> important = true
                            // TODO: see above, all is not handled 100% correctly (but might still be fine)
                            "all" -> contentType = contentType or ContentRequest.TYPE_DOCUMENT or ContentRequest.TYPE_STYLE_SHEET or ContentRequest.TYPE_IMAGE or ContentRequest.TYPE_OTHER or ContentRequest.TYPE_SCRIPT or ContentRequest.TYPE_XHR or ContentRequest.TYPE_FONT or ContentRequest.TYPE_MEDIA or ContentRequest.TYPE_WEB_SOCKET
                            "badfilter" -> badfilter = ABP_PREFIX_BADFILTER
                            "denyallow" -> {
                                // https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters#denyallow-modifier
                                //  either do regex filter
                                //   but this also matches path, not just host
                                //  or create filter + exception filters -> a lot of filters, but no regex
                                //   problem here: denyallow is usually for common domains, so there will be many additional checks
                                //   TODO: how to handle best? have new filter type that has all denyallow domains as pattern?
                                //    could work, and would be much better for loading, and probably faster
                                //    but first do a performance test whether effect of denyallow is actually measurable

                                if (domain == null || value.isNullOrEmpty()) return
                                if (value.any { it == '*' || it == '~' }) return
                                val withoutDenyallow = this.removeOption("denyallow=$value")
                                withoutDenyallow.decodeFilter(elementFilterList, filterLists)
                                val optionsString = withoutDenyallow.substringAfter('$')
                                value.split('|').forEach { denyAllowDomain ->
                                    "@@||$denyAllowDomain\$$optionsString".decodeFilter(elementFilterList, filterLists)
                                }
                            }
                            else -> return
                        }
                    }
                }
            }
            if (contentType and ContentRequest.TYPE_UNSUPPORTED != 0 && contentType and ContentRequest.INVERSE == 0) {
                // remove filter if type is exclusively TYPE_UNSUPPORTED
                if (contentType == ContentRequest.TYPE_UNSUPPORTED) return
                // remove TYPE_UNSUPPORTED if it's not the only type
                else contentType = contentType xor ContentRequest.TYPE_UNSUPPORTED
            }

            filter = filter.substring(0, optionsIndex)

            // some lists use * to match all, some use empty
            //  convert * to empty since it will result in a simple contains filter
            if (filter == "*") filter = ""
        }

        // remove invalid modify filters
        modify?.let {
            // only removeparam may have no parameter when blocking
            if (blocking && it.parameter == null && it !is RemoveparamFilter) return

            // filters that add headers must contain header and value, separated by ':'
            if (blocking && !it.inverse && (it is RequestHeaderFilter || it is ResponseHeaderFilter)
                && it.parameter?.contains(':') == false)
                    return

            // in case of important redirect filters, only the blocking part should be important
            important = false
        }

        val domains = domain?.domainsToDomainMap('|')
        if (contentType == 0) contentType = ContentRequest.TYPE_ALL

        if (elementFilter) {
            return
        }

        val abpFilter =
            if (filter.length >= 2 && filter[0] == '/' && filter[filter.lastIndex] == '/' && filter.mayContainRegexChars()) {
                try { RegexFilter(filter.substring(1, filter.lastIndex), contentType, ignoreCase, domains, thirdParty, modify) }
                catch (e: Exception) { return }
            } else {
                val isStartsWith = filter.startsWith("||")
                val isEndWith = filter.endsWith('^')
                var content = filter.substring(
                    if (isStartsWith) 2 else 0,
                    if (isEndWith) filter.length - 1 else filter.length
                )
                if (ignoreCase) content = content.lowercase()
                val isLiteral = content.isLiteralFilter()
                if (isLiteral) {
                    when {
                        isStartsWith && isEndWith -> StartEndFilter(content, contentType, ignoreCase, domains, thirdParty, modify)
                        isStartsWith -> StartsWithFilter(content, contentType, ignoreCase, domains, thirdParty, modify)
                        isEndWith -> EndWithFilter(content, contentType, ignoreCase, domains, thirdParty, modify)
                        else -> {
                            // mimic uBlock behavior: https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#hosts-files
                            if (this == content && "http://$content".toUri().host == content)
                                StartEndFilter(content, contentType, ignoreCase, domains, thirdParty, modify)
                            else
                                ContainsFilter(content, contentType, ignoreCase, domains, thirdParty, modify)
                        }
                    }
                } else {
                    PatternMatchFilter(filter, contentType, ignoreCase, domains, thirdParty, modify)
                }
            }

        when {
            elementFilter -> elementFilterList += abpFilter
            modify != null && blocking -> {
                if (modify is RedirectFilter)
                    filterLists[badfilter + ABP_PREFIX_REDIRECT] += abpFilter
                else
                    filterLists[badfilter + ABP_PREFIX_MODIFY] += abpFilter
            }
            important && blocking -> filterLists[badfilter + ABP_PREFIX_IMPORTANT] += abpFilter
            blocking -> filterLists[badfilter + ABP_PREFIX_DENY] += abpFilter
            modify != null -> {
                if (modify is RedirectFilter)
                    filterLists[badfilter + ABP_PREFIX_REDIRECT_EXCEPTION] += abpFilter
                else
                    filterLists[badfilter + ABP_PREFIX_MODIFY_EXCEPTION] += abpFilter
            }
            important -> filterLists[badfilter + ABP_PREFIX_IMPORTANT_ALLOW] += abpFilter
            else -> filterLists[badfilter + ABP_PREFIX_ALLOW] += abpFilter
        }
    }

    private fun String.mayContainRegexChars(): Boolean {
        forEach {
            when (it.lowercaseChar()) {
                in 'a'..'z', in '0'..'9', '%', '/', '_', '-' -> Unit
                else -> return true
            }
        }
        return false
    }

    // removes the option (must be full match) and any useless $ or ,
    private fun String.removeOption(toRemove: String): String {
        var optionString = this.substringAfterLast('$').replace(toRemove, "").replace(",,", ",")
        if (optionString.endsWith(','))
            optionString = optionString.dropLast(1)
        return if (optionString.isEmpty())
            this.dropLast(1) // no further filter options, remove $
        else
            this.substringBeforeLast('$') + "$" + optionString
    }

    private fun String.domainsToDomainMap(delimiter: Char): DomainMap? {
        if (length == 0) return null

        val items = split(delimiter)
        return if (items.size == 1) {
            if (items[0][0] == '~') {
                SingleDomainMap(false, items[0].substring(1))
            } else {
                SingleDomainMap(true, items[0])
            }
        } else {
            val domains = ArrayDomainMap(items.size, this.contains('*'))
            items.forEach { domain ->
                if (domain.isEmpty()) return@forEach
                if (domain[0] == '~') {
                    domains[domain.substring(1)] = false
                } else {
                    domains[domain] = true
                    domains.include = true
                }
            }
            domains
        }
    }

    private fun String.isLiteralFilter(): Boolean {
        forEach {
            when (it) {
                '*', '^', '|' -> return false
            }
        }
        return true
    }

    private fun String.getOptionBit(): Int {
        return when (this) {
            "other", "xbl", "dtd" -> ContentRequest.TYPE_OTHER
            "script" -> ContentRequest.TYPE_SCRIPT
            "image", "background" -> ContentRequest.TYPE_IMAGE
            "stylesheet", "css" -> ContentRequest.TYPE_STYLE_SHEET
            "subdocument", "frame" -> ContentRequest.TYPE_SUB_DOCUMENT
            "document", "doc" -> ContentRequest.TYPE_DOCUMENT
            "websocket" -> ContentRequest.TYPE_WEB_SOCKET
            "media" -> ContentRequest.TYPE_MEDIA
            "font" -> ContentRequest.TYPE_FONT
            "popup", "object", "webrtc", "ping", "object-subrequest", "popunder" -> ContentRequest.TYPE_UNSUPPORTED
            "xmlhttprequest", "xhr" -> ContentRequest.TYPE_XHR
            "genericblock" -> -1
            "elemhide", "ehide" -> ContentRequest.TYPE_ELEMENT_HIDE
            "generichide", "ghide" -> ContentRequest.TYPE_ELEMENT_GENERIC_HIDE
            else -> 0
        }
    }

    private fun String.decodeComment(url: String?, info: DecoderInfo) {
        // comment format:
        // ! <title>: <content>
        if (!contains(':')) return
        val title = substringBefore(':').drop(1).trim().lowercase()
        val content = substringAfter(':').trim()
        val comment = split(':')
        if (comment.size < 2) return

        when (title) {
            "title" -> info.title = content
            "homepage" -> info.homePage = content
            "last updated" -> info.lastUpdate = content
            "expires" -> info.expires = content.decodeExpires()
            "version" -> info.version = content
            "redirect" -> info.redirectUrl = content
        }
    }

    private fun String.decodeExpires(): Int {
        val hours = indexOf("hours")
        if (hours > 0) {
            return try {
                substring(0, hours).trim().toInt()
            } catch (e: NumberFormatException) {
                -1
            }
        }
        val days = indexOf("days")
        if (days > 0) {
            return try {
                substring(0, days).trim().toInt() * 24
            } catch (e: NumberFormatException) {
                -1
            }
        }
        return -1
    }

    private class DecoderInfo : UnifiedFilterInfo(null, null, null, null, null, null) {
        override var expires: Int? = null
        override var homePage: String? = null
        override var lastUpdate: String? = null
        override var title: String? = null
        override var version: String? = null
        override var redirectUrl: String? = null
    }

    companion object {
        const val HEADER = "[Adblock Plus"

        private const val ELEMENT_FILTER_REGEX = "^([^/*|@\"!]*?)#([@?\$])?#(.+)\$"
    }
}
