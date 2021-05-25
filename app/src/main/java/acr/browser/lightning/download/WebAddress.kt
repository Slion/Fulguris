/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.download

import android.util.Patterns
import java.util.*
import java.util.regex.Pattern

/**
 * Web Address Parser
 *
 *
 * This is called WebAddress, rather than URL or URI, because it attempts to
 * parse the stuff that a user will actually type into a browser address widget.
 *
 *
 * Unlike java.net.uri, this parser will not choke on URIs missing schemes. It
 * will only throw a ParseException if the input is really hosed.
 *
 *
 * If given an https scheme but no port, fills in port
 */
internal class WebAddress(address: String?) {
    var scheme: String?
    var host: String
    var port: Int
    var path: String
    private var authInfo: String
    override fun toString(): String {
        var port = ""
        if (this.port != 443 && "https" == scheme || this.port != 80 && "http" == scheme) {
            port = ':'.toString() + this.port.toString()
        }
        var authInfo = ""
        if (authInfo.isNotEmpty()) {
            authInfo = this.authInfo + '@'
        }
        return "$scheme://$authInfo$host$port$path"
    }

    companion object {
        private const val string: String = "_"
        private const val MATCH_GROUP_SCHEME = 1
        private const val MATCH_GROUP_AUTHORITY = 2
        private const val MATCH_GROUP_HOST = 3
        private const val MATCH_GROUP_PORT = 4
        private const val MATCH_GROUP_PATH = 5

        @Suppress("DEPRECATION")
        private val sAddressPattern = Pattern.compile( /* scheme */
                "(?:(http|https|file)://)?" +  /* authority */
                        "(?:([-A-Za-z0-9$string.+!*'(),;?&=]+(?::[-A-Za-z0-9$string.+!*'(),;?&=]+)?)@)?" +  /* host */
                        "([" + Patterns.GOOD_IRI_CHAR + "%_-][" + Patterns.GOOD_IRI_CHAR + "%_\\.-]*|\\[[0-9a-fA-F:\\.]+\\])?" +  /* port */
                        "(?::([0-9]*))?" +  /* path */
                        "(/?[^#]*)?" +  /* anchor */
                        ".*", Pattern.CASE_INSENSITIVE)
    }

    /**
     * Parses given URI-like string.
     */
    init {
        requireNotNull(address) { "address can't be null" }
        scheme = ""
        host = ""
        port = -1
        path = "/"
        authInfo = ""
        val m = sAddressPattern.matcher(address)
        require(m.matches()) { "Parsing of address '$address' failed" }
        var t: String? = m.group(MATCH_GROUP_SCHEME)
        if (t != null) {
            scheme = t.lowercase(Locale.ROOT)
        }
        t = m.group(MATCH_GROUP_AUTHORITY)
        if (t != null) {
            authInfo = t
        }
        t = m.group(MATCH_GROUP_HOST)
        if (t != null) {
            host = t
        }
        t = m.group(MATCH_GROUP_PORT)
        if (t != null && t.isNotEmpty()) {
            // The ':' character is not returned by the regex.
            try {
                port = t.toInt()
            } catch (ex: NumberFormatException) {
                throw RuntimeException("Parsing of port number failed", ex)
            }
        }
        t = m.group(MATCH_GROUP_PATH)
        if (t != null && t.isNotEmpty()) {
            /*
             * handle busted myspace frontpage redirect with missing initial "/"
             */
            path = if (t[0] == '/') {
                t
            } else {
                "/$t"
            }
        }

        /*
         * Get port from scheme or scheme from port, if necessary and possible
         */if (port == 443 && scheme != null && scheme!!.isEmpty()) {
            scheme = "https"
        } else if (port == -1) {
            port = if ("https" == scheme) {
                443
            } else {
                80 // default
            }
        }
        if (scheme != null && scheme!!.isEmpty()) {
            scheme = "http"
        }
    }
}