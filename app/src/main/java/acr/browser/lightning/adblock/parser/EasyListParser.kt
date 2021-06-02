package acr.browser.lightning.adblock.parser

import android.content.SharedPreferences
import java.io.InputStreamReader
import java.util.*

class EasyListParser {

    // https://adblockplus.org/en/filter-cheatsheet
    // https://help.eyeo.com/en/adblockplus/how-to-write-filters

    companion object {
        //  in general the rules are applied like url.contains(line), but there are some symbols
        //  *  - wildcard for any number of any characters
        //  ^  - separator: "anything but a letter, a digit, or one of _ - . %" (can also be end of link)
        //  || - domain anchor: replaces http:// and similar
        //  !  - comment
        //  $  - separates actual filter from filter options (which are comma-separated)
        //  @@ - exclusion: don't block what follows (always at start of line?)
        //  ##, ###, #?#, #@#: content filter, not usable at download time -> ignore

        const val SEPARATOR = '^'
        val SEPARATORS = // created from description above and https://stackoverflow.com/questions/7109143/what-characters-are-valid-in-a-url#7109208
                listOf('/','&','=',':','?',';','@','[',']','(',')','!',',','+','~','*','\'','$')
        const val OPTION_SEPARATOR = '$'
        const val WILDCARD = '*'
        const val RUlE_SEPARATOR = '§' // for internal use here, should not occur on easylist (maybe remove/change)
        const val DOMAIN_ANCHOR = "||"
        const val EXCLUSION = "@@"
        val CONTENT_FILTERS = listOf("##", "#@#", "#?#") // ### not necessary, because we use contains to find them


        // filter options (can be inverted by leading ~)
        // if multiple types: any match is enough? or all? if all, ignoring some not understood rules might be bad
        const val THIRD_PARTY = "third-party" // should be clear
        const val DOMAIN = "domain=" // followed by a domain / list of domains, invert is on each domain (check: are either none or all of the domains inverted?)
        const val MATCH_CASE = "match-case" // should be clear, but not implemented since not found on easylist (but probably on other lists)
        val KNOWN_FILTERS = listOf(THIRD_PARTY, DOMAIN) // implemented in blocker

        // detect these by file ending followed by a separator? not yet implemented
        const val IMAGE = "image" // regular images, typically loaded via the HTML img tag
        val IMAGE_ENDINGS = listOf(".jpg", ".jpeg", ".webp", ".gif", ".png", ".svg") // probably more
        const val SCRIPT = "script" // external scripts loaded via the HTML script tag
        val SCRIPTS_ENDINGS = listOf(".js") // more?
        const val STYLESHEET = "stylesheet" // external CSS stylesheet files
        val STYLESHEET_ENDINGS = listOf(".css") // more?
        const val FONTS = "font" // external font files
        val FONT_ENDINGS = listOf(".ttf", ".jfproj", ".woff", ".otf", ".fnt") // there are many more... check which are used
        const val MEDIA = "media" // regular media files like music and video
        val MEDIA_ENDINGS = listOf(".mp3", ".mp4", ".mpg", ".ogg", ".wav", ".aac") // there are many more...
        // object: content handled by browser plug-ins, e.g. Flash or Java -> are those actually supported in webview? flash not, java??

        // need idea how to detect these options from url (if possible at all)
        //  popup: if this is a popup
        //  webrtc: connections opened via RTCPeerConnection instances to ICE servers
        //  ping: requests started by or navigator.sendBeacon()
        //  subdocument: page loaded within frames
        //  document: the page itself, used for exclusions only
        //  xmlhttprequest: requests started using the XMLHttpRequest object or fetch() API
        //  websocket: requests initiated via WebSocket object

        // ignore these
        //  elemhide - for exception rules only, similar to document but only turns off element hiding rules on the page rather than all filter rules (Adblock Plus 1.2 or higher is required)
        //  generichide - for exception rules only, similar to elemhide but only turns off generic element hiding rules on the page (Adblock Plus 2.6.12 or higher is required)
        //  genericblock - for exception rules only, just like generichide but turns off generic blocking rules (Adblock Plus 2.6.12 or higher is required)
        //  other

        // prefs
        const val MAYBE_MAP = "easylist_maybe" // preferences file for filters containing domain and (path or filter options)
        const val EXCLUSION_MAP = "easylist_exclusions" // preferences file for exclusions containing domain
        const val SETS = "easylist_string_sets" // preferences file for string sets (domain lists and generic filters (without rules) not based on domain)
        const val PREFS_HOSTS_LIST = "hostsList" // hosts to be blocked
        const val PREFS_THIRD_PARTY_HOSTS_LIST = "thirdPartyHostsList" // hosts to be blocked if third party
        const val PREFS_EXIST = "exists" // preference indicating the file exists (easiest way to check)

        // SETS pref file contains domain-less filters that will be used like path.contains(filter)
        //  including pattern matching for wildcard and separators (sould be optimized)
        // comparing every url path against the full generic list takes too long
        // approach: split the list into many sub-lists with certain key strings
        //  so if the path contains a key, it is checked only against a small list
        //  then the key is removed so that more generic filters don't match
        //  e.g. /ads/ and then /ads: first check for /ads/, if contains but not excluded: remove /ads/ from path and check /ads
        //   this way we still can block paths containing /ads/ and /ads
        //  order is important: if ad comes first, /ads/ is useless

        // TODO: accelerate! calling path.contains on hundreds of patterns is slow, pattern matching even worse
        //  1. investigate whether this makes sense: add (potentially empty) lists to catch frequent false positives
        //   check how many unnecessary checks occur, and why. then find useful (potentially empty?) lists/keywords to add
        //   -> log all request urls for a few days, and check which lists are frequently needlessly searched, and why
        //    found so for: upload -> ad, json -> js
        //  2. adjust the order: e.g if paths containing 'click' are almost always blocked, move 'click' to earlier position
        //    also might be useful to check shorter lists. e.g. if google often appears in combination with ad, it might not be necessary to check the full ad list
        //  3. try to have a small 'residual' list that is checked every time, ideally less than 100 entries
        //   currently this is done via a bunch of short lists matching only 2 characters, this could trigger a lot of false positives?
        //  4. even if a list frequently triggers false positives:
        //   for most urls it still will not be checked, but it would be if it was part of the residual list... so with short lists this is actually fine
        //  5. it seems a 2-3 level tree-structure would be useful here...
        //   then it might not be necessary to remove "used-up" strings from the path, which is currently done and can lead to false negatives
        val PATTERN_LIST = listOf( // rough number of entries for each list
                "/ads/",// 600
                "/ads", // 700
                "ads/", // 300
                "ads.", // 500
                "ads",  // 500
                "adv",  // 600
                "/ad/", // 300
                "/ad_", // 300
                "/ad-", // 200
                "/ad",  // 900, probably blocked in many cases, but still quite long
                "ad_",  // 300
                "ad-",  // 200
                "-ad",  // 200
                "_ad",  // 300
                "ad/",  // 100
                "ad.",  // 300
                "ead",  // 70
                "?ad",  // 40
                "rad",  // 30
                "=ad",  // 20
                "ad=",  // 30
                "ad",   // 270, this can easily be a false positive -> shrink more?
                "ban",  // 240, ban/banner
                "pop",  // 100, popup/under
                "0x",   // 200, banner size
                "0_",   // 80, banner size
                "8x",   // 100, banner size
                "0.",   // 50, banner size? might also give more false positives
                "spo",  // 80, sponsor
                "js",   // 150, probably triggered by many js... maybe split somehow?
                "aff",  // 50, affiliate
                "click",// 40, mostly doubleclick, use this to avoid false positives?
                "bid",  // 30, live bidding
                "live", // 30
                "google",//10, short... move to earlier position? would make some ad lists shorter (but bad if google occurs in a lot of requests without ads)
                "cgi",  // 20
                ".php", // 60, probably many false positives... but they would be checked anyway
                "id",   // 30, am i getting desperate?
                "script",//30
                "/r",   // 20, now it's getting really generic and useless. these lists are really short
                "/p",   // 50
                "/a",   // 40
                "/e",   // 30
                "/o",   // 30
                "/d",   // 20
                "ma",   // 20
                "in",   // 10
                "ir",   // 10
        )
        // currently ca 160 entries that are checked no matter what... still too much
        //  it's probably worth it to have some more short lists to further reduce this
        const val PATTERN_REST = "rest"
    }

