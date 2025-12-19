/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 */

package fulguris.network

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Network engine that uses OkHttp to handle all network requests.
 * This gives us more control over networking, caching, and connection pooling.
 *
 * **Benefits:**
 * - Works with YouTube and most modern websites
 * - Excellent connection pooling and reuse
 * - Better performance than HttpURLConnection
 * - Supports HTTP/2 and modern protocols
 * - Actively maintained and widely used
 * - User-configurable HTTP disk cache (default 50MB)
 *
 * **Caching:**
 * - Configurable disk cache size via preferences (10-500 MB)
 * - Cache location: app's cache directory (okhttp_cache subfolder)
 * - Respects HTTP caching headers (Cache-Control, ETag, etc.)
 * - LRU eviction policy
 *
 * **Best for:** Advanced users who want better performance and control over networking
 */
class NetworkEngineOkHttp(
    private val context: android.content.Context,
    private val userPreferences: fulguris.settings.preferences.UserPreferences
) : NetworkEngine {

    override val displayName: String = "OkHttp"

    override val id: String = "okhttp"

    private val client: OkHttpClient by lazy {
        // Create cache directory in app's cache folder
        val cacheDir = File(context.cacheDir, "okhttp_cache")

        // Get cache size from preferences (convert MB to bytes)
        val cacheSizeMB = userPreferences.okHttpCacheSize.coerceIn(10, 500).toLong()
        val cacheSizeBytes = cacheSizeMB * 1024L * 1024L

        val cache = Cache(cacheDir, cacheSizeBytes)

        OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun handleRequest(request: WebResourceRequest): WebResourceResponse? {
        try {
            // Build OkHttp request from WebResourceRequest
            val okHttpRequest = Request.Builder()
                .url(request.url.toString())
                .method(request.method, null) // TODO: Handle POST body if needed
                .apply {
                    // Copy headers from WebResourceRequest
                    request.requestHeaders?.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            // Execute request
            val response = client.newCall(okHttpRequest).execute()

            if (!response.isSuccessful) {
                Timber.w("OkHttp request failed: ${response.code} ${request.url}")
                return null // Let WebView handle failures
            }

            // Extract response data
            val body = response.body
            val contentType = response.header("Content-Type")
            val encoding = contentType?.let { extractCharset(it) } ?: "UTF-8"
            val mimeType = contentType?.let { extractMimeType(it) } ?: "text/plain"

            // Build response headers map
            val responseHeaders = mutableMapOf<String, String>()
            response.headers.forEach { (name, value) ->
                responseHeaders[name] = value
            }

            // Create WebResourceResponse
            return WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                response.message,
                responseHeaders,
                body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
            )

        } catch (e: Exception) {
            Timber.e(e, "OkHttp engine error for ${request.url}")
            // Return null to let WebView handle the request normally
            return null
        }
    }

    override fun onDeselected() {
        // Clean up OkHttp client resources
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up OkHttp client")
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

