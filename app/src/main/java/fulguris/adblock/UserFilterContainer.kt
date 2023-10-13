package fulguris.adblock

import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter

// UnifiedFilterResponse looks nicer than Pair<UnifiedFilter, Boolean?>
// response: true -> block, false -> allow, null: noop
data class UnifiedFilterResponse(val filter: UnifiedFilter, val response: Boolean?)

/*
    works somewhat similar to FilterContainer, but tailored for uBo-style dynamic filtering
    the important part: specific rules should override general rules
     so checking allow, then block (as done for ads) can't work here
     -> get most specific matching filter and return the associated response
 */
class UserFilterContainer {
    private val filters = hashMapOf<String, List<UnifiedFilterResponse>>()

    private val filterComparator = Comparator<UnifiedFilterResponse> { f1, f2 ->
        // TODO: order may not be correct, https://github.com/gorhill/uBlock/wiki/Dynamic-filtering:-precedence and uBo settings seem to disagree
        //  using what the dashboard suggests: first request domain, then 3rd party, then content type, then local
        when {
            // first: request (sub)domain matches
            //  filter already matches -> if there is a pattern (=requestDomain) (sub)domain must match
            //  -> if both domain and subdomain exist, prefer subdomain as it's more specific
            //   since both must match, the longer one must be more specific (if they're same length, they must be equal)
            f1.filter.pattern != f2.filter.pattern -> f1.filter.pattern.length - f2.filter.pattern.length

            // then: third party matches (filter already matches -> prefer specific 3rd party value (1 or 0) over -1)
            f1.filter.thirdParty != f2.filter.thirdParty -> f1.filter.thirdParty - f2.filter.thirdParty

            // then: content type more precise (filter already matches -> prefer specific (0<value<0xffff) over generic (0xffff), i.e. smaller)
            f1.filter.contentType != f2.filter.contentType -> f2.filter.contentType - f1.filter.contentType

            // then: pageDomain matches
            //  as for request domain: more specific domain wins
            f1.filter.domains != f2.filter.domains -> getBetterDomainMatch(f1, f2)

            else -> 0
        }
    }

    fun add(filter: UnifiedFilterResponse) {
        // no need for explicit tag, it's either pageUrl.host or empty (stored in the domainMap)
        // domains can only be SingleDomainMaps with include=true for user rules
        val tag = filter.filter.domains?.getKey(0) ?: ""
        filters[tag] = filters[tag]?.plus(filter) ?: listOf(filter)
    }

    fun remove(filter: UnifiedFilterResponse) {
        val tag = filter.filter.domains?.getKey(0) ?: ""
        filters[tag]?.mapNotNull { if (it != filter) it else null }?.let {
            if (it.isEmpty())
                filters.remove(tag)
            else
                filters[tag] = it
        }
    }

    fun get(request: ContentRequest): UnifiedFilterResponse? {
        // tags are not really used (only pageDomain and empty) -> ignore tags from contentRequest

        val matchingFilters = (filters[""] ?: listOf()) + (filters[request.pageHost] ?: listOf())

        if (matchingFilters.isEmpty()) return null
        if (allSameResponse(matchingFilters)) return matchingFilters.first()

        // get the highest priority rule according to uBo criteria (see comments inside filterComparator)
        // TODO: test whether it does what it should
        return matchingFilters.maxOfWith(filterComparator, {it})
    }


    private fun getBetterDomainMatch(f1: UnifiedFilterResponse, f2: UnifiedFilterResponse): Int {
        // pageDomains are not equal, so if one of them is null, the other one is better
        val domains1 = f1.filter.domains ?: return -1
        val domains2 = f2.filter.domains ?: return 1

        // there is only one domain for user rules, and both match but they aren't equal -> the longer one is more specific
        return domains1.getKey(0).length - domains2.getKey(0).length

/*        // might be extended later to arrayDomainMaps, as larger domainMaps can increase efficiency (at cost of simple add/remove of filters)
        var compare = 0
        for (i in 0..domains1.size) {
            if (domains1.getKey(i) == requestDomain) ++compare
        }
        for (i in 0..domains2.size) {
            if (domains2.getKey(i) == requestDomain) --compare
        }
        return compare*/
    }

    private fun allSameResponse(filters: List<UnifiedFilterResponse>): Boolean {
        // get first response, go through others, return false if one is different
        if (filters.size == 1) return true
        val response = filters.first().response
        filters.drop(1).forEach { if (it.response != response) return false }
        // in the end return true
        return true
    }

}