    // TODO: performance
    //  switch to bloom filter, it's implemented, faster, and doesn't abuse shared preferences as DB
    //  but this is for later, currently pattern matching is way too slow
    //   idea: extend hosts db to include filter options and path pattern
    //    then if there is a maybe-hit by the bloom filter, we can load the rules from db
    //    generic lists ('patternMap') still needs to be checked every time, and probably stored in a different table (how exactly?)
    val maybeMap = mutableMapOf<String,String>()
    val exclusionMap = mutableMapOf<String,String>()
    val hostsList = mutableSetOf<String>()
    val thirdPartyHostsList = mutableSetOf<String>()
    val patternMap = mutableMapOf<String, MutableSet<String>>()


    fun parseInput(input: InputStreamReader, hostsPrefs: SharedPreferences, maybePrefs: SharedPreferences, exclusionPrefs: SharedPreferences) {
        for (item in PATTERN_LIST) {
            patternMap[item] = mutableSetOf()
        }
        patternMap[PATTERN_REST] = mutableSetOf()
        input.use { inputStreamReader ->
            inputStreamReader.forEachLine {
                try {
                    parseLine(it)
                } catch (e: Exception) {
                    // TODO: check lines that trigger this
                }
            }
        }

        var editor = hostsPrefs.edit()
        editor.putStringSet(PREFS_HOSTS_LIST, hostsList)
        editor.putStringSet(PREFS_THIRD_PARTY_HOSTS_LIST, thirdPartyHostsList)

        for (item in PATTERN_LIST) {
            editor.putStringSet(item, patternMap[item])
        }
        editor.putStringSet(PATTERN_REST, patternMap[PATTERN_REST])

        editor.apply()
        editor = maybePrefs.edit()
        maybeMap.forEach { if (!hostsList.contains(it.key)) // don't clutter the maybeMap with stuff that can't be checked
                            editor.putString(it.key,it.value) }
        editor.apply()
        editor = exclusionPrefs.edit()
        exclusionMap.forEach { editor.putString(it.key,it.value) }
        editor.apply()
    }

