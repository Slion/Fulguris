package acr.browser.lightning.adblock

import acr.browser.lightning.database.adblock.UserRulesRepository
import android.os.SystemClock
import android.util.Log
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.filter.unified.*
import javax.inject.Singleton

/*
    user rules:
    implements what is called 'dynamic filtering' in ubo: https://github.com/gorhill/uBlock/wiki/Dynamic-filtering:-quick-guide
    uses only 2 types of filters and a special filter container working with pageUrl.host as tag (empty for global rules)
    each rule is a single filter! this is important for easy display of existing filter rules
     -> TODO: test how much slower it is compared to optimized filters
          e.g. global block of example.net and example.com could be one filter with both domains in the domainMap
          but when adding/removing rules, it would be necessary to remove the filter, and add a new one with modified domainMap
          definitely possible, but is it worth the work?
          also content types could be joined
    TODO:
     test how long loading takes, if slow find some solution
     could the db operations for add/remove introduce noticeable delays when done on the wrong thread?
      probably safer to do on IO thread anyway...
 */

@Singleton
class AbpUserRules {

    // inject, or how to do?
    private lateinit var userRulesRepository: UserRulesRepository

    private lateinit var userRules: UserFilterContainer
    init {
//        loadUserLists()
    }

    private fun loadUserLists() {
        val time = SystemClock.elapsedRealtime()
        val ur = userRulesRepository.getAllRules()
        Log.i("yuzu", "getting rules from db took ${SystemClock.elapsedRealtime() - time} ms")

        // careful: the following line crashes android studio:
//        userRules = UserFilterContainer().also { ur.forEach(it::add) } }
        Log.i("yuzu", "loading user rules took ${SystemClock.elapsedRealtime() - time} ms")
    }

    // true: block
    // false: allow
    // null: nothing (relevant to supersede more general block/allow rules)
    fun getResponse(contentRequest: ContentRequest): Boolean? {
        // maybe return filter too?
        return userRules.get(contentRequest)?.response
    }

    fun addUserRule(filter: UnifiedFilterResponse) {
        userRules.add(filter)
        userRulesRepository.addRules(listOf(filter))
    }

    fun removeUserRule(filter: UnifiedFilterResponse) {
        userRules.remove(filter)
        userRulesRepository.removeRule(filter)
    }

    fun createUserFilter(pageDomain: String, requestDomain: String, contentType: Int, thirdParty: Boolean): UnifiedFilter {
        // 'domains' contains (usually 3rd party) domains, but can also be same as pageDomain (or subdomain of pageDomain)
        // include is always set to true (filter valid only on this domain, and any subdomain if there is no more specific rule)
        val domains = if (requestDomain.isNotEmpty())
            SingleDomainMap(true, requestDomain)
        else null

        // thirdParty true means filter only applied to 3rd party content, translates to 1 in the filter
        //  0 would be only first party, -1 is for both
        val thirdPartyInt = if (thirdParty) 1 else -1

        // ContainsFilter for global rules (empty pattern), HostFilter for local rules
        return if (pageDomain.isEmpty())
            ContainsFilter(pageDomain, contentType, domains, thirdPartyInt)
        else
            HostFilter(pageDomain, contentType, false, domains, thirdPartyInt)
    }

}
