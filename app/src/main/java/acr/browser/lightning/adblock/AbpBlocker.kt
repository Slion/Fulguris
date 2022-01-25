package acr.browser.lightning.adblock

import android.net.Uri
import jp.hazuki.yuzubrowser.adblock.core.ContentRequest
import jp.hazuki.yuzubrowser.adblock.core.FilterContainer
import jp.hazuki.yuzubrowser.adblock.filter.ContentFilter
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.unified.*

class AbpBlocker(
    private val abpUserRules: AbpUserRules?,
    private val filterContainers: Map<String, FilterContainer>
) {

    // return null if ok, BlockerResponse if blocked or modified
    fun shouldBlock(request: ContentRequest): BlockerResponse? {

        // first check user rules, they have highest priority
        abpUserRules?.getResponse(request)?.let { response ->
            // no pattern needed if blocked by user
            return if (response) BlockResponse(USER_BLOCKED, "")
            else null // don't modify anything that is explicitly blocked or allowed by the user?
        }

        // then 'important' filters
        filterContainers[ABP_PREFIX_IMPORTANT_ALLOW]!![request]?.let { return allowOrModify(request) }
        filterContainers[ABP_PREFIX_IMPORTANT]!![request]?.let {
            // https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters#important
            //  -> "The $important modifier will be ignored if a document-level exception rule is applied to the document."
            return if (filterContainers[ABP_PREFIX_ALLOW]!![request]?.let { allowFilter ->
                    allowFilter.contentType and ContentRequest.TYPE_DOCUMENT != 0 && // document-level allowed
                            allowFilter.contentType and ContentRequest.INVERSE == 0 // but not simply everything allowed
                } == true) // we have a document-level exception
                null
            else blockOrRedirect(request, ABP_PREFIX_IMPORTANT, it.pattern)
        }

        // check normal blocklist
        filterContainers[ABP_PREFIX_ALLOW]!![request]?.let { return allowOrModify(request) }
        filterContainers[ABP_PREFIX_DENY]!![request]?.let { return blockOrRedirect(request, ABP_PREFIX_DENY, it.pattern) }

        // not explicitly allowed or blocked
        return allowOrModify(request)
    }

    private fun blockOrRedirect(request: ContentRequest, prefix: String, pattern: String): BlockerResponse {
        val modify = filterContainers[ABP_PREFIX_REDIRECT]!!.getAll(request)
        modify.removeModifyExceptions(request, ABP_PREFIX_REDIRECT_EXCEPTION)
        return if (modify.isEmpty())
            BlockResponse(prefix, pattern)
        else {
            modify.map { (it.modify as RedirectFilter).withPriority() }
                .maxByOrNull { it.second }
                ?.let { BlockResourceResponse(it.first) }
                ?: BlockResponse(prefix, pattern)
        }
    }

    private fun allowOrModify(request: ContentRequest): ModifyResponse? {
        // check whether response should be modified
        // careful: we need to get ALL matching modify filters, not just any (like it's done for block and allow decisions)
        val modifyFilters = filterContainers[ABP_PREFIX_MODIFY]!!.getAll(request)
        if (modifyFilters.isEmpty()) return null

        if (request.url.encodedQuery == null) {
            // if no parameters, remove all removeparam filters
            modifyFilters.removeAll { RemoveparamFilter::class.java.isAssignableFrom(it.modify!!::class.java) }
            if (modifyFilters.isEmpty()) return null
        }
println("allowOrModify: check for ${request.url}")
        println("allowOrModify: filters: ${modifyFilters.size}: ${modifyFilters.map { it.pattern }}")
        modifyFilters.removeModifyExceptions(request, ABP_PREFIX_MODIFY_EXCEPTION)
        println("allowOrModify: filters left after remove modify: ${modifyFilters.size}: ${modifyFilters.map { it.pattern }}")

        // there can be multiple valid filters, and all should be applied if possible
        //  just do one after the other
        //  but since WebResourceRequest can't be changed and returned to WebView, it must all happen within getModifiedResponse()
        return getModifiedResponse(request, modifyFilters.mapNotNull { it.modify }.toMutableList())
    }

    // how exceptions work: (adguard removeparam documentation is useful: https://kb.adguard.com/en/general/how-to-create-your-own-ad-filters)
    //  without parameter (i.e. empty), all filters of that type (removeparam, csp, redirect,...) are invalid
    //  with parameter, only same type and same parameter are considered invalid
    private fun MutableList<ContentFilter>.removeModifyExceptions(request: ContentRequest, prefix: String) {
        val modifyExceptions = filterContainers[prefix]!!.getAll(request)
        println("exceptions: ${modifyExceptions.size}, ${modifyExceptions.map { it.pattern }}")
        println("exception filters: ${filterContainers[prefix]!!.filters().map {it.value.pattern + ", " + it.value.modify!!.parameter + ", " + it.value.javaClass}}")
        modifyExceptions.forEach { exception ->
            forEach {
                println("contentFilter prefix: ${it.modify!!.prefix}, exception filter prefix ${exception.modify!!.prefix}")
                println("parameter is ${it.modify!!.parameter}, ${exception.modify!!.parameter}")
            }
            if (exception.modify!!.parameter == null) // no parameter -> remove all modify of same type (not same prefix because of RemoveParamRegexFilter)
                removeAll { exception.modify!!::class.java.isAssignableFrom(it.modify!!::class.java) }
            // TODO: need to deal with things like contentFilter $removeparam, exception $removeparam=bla
            //  should remove app parameters except bla -> $removeparam=~bla
            //  but this simple thing does not hold, there could be some weird regex combinations
            //  -> getModifedResponse NEEDS the exceptions and "undo" hits if there is an exception
            else // else remove exact matches in modify
                removeAll { it.modify == exception.modify }
        }
    }

    companion object {
        // this needs to be fast, the adguard url tracking list has quite a few options acting on all urls!
        //  e.g. removing the fbclid parameter added by facebook
        private fun getModifiedResponse(request: ContentRequest, filters: MutableList<ModifyFilter>): ModifyResponse? {
            // we can't simply modify the request, so do what the request wants, but modify
            //  then deliver what we got as response
            //  like in https://stackoverflow.com/questions/7610790/add-custom-headers-to-webview-resource-requests-android

            // apply removeparam
            val parameters = getModifiedParameters(request.url, filters)
            filters.removeAll { RemoveparamFilter::class.java.isAssignableFrom(it::class.java) }
            if (parameters == null && filters.isEmpty()) return null

            // apply request header modifying filters
            val requestHeaders = request.headers
            val requestHeaderSize = requestHeaders.size
            filters.forEach {
                if (it is RequestHeaderFilter) {
                    if (it.inverse) // 'inverse' is the same as 'remove' here
                        requestHeaders.removeHeader(it.parameter!!) // can't have null parameter
                    else
                        requestHeaders.addHeader(it.parameter!!)
                }
            }
            filters.removeAll { it is RequestHeaderFilter }
            if (parameters == null && filters.isEmpty() && requestHeaderSize == requestHeaders.size)
                return null

            // gather headers to add/remove from remaining filters
            val addHeaders = mutableMapOf<String, String>()
            val removeHeaders = mutableListOf<String>()
            filters.forEach {
                when (it) {
                    is ResponseHeaderFilter -> {
                        if (it.inverse) // 'inverse' is the same as 'remove' here
                            removeHeaders.add(it.parameter!!) // can't have null parameter
                        else {
                            addHeaders.addHeader(it.parameter!!)
                        }
                    }
                    // else -> what do? this should never happen, maybe log?
                }
            }
            val newUrl = if (parameters == null)
                    request.url.toString()
                else
                    request.url.toString().substringBefore('?').substringBefore('#') + // url without parameters and fragment
                        parameterString(parameters) + // add modified parameters
                        (request.url.fragment?.let {"#$it"} ?: "") // add fragment

            return ModifyResponse(newUrl, request.method, requestHeaders, addHeaders, removeHeaders)
        }

        // applies filters to parameters and returns remaining parameters
        // returns null of parameters are not modified
        private fun getModifiedParameters(url: Uri, filters: List<ModifyFilter>): Map<String, String>? {
            val parameters = url.getQueryParameterMap()
            var changed = false
            filters.forEach { modify ->
                when (modify) {
                    is RemoveparamRegexFilter -> {
                        val regex = modify.regex
                        changed = changed or if (modify.inverse)
                            parameters.entries.retainAll { regex.containsMatchIn(it.key) }
                        else
                            parameters.entries.removeAll { regex.containsMatchIn(it.key) }
                    }
                    is RemoveparamFilter -> {
                        if (modify.parameter == null) // means: remove all parameters
                            return emptyMap()
                        changed = changed or if (modify.inverse)
                            parameters.entries.retainAll { it.key == modify.parameter }
                        else
                            parameters.entries.removeAll { it.key == modify.parameter }
                    }
                }
            }
            return if (changed) parameters else null
        }

        private fun parameterString(parameters: Map<String, String>) =
            if (parameters.isEmpty()) ""
            else "?" + parameters.entries.joinToString("&") { it.key + "=" + it.value }

        // string must look like: User-Agent: Mozilla/5.0
        private fun MutableMap<String, String>.addHeader(headerAndValue: String) =
            addHeader(MapEntry(
                headerAndValue.substringBefore(':').trim(),
                headerAndValue.substringAfter(':').trim()
            ))

        class MapEntry(override val key: String, override val value: String) : Map.Entry<String, String>

        fun MutableMap<String, String>.addHeader(headerAndValue: Map.Entry<String, String>) {
            keys.forEach {
                // header names are case insensitive, but we want to modify as little as possible
                if (it.lowercase() == headerAndValue.key.lowercase()) {
                    put(it, get(it) + "; " + headerAndValue.value)
                    return
                }
            }
            put(headerAndValue.key, headerAndValue.value)
        }

        fun MutableMap<String, String>.removeHeader(header: String) {
            keys.forEach {
                if (it.lowercase() == header.lowercase())
                    remove(it)
            }
        }

        // redirect filters are only applied (or even checked!) if request is blocked!
        //  usually, a matching block filter is created with redirect filter, but not necessarily
        private fun RedirectFilter.withPriority(): Pair<String, Int> {
            val split = parameter!!.indexOf(':')
            return if (split > -1)
                Pair(parameter.substring(0,split), parameter.substring(split+1).toInt())
            else
                Pair(parameter, 0)
        }

        // using query and not decoding is twice as fast
        //  any problems? better leave as is, overall this is so fast at doesn't matter anyway
        // using LinkedHashMap to keep original order
        fun Uri.getQueryParameterMap(): LinkedHashMap<String, String> {
            // using some code from android.net.uri.getQueryParameters()
            val query = encodedQuery ?: return linkedMapOf()
            val parameters = linkedMapOf<String, String>()
            var start = 0
            do {
                val next = query.indexOf('&', start)
                val end = if (next == -1) query.length else next
                var separator = query.indexOf('=', start)
                if (separator > end || separator == -1) {
                    separator = end
                }
                parameters[Uri.decode(query.substring(start, separator))] = // parameter name
                    Uri.decode(
                        query.substring(
                            if (separator < end) separator + 1 else end,
                            end
                        )
                    ) // parameter value
                start = end + 1
            } while (start < query.length)
            return parameters
        }
    }
}
