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
 * Default network engine that lets WebView handle all networking.
 * This is the standard behavior - returns null to allow WebView's built-in networking.
 */
class NetworkEngineWebView : NetworkEngine {

    override val displayName: String = "WebView"

    override val id: String = "webview"

    override fun handleRequest(request: WebResourceRequest): WebResourceResponse? {
        // Return null to let WebView handle networking with its built-in mechanisms
        return null
    }
}

