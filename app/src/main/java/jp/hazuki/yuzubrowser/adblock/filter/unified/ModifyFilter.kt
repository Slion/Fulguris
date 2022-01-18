package jp.hazuki.yuzubrowser.adblock.filter.unified

import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_CSP
import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_REDIRECT
import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_REMOVEHEADER
import jp.hazuki.yuzubrowser.adblock.filter.abp.MODIFY_PREFIX_REMOVEPARAM

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
        return (parameter + prefix + inverse).hashCode()
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
    override val prefix = MODIFY_PREFIX_REMOVEPARAM
}

class RemoveparamRegexFilter(parameter: String?, inverse: Boolean): RemoveparamFilter(parameter, inverse)

class RedirectFilter(parameter: String?): ModifyFilter(parameter, false) {
    override val prefix = MODIFY_PREFIX_REDIRECT
}

class CspFilter(parameter: String?): ModifyFilter(parameter, false) {
    override val prefix = MODIFY_PREFIX_CSP
}

class RemoveHeaderFilter(parameter: String, request: Boolean): ModifyFilter(parameter, request) {
    override val prefix = MODIFY_PREFIX_REMOVEHEADER
}

fun getRemoveparamFilter(parameter: String, inverse: Boolean) =
    if (parameter.startsWith('/'))
        RemoveparamRegexFilter(parameter, inverse)
    else
        RemoveparamFilter(parameter, inverse)