    // accelerate? currently takes 6-7 seconds for easylist on my 9192, which is a lot
    // maybe skip that [Adblock Plus 2.0] line... is it always the 1st line?
    // could be merged with hosts list parser, to understand both formats?
    //  might be useful for the common db
    // TODO: check what is removed by which rule, and maybe implement
    fun parseLine(line2: String) {
        var line = line2.trim().toLowerCase(Locale.ROOT)

        // ignore lines with not (yet) implemented expressions
        CONTENT_FILTERS.forEach { if (line.contains(it)) return }

        // ignore comments (starting with !) and empty lines
        if (line.startsWith("!") || line.isEmpty())
            return

        // if there are filter options, remove unknown ones and ignore line if we removed all
        if (OPTION_SEPARATOR in line) {
            val filters = line.substringAfter(OPTION_SEPARATOR).split(",")
            line = line.substringBefore(OPTION_SEPARATOR)
            val newFilters = mutableListOf<String>()
            // double loop for finding KNOWN_FILTERS contained in filters
            filters.forEach { filter ->
                KNOWN_FILTERS.forEach { knownFilter ->
                    if (filter.contains(knownFilter)) // "contains" to get negations and domain=
                        newFilters.add(filter)
                }
            }
            if (newFilters.isEmpty())
                return
            line = line + OPTION_SEPARATOR + newFilters.joinToString(",")
        }

        if (line.startsWith(DOMAIN_ANCHOR)) { // we have a domain (and maybe more)
            line = line.drop(DOMAIN_ANCHOR.length) // remove ||

            // ignore incomplete ip addresses (maybe implement later)
            if (line.matches("""\d{1,3}\.\d{1,3}\..*""".toRegex())
                    && !line.matches("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\..*""".toRegex()))
                return

            // discard wildcards in domain (implement later, at least if it's replacing TLD)
            val wildcard = line.indexOf(WILDCARD)
            val separator = line.indexOfFirst { it == SEPARATOR || it == '/' } // both are used for domains, anything else?
            if (wildcard > -1 && separator > -1 && wildcard < separator)
                return

            // remove incomplete domains (might be nice to have, but needs to be implemented
            if (!line.substringBefore(OPTION_SEPARATOR).dropLast(1).contains('.'))
                return

            if (OPTION_SEPARATOR in line) {
                // put on 3rd party hosts list if there is no path and option is only third party
                if (line.substringAfter(OPTION_SEPARATOR) == THIRD_PARTY
                        && maybeAddToList(line.substringBefore(OPTION_SEPARATOR), thirdPartyHostsList))
                    return
            } else {
                // add to hosts list if there is no path
                if (maybeAddToList(line, hostsList))
                    return
            }

            putOnMap(line, maybeMap) // domain rules that are more complicated than a hosts list
            return
        }

        if (line.startsWith(EXCLUSION)) {
            line = line.drop(EXCLUSION.length) // remove @@
            if (!line.startsWith(DOMAIN_ANCHOR))
                return // there are very few entries in easylist that match, but still implement later
            line = line.drop(DOMAIN_ANCHOR.length) // remove ||

            // now we have something like the maybeMap, just for inverted use
            // possible problem: we really need the filters here...
            //  not fully understanding the filter options is bad, could lead to blocking things on exclusion list
            putOnMap(line, exclusionMap)
            return
        }

        // rest are generic filters (with or without filter option)
        //  only about path, not domain

        // ignore a bunch of lines
        //  TODO: option separator should be fine, implement in blocker and adjust accordingly
        if (OPTION_SEPARATOR in line || '|' in line || '\\' in line || ':' in line)
            return

        //  need to do some pre-selection of which urls to compare (comparison is slow)
        //   e.g. only the ones containing "banner" are compared to the list of patterns containing banner
        for (item in PATTERN_LIST) {
            if (item in line) {
                if (patternMap[item] == null)
                    patternMap[item] = mutableSetOf()
                patternMap[item]!!.add(line)
                return
            }
        }
        patternMap[PATTERN_REST]?.add(line)
    }

    fun maybeAddToList(maybeDomain: String, list: MutableSet<String>): Boolean {
        if (!maybeDomain.dropLast(1).contains(SEPARATOR) // only contains 1 separator
                && !maybeDomain.contains('/')) { // definitely no path (never occurs as last character on easylist)
            if (maybeDomain.endsWith(SEPARATOR))
                list.add(maybeDomain.dropLast(1))
            else
                list.add(maybeDomain)
            return true
        }
        return false
    }

    // put line on a domain map
    //  filter expressions could be converted to regex, but better do a small parser for wildcards and separators only
    private fun putOnMap(line: String, map: MutableMap<String,String>) {
        // better not use Uri.path / Uri.host, as uri might be invalid due to filter symbols
        val separator = line.indexOfFirst { it == SEPARATOR || it == '/' }
        val host = if (separator > -1)
                line.substring(0,separator) // everything before separator
            else
                return // if separator is -1, there is nothing to put in the map
        val lineAfterHost = line.substring(separator) // separator should be included!
        if (map.containsKey(host))
            map[host] = map[host] + RUlE_SEPARATOR + lineAfterHost
        else
            map[host] = lineAfterHost
    }

}