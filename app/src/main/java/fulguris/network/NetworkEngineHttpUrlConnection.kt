/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 */

package fulguris.network

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Network engine that uses HttpURLConnection to handle all network requests.
 * This is Java's standard HTTP client - simple, lightweight, and available on all Android versions.
 *
 * **Known Limitations:**
 * - Does not work with YouTube (use OkHttp or WebView instead)
 * - No HTTP/2 support (only HTTP/1.1)
 * - Limited connection pooling compared to OkHttp
 * - May have issues with some modern websites that require advanced HTTP features
 * - **No HTTP caching configured** (could be added with HttpResponseCache)
 * - POST/PUT/PATCH requests are delegated to WebView (Android API doesn't provide request body)
 *
 * **Caching:**
 * - No cache currently configured
 * - Every request hits the network (slower performance)
 * - Could add HttpResponseCache if needed
 *
 * **Limitations:**
 * - POST/PUT/PATCH requests are delegated to WebView (Android API doesn't provide request body)
 * - Only GET, HEAD, DELETE, and OPTIONS requests are handled by HttpURLConnection
 *
 * **Best for:** Simple websites, testing, or when you want minimal dependencies
 */
class NetworkEngineHttpUrlConnection : NetworkEngine {

    override val displayName: String = "HttpURLConnection"

    override val id: String = "httpurlconnection"

    override fun handleRequest(request: WebResourceRequest): WebResourceResponse? {
        var connection: HttpURLConnection? = null
        try {
            // Check if we can handle this request (scheme and method checks)
            if (!canHandleRequest(request)) {
                return null
            }

            val urlString = request.url.toString()

            // Open connection
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection

            // Set request method (GET/HEAD/DELETE/OPTIONS)
            connection.requestMethod = request.method

            // Set timeouts
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 30000 // 30 seconds

            // Set whether to follow redirects
            connection.instanceFollowRedirects = true

            // Copy headers from WebResourceRequest
            request.requestHeaders?.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            // Connect and get response
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode < 200 || responseCode >= 400) {
                Timber.w("HttpURLConnection request failed: $responseCode ${request.url}")
                return null // Let WebView handle failures
            }

            // Extract response data
            val contentType = connection.contentType
            val encoding = contentType?.let { extractCharset(it) } ?: "UTF-8"
            val mimeType = contentType?.let { extractMimeType(it) } ?: "text/plain"

            // Build response headers map
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields.forEach { (key, values) ->
                // Skip null keys (status line) and empty value lists
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.joinToString(", ")
                }
            }

            // Get input stream
            val inputStream = try {
                connection.inputStream
            } catch (e: Exception) {
                // If we can't get the input stream, return empty response
                ByteArrayInputStream(ByteArray(0))
            }

            // Process Set-Cookie headers using base interface method
            val setCookieHeaders = connection.headerFields["Set-Cookie"]
            if (!setCookieHeaders.isNullOrEmpty()) {
                processCookies(request.url.toString(), setCookieHeaders.filterNotNull())
            }

            // Create WebResourceResponse
            // HTTP/2 responses may have empty message, but Android requires non-empty reason phrase
            val reasonPhrase = connection.responseMessage?.ifEmpty { getDefaultReasonPhrase(responseCode) }
                ?: getDefaultReasonPhrase(responseCode)

            return WebResourceResponse(
                mimeType,
                encoding,
                responseCode,
                reasonPhrase,
                responseHeaders,
                inputStream
            )

        } catch (e: Exception) {
            Timber.e(e, "HttpURLConnection engine error for ${request.url}")
            // Return null to let WebView handle the request normally
            return null
        } finally {
            // Note: We don't disconnect here because the inputStream is still being read
            // The connection will be automatically closed when the stream is closed
        }
    }

    /**
     * Extract MIME type from Content-Type header.
     * Example: "text/html; charset=utf-8" -> "text/html"
     */
    private fun extractMimeType(contentType: String): String {
        return contentType.split(";").firstOrNull()?.trim() ?: "text/plain"
    }

    /**
     * Extract charset from Content-Type header.
     * Example: "text/html; charset=utf-8" -> "utf-8"
     */
    private fun extractCharset(contentType: String): String? {
        return contentType.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
    }
}

