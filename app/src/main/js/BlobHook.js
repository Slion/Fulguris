/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Nicolo Cozzolino (nicolo.cozzolino@gmail.com).
 * Portions created by the Initial Developer are Copyright (C) 2023 the Initial Developer.
 * All Rights Reserved.
 *
 * Contributor(s):
 */

/**
 * Hooks URL.createObjectURL so that every Blob passed to it is stored in a
 * global map keyed by the resulting blob: URL.  This allows BlobDownload.js
 * to read the Blob even after the page revokes the URL.
 *
 * Also hooks HTMLAnchorElement.prototype.click to capture the HTML5 "download"
 * attribute from programmatically-clicked anchors with blob: hrefs. Many sites
 * (GitHub, etc.) create a temporary <a> in JS, set href and download, call
 * .click(), then remove it — this hook catches that pattern.
 *
 * Hooks Response.prototype.blob to:
 * - For attachment responses: show a download confirmation dialog BEFORE the
 *   body is fetched, so the user can cancel/confirm immediately.
 * - For other large responses (>1 MB): track download progress via the
 *   page-load progress bar.
 *
 * Must be injected once per page load (e.g. in onPageFinished) BEFORE any
 * site script creates blob downloads.
 */
(function() {
    'use strict';
    if (window._fulgurisBlobStore) return;
    window._fulgurisBlobStore = {};
    window._fulgurisBlobNames = {};
    var origCreate = URL.createObjectURL.bind(URL);
    var origRevoke = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = function(obj) {
        var url = origCreate(obj);
        if (obj instanceof Blob) {
            window._fulgurisBlobStore[url] = obj;
        }
        return url;
    };
    URL.revokeObjectURL = function(url) {
        origRevoke(url);
    };

    // -- Response.prototype.blob hook ------------------------------------------

    var origBlob = Response.prototype.blob;
    var SIZE_THRESHOLD = 1048576; // 1 MB
    var confirmCounter = 0;

    /** Read the response body with progress reporting. */
    function pumpWithProgress(resp, total) {
        try { _fulgurisBlobDownload.onProgress(0, total); } catch(e) {}
        var reader = resp.body.getReader();
        var chunks = [];
        var loaded = 0;
        function pump() {
            return reader.read().then(function(result) {
                if (result.done) {
                    try { _fulgurisBlobDownload.onProgress(total, total); } catch(e) {}
                    var type = resp.headers.get('content-type') || '';
                    return new Blob(chunks, type ? {type: type} : undefined);
                }
                chunks.push(result.value);
                loaded += result.value.length;
                try { _fulgurisBlobDownload.onProgress(loaded, total); } catch(e) {}
                return pump();
            });
        }
        return pump();
    }

    /** Extract a filename from Content-Disposition header or URL path. */
    function extractFilename(disp, url) {
        if (disp) {
            // Try filename*= (RFC 5987) first, then filename=
            var match = /filename\*=[^']*'[^']*'([^\s;]+)/i.exec(disp);
            if (match) try { return decodeURIComponent(match[1]); } catch(e) {}
            match = /filename\s*=\s*"([^"]+)"/i.exec(disp);
            if (match) return match[1];
            match = /filename\s*=\s*([^\s;]+)/i.exec(disp);
            if (match) return match[1];
        }
        if (url) {
            try {
                var path = new window.URL(url).pathname;
                var name = decodeURIComponent(path.substring(path.lastIndexOf('/') + 1));
                if (name && name.indexOf('.') !== -1) return name;
            } catch(e) {}
        }
        return '';
    }

    Response.prototype.blob = function() {
        var resp = this;
        var total = parseInt(resp.headers.get('content-length') || '0', 10);
        var disp = resp.headers.get('content-disposition') || '';
        var isAttachment = disp.toLowerCase().indexOf('attachment') !== -1;

        // Small non-attachment — pass through.
        // (CORS often hides Content-Disposition, so we also gate on size.)
        if (!isAttachment && total < SIZE_THRESHOLD) {
            return origBlob.call(resp);
        }

        // No readable body stream — fall back to original implementation.
        if (!resp.body) return origBlob.call(resp);

        // Large response OR explicit attachment — show confirmation dialog
        // BEFORE the body is fetched so the user can cancel immediately.
        var filename = extractFilename(disp, resp.url);
        var contentType = resp.headers.get('content-type') || 'application/octet-stream';

        return new Promise(function(resolve, reject) {
            var cbId = '_bc' + (++confirmCounter);
            window[cbId] = function(confirmed) {
                delete window[cbId];
                if (!confirmed) {
                    try { resp.body.cancel(); } catch(e) {}
                    // Return an empty blob so the page doesn't crash on TypeError
                    resolve(new Blob([]));
                    return;
                }
                pumpWithProgress(resp, total).then(resolve, reject);
            };
            _fulgurisBlobDownload.onConfirmDownload(cbId, filename, '' + total, contentType);
        });
    };

    // -- Anchor click hooks ----------------------------------------------------

    // Hook HTMLAnchorElement.click() to capture the download attribute from
    // programmatically triggered blob downloads (the most common pattern).
    var origClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function() {
        var href = this.href;
        if (href && href.indexOf('blob:') === 0 && this.download) {
            window._fulgurisBlobNames[href] = this.download;
            // Notify native side so the download dialog can show the real filename.
            // This fires synchronously before the WebView download listener callback.
            _fulgurisBlobDownload.onFilename(href, this.download);
        }
        return origClick.apply(this, arguments);
    };
    // Also listen for real user clicks on anchors as a fallback.
    document.addEventListener('click', function(e) {
        var el = e.target;
        while (el && el.tagName !== 'A') el = el.parentElement;
        if (!el) return;
        var href = el.href;
        if (href && href.indexOf('blob:') === 0 && el.download) {
            window._fulgurisBlobNames[href] = el.download;
            _fulgurisBlobDownload.onFilename(href, el.download);
        }
    }, true);
})();
