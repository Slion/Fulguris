/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 */

package fulguris.network

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

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

