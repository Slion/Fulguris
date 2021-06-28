package acr.browser.lightning.adblock

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

    fun add(filter: UnifiedFilterResponse) {
        // no need for explicit tag, it's either pageUrl.host or empty
        val tag = filter.filter.pattern
        // maybe can be improved/optimized
        val list = filters[tag]
        if (list.isNullOrEmpty())
            filters[tag] = listOf(filter)
        else {
            filters[tag] = list + filter
        }
    }

    fun remove(filter: UnifiedFilterResponse) {
        val tag = filter.filter.pattern
        // maybe can be improved/optimized
        val list = filters[tag]?.toMutableList() ?: return
        list.removeAll { it == filter }
        if (list.isEmpty())
            filters.remove(tag)
        else
            filters[tag] = list
    }

    fun get(request: ContentRequest): UnifiedFilterResponse? {
        // use slightly different contentRequest here:
        //  tags are not really used (just out of convenience)
        //  pageUrl and (request)url are switched
        val contentRequest = ContentRequest(request.pageUrl, request.url, request.type, request.isThirdParty, listOf(""))

        // build list of all matching filters
        val list = mutableListOf<UnifiedFilterResponse>()
        filters[""]?.forEach { if (it.filter.isMatch(contentRequest)) list.add(it) }
        filters[request.pageUrl.host]?.forEach { if (it.filter.isMatch(contentRequest)) list.add(it) }
        if (list.isEmpty()) return null
        if (allSameResponse(list)) return list.first()

        // attempt:
        //  sort list according to ubo criteria and take the first one
        // TODO: test!
        // TODO 2: list is a List<UnifiedFilterResponse>, why do I need to check whether the filters are null? (only in maxOfWith, not in sortWith)
/*        return list.maxOfWith({ f1, f2 ->
            when {
                // first: request (sub)domain matches (filter already matches -> if there is a domain, either domain or subdomain must match)
                    // using contentRequest.pageUrl because it's the actual request url (could also use request.url)
                f1.filter.domains != f2.filter.domains -> getBetterDomainMatch(contentRequest.pageUrl.host ?: "", f1, f2)
                // then: page domain matches, here pattern = page domain (filter already matches -> if there is a non-empty pattern it must match)
                f1.filter.pattern != f2.filter.pattern -> if (f1.filter.pattern.isEmpty()) -1 else 1
                // then: third party matches (filter already matches -> prefer specific 3rd party value (1 or 0) over -1)
                f1.filter.thirdParty != f2.filter.thirdParty -> f1.filter.thirdParty - f2.filter.thirdParty
                // then: content type more precise (filter already matches -> prefer specific (0<value<0xffff) over generic (0xffff), i.e. smaller)
                f1.filter.contentType != f2.filter.contentType -> f2.filter.contentType - f1.filter.contentType
                else -> 0
            }
        }, {it})*/

        // if not working, try
        // interestingly, the same code part as above can't have null filters here
        list.sortWith { f1, f2 ->
            when {
                // first: request (sub)domain matches (filter already matches -> if there is a domain, either domain or subdomain must match)
                    // using contentRequest.pageUrl because it's the actual request url (could also use request.url)
                f1.filter.domains != f2.filter.domains -> getBetterDomainMatch(contentRequest.pageUrl.host ?: "", f1, f2)
                // then: page domain matches, here pattern = page domain (filter already matches -> if there is a non-empty pattern it must match)
                f1.filter.pattern != f2.filter.pattern -> if (f1.filter.pattern.isEmpty()) -1 else 1
                // then: third party matches (filter already matches -> prefer specific 3rd party value (1 or 0) over -1)
                f1.filter.thirdParty != f2.filter.thirdParty -> f1.filter.thirdParty - f2.filter.thirdParty
                // then: content type more precise (filter already matches -> prefer specific (0<value<0xffff) over generic (0xffff), i.e. smaller)
                f1.filter.contentType != f2.filter.contentType -> f2.filter.contentType - f1.filter.contentType
                else -> 0
            }
        }
        return list.first()

        // otherwise try stuff below


        //  have a separate function for each of the checks, otherwise i will recursively have duplicate code

/*
        val list2 = mutableListOf<UnifiedFilterResponse>()

        // first: get matching request domain
        list.forEach { if (it.filter.domains != null) list2.add(it) }
        if (list2.isNotEmpty()) {
            return if (allSameResponse(list2)) list2.first().response
            else {
                // prefer matching subdomain, then matching page domain, then matching content type
                list2.first().response
            }
        }

        // then: get matching page domain
        list.forEach { if (it.filter.pattern != "") list2.add(it) }
        if (list2.isNotEmpty()) {
            return if (allSameResponse(list2)) list2.first().response
            else {
                // prefer matching content type
                list2.first().response
            }
        }

        // then: get matching content type
        list.forEach { if (it.filter.contentType != 0xffff) list2.add(it) }
        if (list2.isNotEmpty()) {
            return if (allSameResponse(list2)) list2.first().response
            else {
                // what do? can this happen?
                list2.first().response
            }
        }

        // nothing should be left, check if this is actually true
        return null
*/
    }

    private fun getBetterDomainMatch(requestDomain: String, f1: UnifiedFilterResponse, f2: UnifiedFilterResponse): Int {
        // domains are not equal, so if one of them is null, the other one is better
        val domains1 = f1.filter.domains ?: return -1
        val domains2 = f2.filter.domains ?: return 1

        // domains SHOULD always have size 1
        //  but better be sure (and might be extended later)
        var compare = 0
        for (i in 0..domains1.size) {
            if (domains1.getKey(i) == requestDomain) ++compare
        }
        for (i in 0..domains2.size) {
            if (domains2.getKey(i) == requestDomain) --compare
        }
        return compare
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