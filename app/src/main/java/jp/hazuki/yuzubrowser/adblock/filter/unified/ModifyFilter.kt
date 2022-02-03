package jp.hazuki.yuzubrowser.adblock.filter.unified

import jp.hazuki.yuzubrowser.adblock.filter.abp.*

// this is NOT a unified filter, it's an optional part of it
abstract class ModifyFilter(val parameter: String?, val inverse: Boolean) {
    abstract val prefix: Char
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        other as ModifyFilter
        if (prefix != other.prefix) return false
        if (parameter != other.parameter) return false
        return inverse == other.inverse
    }

    override fun hashCode(): Int {
        var result = parameter.hashCode()
        result = 31 * result + prefix.hashCode()
        result = 31 * result + inverse.hashCode()
        return result
    }

    companion object {

        val REMOVEHEADER_NOT_ALLOWED = """
        access-control-allow-origin
        access-control-allow-credentials
        access-control-allow-headers
        access-control-allow-methods
        access-control-expose-headers
        access-control-max-age
        access-control-request-headers
        access-control-request-method
        origin
        timing-allow-origin
        allow
        cross-origin-embedder-policy
        cross-origin-opener-policy
        cross-origin-resource-policy
        content-security-policy
        content-security-policy-report-only
        expect-ct
        feature-policy
        origin-isolation
        strict-transport-security
        upgrade-insecure-requests
        x-content-type-options
        x-download-options
        x-frame-options
        x-permitted-cross-domain-policies
        x-powered-by
        x-xss-protection
        public-key-pins
        public-key-pins-report-only
        sec-websocket-key
        sec-websocket-extensions
        sec-websocket-accept
        sec-websocket-protocol
        sec-websocket-version
        p3p
        sec-fetch-mode
        sec-fetch-dest
        sec-fetch-site
        sec-fetch-user
        referrer-policy
        content-type
        content-length
        accept
        accept-encoding
        host
        connection
        transfer-encoding
        upgrade
    """.trimIndent().split("\n")

        val RESPONSEHEADER_ALLOWED = """
        location
        refresh
        report-to
        set-cookie
    """.trimIndent().split("\n")

    }
}

open class RemoveparamFilter(parameter: String?, inverse: Boolean): ModifyFilter(parameter, inverse) {
    override val prefix get() = MODIFY_PREFIX_REMOVEPARAM
}

class RemoveparamRegexFilter(parameter: String, inverse: Boolean): RemoveparamFilter(parameter, inverse) {
    override val prefix get() = MODIFY_PREFIX_REMOVEPARAM_REGEX
    val regex = parameter.toRegex()
}

class RedirectFilter(parameter: String?): ModifyFilter(parameter, false) {
    override val prefix get() = MODIFY_PREFIX_REDIRECT
}

class ResponseHeaderFilter(header: String, remove: Boolean): ModifyFilter(header, remove) {
    override val prefix get() = MODIFY_PREFIX_RESPONSE_HEADER
}

class RequestHeaderFilter(header: String, remove: Boolean): ModifyFilter(header, remove) {
    override val prefix get() = MODIFY_PREFIX_REQUEST_HEADER
}

fun getRemoveparamFilter(parameter: String, inverse: Boolean) =
    if (parameter.startsWith('/') && parameter.endsWith('/'))
        RemoveparamRegexFilter(parameter.drop(1).dropLast(1), inverse)
    else
        RemoveparamFilter(parameter, inverse)
