package acr.browser.lightning.adblock

import acr.browser.lightning.adblock.parser.EasyListParser
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.net.Uri
import java.io.InputStreamReader
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EasyListBlocker @Inject constructor(
        private val assetManager: AssetManager,
        private val application: Application): AdBlocker {

    private val hostsList: HashSet<String>
    private val thirdPartyHostsList: HashSet<String>
    private val patternMap = mutableMapOf<String, Set<String>>() // the way we check it hashSets probably offer no advantage (but better do some test)
    private val maybePrefs: SharedPreferences
    private val exclusionPrefs: SharedPreferences

    // caching recent results accelerates things considerably
    //  browsing some random sites gives ca 20-25% hits
    //  cache checks are ca 5 times faster than bloom filter checks (using full hosts list), and 10-100 times faster than pattern checks
    // using a simple map is a bit slower than a hashMap, but removing the oldest entries is easy
    //  and there are no concurrent modification exceptions... just that every now and then the cache might not be updated
    // there is no 3rd party check done here, maybe should be implemented
    private val cacheMap = mutableMapOf<String, Boolean>()
    private val maxCacheSize = 1000

    // TODO: change basic implementation
    //  idea: add columns to the hosts db (filter options, exclusions, path, maybe split path and path-with-wildcard/separator) and use bloom filter
    //  if host not on list -> check generic patterns
    //  if host maybe on list -> load row from db and check (no need to process rules every time!)
    //   and then (if no ad) check generic patterns
    init {

        // some kind of update / force refresh mechanism would be good
        //  simply remove the "exists" entry and restart blocker? won't work once it's changed to use the db
        val setPrefs = application.applicationContext.getSharedPreferences(EasyListParser.SETS, Context.MODE_PRIVATE)
        maybePrefs = application.applicationContext.getSharedPreferences(EasyListParser.MAYBE_MAP, Context.MODE_PRIVATE)
        exclusionPrefs = application.applicationContext.getSharedPreferences(EasyListParser.EXCLUSION_MAP, Context.MODE_PRIVATE)
        if (!setPrefs.contains(EasyListParser.PREFS_EXIST)) {
            // clear all 3 preferences files
            setPrefs.edit().clear().apply()
            maybePrefs.edit().clear().apply()
            exclusionPrefs.edit().clear().apply()

            // read data
            // TODO: performance
            //  currently parsing blocks the app, move to background!
            //  and actually storage should probably move to a db
            val reader = InputStreamReader(assetManager.open("easylist.txt"))
            val easyListParser = EasyListParser()
            easyListParser.parseInput(reader, setPrefs, maybePrefs, exclusionPrefs)

            // add entry to be sure parsing was successful
            setPrefs.edit().putBoolean(EasyListParser.PREFS_EXIST, true).apply()
        }

        // load host lists
        hostsList = setPrefs.getStringSet(EasyListParser.PREFS_HOSTS_LIST, hashSetOf<String>()) as HashSet
        thirdPartyHostsList = setPrefs.getStringSet(EasyListParser.PREFS_THIRD_PARTY_HOSTS_LIST, hashSetOf<String>()) as HashSet

        // load pattern lists
        for (item in EasyListParser.PATTERN_LIST) {
            patternMap[item] = setPrefs.getStringSet(item, setOf<String>()) as Set<String>
        }
        patternMap[EasyListParser.PATTERN_REST] = setPrefs.getStringSet(EasyListParser.PATTERN_REST, setOf<String>()) as Set<String>

    }

    override fun isAd(url2: String, site2: String): Boolean {
        // TODO: performance
        //  check which part gets triggered (detected as ad) how often
        //  especially interesting for the filter rules, not sure whether they get anything

        // no match-case filter in easylist -> use lower case only
        val url = url2.toLowerCase(Locale.ROOT)
        val site = site2.toLowerCase(Locale.ROOT)

        // try to use cache (maybe extend to use site domain for proper 3rd party handling)
        val cached = cacheMap[url]

        if (cached != null) {
            return cached
        }

        // check if it is ad, add result to cache
        val isAd = isAd2(url, site)
        cacheMap[url] = isAd
        // remove oldest entry if cache is too large (better would be sorting by use order...)
        if (cacheMap.size > maxCacheSize)
            cacheMap.remove(cacheMap.keys.first()) // this is a bit slow, try deleting bunches? but how?

        return isAd
    }

    private fun isAd2(url: String, site: String): Boolean {
        // sometimes 'site' is the previous page, which turns everything into 3rd party
        //  and messes with 'domain=' filter option -> how to fix?

        val urlDomain = Uri.parse(url).host ?: return false
        val siteDomain = Uri.parse(site).host ?: urlDomain // assume 1st party

        // build a list like ads.example.com -> [example.com, ads.example.com]
        //  so if example.com or ads.example.com on any of the following lists, we get a match
        val urlDomainList2 = urlDomain.split(".")
        val urlDomainList = if (urlDomainList2.size > 1)
                urlDomainList2.asReversed().runningReduce { acc, string -> "$string.$acc" }.drop(1)
            else
                return false // for urls like fulguris://home
        val siteDomainList2 = siteDomain.split(".")
        val siteDomainList = if (siteDomainList2.size > 1)
                siteDomainList2.asReversed().runningReduce { acc, string -> "$string.$acc" }.drop(1) // actually not necessary, only need the first remaining thing
            else
                urlDomainList // assume first party for urls like fulguris://home

        val thirdParty = urlDomainList[0] != siteDomainList[0]

        // first check hosts list
        if (isOnList(urlDomainList, hostsList))
            return !isExcluded(url, siteDomain, thirdParty)

        // then check 3rd party hosts list (if third party)
        if (thirdParty && isOnList(urlDomainList, thirdPartyHostsList))
            return !isExcluded(url, siteDomain, thirdParty)

        // check filter options if there are any
        var path = Uri.parse(url).path ?: return false
        val maybeDomain = whichIsOnList(urlDomainList, maybePrefs)

        if (maybeDomain != null && filterMatches(path, siteDomain, maybePrefs.getString(maybeDomain, "nope"), thirdParty))
            return !isExcluded(url, siteDomain, thirdParty)

        // check generic patterns
        for (item in EasyListParser.PATTERN_LIST) {
            if (path.contains(item)) {
                patternMap[item]?.forEach { if (path.matchesEasyPattern(it))
                    return !isExcluded(url, siteDomain, thirdParty) }
                // replace by something that is not matched, but not empty to avoid accidentally creating new matches
                path = path.replace(item, "§")
            }
        }

        patternMap[EasyListParser.PATTERN_REST]?.forEach { if (path.matchesEasyPattern(it))
            return !isExcluded(url, siteDomain, thirdParty) }

        return false
    }

    private fun filterMatches(path: String, siteDomain: String, rulesString: String?, thirdParty: Boolean): Boolean {
        // not sure what to do... first get the rules
        if (rulesString == null) return false
        val rules = rulesString.split(EasyListParser.RUlE_SEPARATOR)

        // TODO: performance
        //  check how long separating the rules takes, this could be done in init
        rules.forEach { rule -> // check if path matches any rule
            // split filters into list
            val filters = rule.substringAfter(EasyListParser.OPTION_SEPARATOR).split(",")

            // get part between first separator and option separator
            val separator = rule.indexOfFirst { it == EasyListParser.SEPARATOR || it == '/' }
            val pattern = rule.substringBefore(EasyListParser.OPTION_SEPARATOR).substring(separator + 1)

            // if there are filters and none matches -> no need to check pattern
            if (filters.isEmpty() || filterOptionsMatch(filters, thirdParty, siteDomain)) {
                // check pattern
                return path.matchesEasyPattern(pattern)
            }
        }
        return false
    }

    private fun String.matchesEasyPattern(pattern: String): Boolean {
        // EasyListParser.SEPARATOR can be any of EasyListParser.SEPARATORS
        // EasyListParser.WILDCARD can be anything
        // just do it on character level
        //  basically copied from https://www.programcreek.com/2014/06/leetcode-wildcard-matching-java/
        //   with adjustments to use separator
        // this is ca 3.5 times slower than 'contains', probably much room for improvement

        // auto-switch to contains if no separator or wildcard in pattern
        if (!pattern.contains(EasyListParser.SEPARATOR) && !pattern.contains(EasyListParser.WILDCARD)) {
            return this.contains(pattern)
        }

        // try using regex instead?
        // no, this is far slower
/*        val regex = ("\\Q" + pattern + "\\E")
                .replace("*", "\\E.*\\Q")
                .replace("^", "\\E([\\/&=:?;@\\[\\]()!,+~*'\\$]|\$)\\Q")
                .toRegex()
            return this.matches(regex)
*/
        val actualPattern = "*$pattern*" // we actually want 'contains', not an exact match
        var i = 0
        var j = 0
        var starIndex = -1
        var iIndex = -1
        while (i < this.length) {
            if (j < actualPattern.length // not at the end
                    && ((actualPattern[j] == EasyListParser.SEPARATOR && EasyListParser.SEPARATORS.contains(this[i])) // match separator
                            || actualPattern[j] == this[i])) { // or match character
                ++i
                ++j
            } else if (j < actualPattern.length && actualPattern[j] == EasyListParser.WILDCARD) {
                starIndex = j
                iIndex = i
                j++
            } else if (starIndex != -1) {
                j = starIndex + 1
                i = iIndex+1
                iIndex++
            } else {
                return false
            }
        }
        while (j < actualPattern.length && actualPattern[j] == EasyListParser.WILDCARD) {
            ++j
        }

        // allow match of SEPARATOR to end of 'this' (after the last character)
        return j == if (pattern.endsWith(EasyListParser.SEPARATOR) && !EasyListParser.SEPARATORS.contains(this.last()))
            actualPattern.length-2
        else
            actualPattern.length
    }

    private fun filterOptionsMatch(filters: List<String>, thirdParty: Boolean, siteDomain: String): Boolean {
        for (filter in filters) {
            var fFilter = filter
            val invert = filter.contains("~") // this also works for domains, looks like it's always in- or exclude for all (check!)
            if (invert) fFilter = filter.drop(1)
            if (fFilter == EasyListParser.THIRD_PARTY) {
                if (thirdParty) {
                    return !invert
                }
            } else if (filter.startsWith(EasyListParser.DOMAIN)) {
                // never starts with invert, but the individual domains do (all or none, right?)
                val domains = filter.substringAfter("=").split("|")
                domains.forEach { domain ->
                    val domain2 = if (invert) domain.drop(1) else domain
                    if (domain2 == siteDomain) {
                        return !invert
                    }
                }
            }
        }
        return false
    }

    private fun isExcluded(url: String, siteDomain: String, thirdParty: Boolean): Boolean {
        val urlDomain = Uri.parse(url).host ?: return false
        val path = Uri.parse(url).path ?: return false
        val maybeDomain = whichIsOnList(listOf(urlDomain), exclusionPrefs) ?: return false
        return filterMatches(path, siteDomain, exclusionPrefs.getString(maybeDomain, "nope"), thirdParty)
    }

    override fun isAd(url: String): Boolean { // actually not used at all
        val host = Uri.parse(url).host ?: return false
        return isOnList(listOf(host), hostsList)
    }

    private fun isOnList(urlDomainList: List<String>, list: Set<String>): Boolean {
        urlDomainList.forEach { urlDomain ->
            if (list.contains(urlDomain))
                return true
        }
        return false
    }

    private fun whichIsOnList(urlDomainList: List<String>, prefs: SharedPreferences): String? {
        urlDomainList.forEach { urlDomain ->
            if (prefs.contains(urlDomain))
                return urlDomain }
        return null
    }

}
