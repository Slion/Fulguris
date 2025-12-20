/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 */

package fulguris.network

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import timber.log.Timber

/**
 * Interface for network engines that can handle network requests
 * in shouldInterceptRequest instead of letting WebView handle them.
 */
interface NetworkEngine {

    /**
     * Human-readable name to display in settings UI.
     * Examples: "WebView", "OkHttp", "Cronet"
     */
    val displayName: String

    /**
     * Unique identifier for this engine implementation.
     * Used for storing user preference.
     */
    val id: String

    /**
     * Get the cache directory used by this engine.
     * Returns null if this engine doesn't use a cache directory.
     */
    fun getCacheDir(): java.io.File? = null

    /**
     * Handle a network request and optionally provide a custom response.
     *
     * @param request The WebResourceRequest to handle
     * @return WebResourceResponse if this engine handles the request,
     *         null to let WebView handle it normally
     */
    fun handleRequest(request: WebResourceRequest): WebResourceResponse?

    /**
     * Check if this engine can handle the given request.
     * Default implementation checks for HTTP/HTTPS schemes and delegates POST/PUT/PATCH to WebView.
     *
     * @param request The WebResourceRequest to check
     * @return true if this engine can handle the request, false to delegate to WebView
     */
    fun canHandleRequest(request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Only handle http and https schemes, let WebView handle others (file://, data://, etc.)
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }

        // WebView API limitation: We cannot access POST body, so delegate to WebView
        // This ensures forms, login, and API calls work correctly
        when (request.method.uppercase()) {
            "POST", "PUT", "PATCH" -> {
                Timber.v("Delegating ${request.method} request to WebView: ${request.url}")
                return false
            }
        }

        return true
    }

    /**
     * Process Set-Cookie headers from response and set them in WebView's CookieManager.
     * CRITICAL: When we intercept requests, WebView doesn't automatically process Set-Cookie headers.
     * We must manually set them for authentication/sessions to work.
     *
     * @param url The response URL (used as the cookie domain)
     * @param setCookieHeaders List of Set-Cookie header values
     */
    fun processCookies(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return

        val cookieManager = CookieManager.getInstance()

        setCookieHeaders.forEach { cookie ->
            try {
                cookieManager.setCookie(url, cookie)
                Timber.v("Set cookie for $url: ${cookie.substringBefore(';')}")
            } catch (e: Exception) {
                Timber.e(e, "Error setting cookie: $cookie")
            }
        }

        // Flush cookies to persistent storage
        cookieManager.flush()
    }

    /**
     * Get standard HTTP reason phrase for a status code.
     * HTTP/2 responses may have empty message, but Android requires non-empty reason phrase.
     */
    fun getDefaultReasonPhrase(code: Int): String {
        return when (code) {
            200 -> "OK"
            201 -> "Created"
            202 -> "Accepted"
            204 -> "No Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            307 -> "Temporary Redirect"
            308 -> "Permanent Redirect"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Unknown"
        }
    }

    /**
     * Called when this engine is selected by the user.
     * Use this to initialize any resources needed.
     */
    fun onSelected() {}

    /**
     * Called when this engine is deselected by the user.
     * Use this to clean up any resources.
     */
    fun onDeselected() {}

    /**
     * Get the maximum configured cache size in bytes.
     * Returns 0 if this engine doesn't use a cache.
     */
    fun cacheMaxSize(): Long = 0L

    /**
     * Get the currently used cache size in bytes.
     * Returns 0 if this engine doesn't use a cache.
     */
    fun cacheUsedSize(): Long = 0L

    /**
     * Clear the cache for this engine.
     * Returns true if cache was cleared successfully, false otherwise.
     */
    fun clearCache(): Boolean = false

    /**
     * Check if this engine supports caching.
     */
    fun supportsCache(): Boolean = false
}

