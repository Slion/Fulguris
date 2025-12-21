package fulguris.extensions

import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

val String.reverseDomainName get() = split('.').reversed().joinToString(".")

/**
 * Extract the top private domain (eTLD+1) from a domain string.
 * For example: "www.example.com" -> "example.com", "mail.google.co.uk" -> "google.co.uk"
 * See DomainPreferences.topPrivateDomain
 * Not sure why we could not use PublicSuffix.getEffectiveTldPlusOne it looked like it worked for IP addresses too.
 * Maybe it was fixed in the library since.
 */
val String.topPrivateDomain: String?
    get() = if (isNotEmpty()) {
        try {
            "http://$this".toHttpUrl().topPrivateDomain()
        } catch (ex: Exception) {
            Timber.w(ex, "Failed to extract top private domain from: $this")
            null
        }
    } else null

/**
 * Converts an origin URL string to a domain string by:
 * - Removing scheme (https://, http://)
 * - Removing port number
 * - Removing trailing slash
 *
 * Example: "https://example.com:8080/" -> "example.com"
 */
fun String.originToDomain(): String {
    return this.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore(":")
        .removeSuffix("/")
}

