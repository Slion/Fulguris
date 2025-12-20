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
 * - Configurable disk cache size via preferences
 * - Cache location: app's cache directory (okhttp_cache subfolder)
 * - Respects HTTP caching headers (Cache-Control, ETag, etc.)
 * - LRU eviction policy
 *
 * **Limitations:**
 * - POST/PUT/PATCH requests are delegated to WebView (Android API doesn't provide request body)
 * - Only GET, HEAD, DELETE, and OPTIONS requests are handled by OkHttp
 *
 * **Best for:** Advanced users who want better performance and control over networking
 */
class NetworkEngineOkHttp(
    private val context: android.content.Context,
    private val userPreferences: fulguris.settings.preferences.UserPreferences
) : NetworkEngine {

    override val displayName: String = "OkHttp"

    override val id: String = "okhttp"

    override fun getCacheDir(): File = File(context.cacheDir, "okhttp_cache")

    @Volatile
    private var client: OkHttpClient? = null

    /**
     * Get or create the OkHttp client with current cache settings
     */
    private fun getClient(): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: createClient().also { client = it }
        }
    }

    /**
     * Create a new OkHttp client with current cache size from preferences
     */
    private fun createClient(): OkHttpClient {
        // Get cache size from preferences (convert MB to bytes)
        // Parse string to int with default fallback
        // No upper limit here - validator enforces max based on available space
        val cacheSizeMB = userPreferences.networkCacheSize.toIntOrNull()?.coerceAtLeast(0)?.toLong() ?: 50L
        val cacheSizeBytes = cacheSizeMB * 1024L * 1024L

        Timber.i("Creating OkHttp client with cache size: $cacheSizeMB MB ($cacheSizeBytes bytes)")
        Timber.i("Cache directory: ${getCacheDir().absolutePath}")

        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        // Only add cache if size > 0
        if (cacheSizeBytes > 0) {
            val cache = Cache(getCacheDir(), cacheSizeBytes)
            builder.cache(cache)
            Timber.i("OkHttp cache enabled with max size: $cacheSizeBytes bytes")
        } else {
            Timber.i("OkHttp cache disabled (size = 0)")
        }

        return builder.build()
    }

    /**
     * Recreate the OkHttp client with updated cache size.
     * Call this when the cache size preference changes.
     */
    fun recreateClient() {
        val clientToClose = synchronized(this) {
            val old = client
            // Clear reference so it will be recreated on next request
            client = null
            old
        }

        Timber.i("OkHttp client will be recreated with new cache size on next request")

        // Close old client resources on background thread to avoid NetworkOnMainThreadException
        if (clientToClose != null) {
            Thread {
                try {
                    clientToClose.apply {
                        dispatcher.executorService.shutdown()
                        connectionPool.evictAll()
                        cache?.close()
                    }
                    Timber.d("Old OkHttp client cleaned up successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up old OkHttp client")
                }
            }.start()
        }
    }

    override fun handleRequest(request: WebResourceRequest): WebResourceResponse? {
        try {
            // Check if we can handle this request (scheme and method checks)
            if (!canHandleRequest(request)) {
                return null
            }

            val url = request.url.toString()

            // Build OkHttp request from WebResourceRequest
            val requestBuilder = Request.Builder().url(url)

            // Copy headers from WebResourceRequest
            request.requestHeaders?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // For GET/HEAD/DELETE/OPTIONS, we can handle them (no body needed)
            val okHttpRequest = requestBuilder.method(request.method, null).build()

            // Execute request
            val response = getClient().newCall(okHttpRequest).execute()

            // Log cache hit/miss for debugging
            val cacheResponse = response.cacheResponse
            val networkResponse = response.networkResponse
            if (cacheResponse != null) {
                Timber.v("Cache HIT: ${request.url}")
            } else if (networkResponse != null) {
                Timber.v("Cache MISS: ${request.url}")
            }

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

            // Process Set-Cookie headers using base interface method
            val setCookieHeaders = response.headers("Set-Cookie")
            if (setCookieHeaders.isNotEmpty()) {
                processCookies(response.request.url.toString(), setCookieHeaders)
            }

            // Create WebResourceResponse
            // HTTP/2 responses may have empty message, but Android requires non-empty reason phrase
            val reasonPhrase = response.message.ifEmpty { getDefaultReasonPhrase(response.code) }

            return WebResourceResponse(
                mimeType,
                encoding,
                response.code,
                reasonPhrase,
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
        // Clean up OkHttp client resources on background thread to avoid NetworkOnMainThreadException
        val clientToClose = client
        client = null

        if (clientToClose != null) {
            // Execute cleanup in background thread
            Thread {
                try {
                    clientToClose.apply {
                        dispatcher.executorService.shutdown()
                        connectionPool.evictAll()
                        cache?.close()
                    }
                    Timber.i("OkHttp client cleaned up successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up OkHttp client")
                }
            }.start()
        }
    }

    override fun supportsCache(): Boolean = true

    override fun cacheMaxSize(): Long {
        return try {
            // Get the max size from the OkHttp cache
            getClient().cache?.maxSize() ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Error getting max cache size")
            0L
        }
    }

    override fun cacheUsedSize(): Long {
        return try {
            val client = getClient()
            val cache = client.cache

            if (cache == null) {
                Timber.w("OkHttp cache is null! Client was not initialized with cache.")
                return 0L
            }

            // Flush the cache to ensure all pending writes are complete
            cache.flush()

            // Get the actual used size from OkHttp cache
            val usedSize = cache.size()
            val maxSize = cache.maxSize()

            Timber.d("Cache used size: $usedSize bytes (max: $maxSize bytes)")
            Timber.d("Cache directory: ${getCacheDir().absolutePath}, exists: ${getCacheDir().exists()}")

            usedSize
        } catch (e: Exception) {
            Timber.e(e, "Error calculating cache size")
            0L
        }
    }

    override fun clearCache(): Boolean {
        return try {
            // First, try to evict the cache through OkHttp API
            getClient().cache?.evictAll()

            // Then delete the cache directory to ensure everything is cleared
            val cacheDir = getCacheDir()
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }

            Timber.i("OkHttp cache cleared successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error clearing OkHttp cache")
            false
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

