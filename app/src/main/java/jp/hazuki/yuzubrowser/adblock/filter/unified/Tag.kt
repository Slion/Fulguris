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

package jp.hazuki.yuzubrowser.adblock.filter.unified

object Tag {
    fun create(url: String): List<String> {
        return url.lowercase().getTagCandidates().also {
            it += ""
        }
    }

    fun createBest(pattern: String) = pattern.lowercase().getTagCandidates().maxByOrNull { it.length } ?: ""

    fun createBest(filter: UnifiedFilter): String {
        when {
            // ContainsFilter requires removal of tags directly at start and end
            //   ContainsFilter with pattern 'example' matches 'http://test.com/badexamples'
            //   but will never get checked because tags don't match
            // same for ContainsHostFilter, though this is actually never used
            filter is ContainsFilter || filter is ContainsHostFilter -> {
                val pattern = filter.pattern.lowercase()
                val tags = pattern.getTagCandidates()
                // remove tags directly at start or end of pattern
                if (tags.isNotEmpty() && pattern.lastIndexOf(tags.first()) == 0) // lastIndex because tag may occur multiple times
                    tags.removeAt(0)
                if (tags.isNotEmpty() && pattern.indexOf(tags.first()) == 0)
                    tags.removeAt(tags.lastIndex)
                return tags.maxByOrNull { it.length } ?: ""
            }
            filter is StartEndFilter && filter.pattern.none { it == '/' || it == '*' }
                    && !filter.pattern.endsWith('.') && filter.pattern.contains('.')
                    -> return filter.pattern // pattern is a domain
            filter.isRegex -> {
                // require tags to be between a few selected delimiters
                //   regex is used like a contains filter and can start in the middle of any string
                //   can't just have it delimited like normal, because tags may be created from pattern
                var pattern = filter.pattern.lowercase()

                // valid separators: "\\/(.+?\\.)?", "\\.", "\\/"
                //  convert to the same one for easier checking
                pattern = pattern.replace("\\/(.+?\\.)?", "\\.").replace("\\/", "\\.")

                // remove some common patterns before creating candidates
                //  replace everything in [] and () with some invalid char that is a separator for getTagCandidates
                pattern = pattern.replaceAllBetweenChars('[', ']', "|")
                pattern = pattern.replaceAllBetweenChars('(', ')', "|")

                val tags = pattern.getTagCandidates()

                // remove tags that don't have a valid separator on each side
                //  necessary for regex, as it's basically a contain filter
                var tag = ""
                for (i in tags.indices.reversed()) {
                    if (pattern.contains("\\.${tags[i]}\\.") && tags[i].length > tag.length)
                        tag = tags[i]
                }

                return tag
            }
            else ->  return createBest(filter.pattern)
        }
    }

    private fun String.replaceAllBetweenChars(start: Char, end: Char, replacement: String): String {
        var r = this
        val open = mutableListOf<Int>()
        val close = mutableListOf<Int>()
        var isOpen = false
        for (i in 0 until length) {
            when (get(i)) {
                start -> {
                    open.add(i)
                    if (isOpen) return "" // no nesting
                    else isOpen = true
                }
                end -> {
                    close.add(i)
                    if (!isOpen) return "" // no nesting
                    else isOpen = false
                }
            }
        }
        if (open.size != close.size)
            return "" // same amount of open and close

        for (i in open.indices.reversed()) {
            if (open[i] > close[i])
                return "" // open before close
            r = r.replaceRange(open[i], close[i], replacement)
        }
        return r
    }

    private fun String.getTagCandidates(): MutableList<String> {
        var start = 0
        val list = mutableListOf<String>()
        for (i in 0 until length) {
            when (get(i)) {
                // changed: allow tags to contain wildcard, but remove those tags (see isPrevent)
                //  purpose: tags are created for patterns containing wildcards (change in PatternMatchFilter)
                //  so no need to check against all patterns containing wildcards
                //  but we can't use something like google*ads as tag, and using google or ads is bad because the tag is not complete
                //   e.g. if url has google14/23ads, none of the tags google, ads, google*ads will match
//                in 'a'..'z', in '0'..'9', '%' -> continue
                in 'a'..'z', in '0'..'9', '%', '*' -> continue
                else -> {
                    if (i != start && i - start >= 3) {
                        val tag = substring(start, i)
                        if (!isPrevent(tag)) {
                            list += tag
                        }
                    }
                    start = i + 1
                }
            }
        }
        if (length != start && length - start >= 3) {
            val tag = substring(start, length)
            if (!isPrevent(tag)) {
                list += tag
            }
        }
        return list
    }

    private fun isPrevent(tag: String): Boolean {
        // added: remove if contains wildcard
        if (tag.contains('*'))
            return true
        return when (tag) {
            "http", "https", "html", "jpg", "png" -> true
            else -> false
        }
    }
}
