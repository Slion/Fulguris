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
 * Reads a blob: URL and converts it to a base64 data URL, posting the result
 * back to the native side through the _fulgurisBlobDownload bridge.
 *
 * First checks window._fulgurisBlobStore (populated by BlobHook.js) for a
 * stored Blob reference — this works even if the page has revoked the URL.
 * Falls back to fetch() if no stored blob is available.
 *
 * Expects window._fulgurisBlobUrl to be set before this script is evaluated.
 */
(function() {
    'use strict';
    var url = window._fulgurisBlobUrl;
    if (!url) {
        _fulgurisBlobDownload.onError('No blob URL set in window._fulgurisBlobUrl');
        return;
    }

    function readBlob(blob) {
        var reader = new FileReader();
        reader.onloadend = function() {
            // Clean up stored references to free memory
            if (window._fulgurisBlobStore) delete window._fulgurisBlobStore[url];
            // Pass the filename captured by BlobHook.js (may be empty)
            var name = (window._fulgurisBlobNames && window._fulgurisBlobNames[url]) || '';
            if (window._fulgurisBlobNames) delete window._fulgurisBlobNames[url];
            _fulgurisBlobDownload.onData(reader.result, name);
        };
        reader.onerror = function() {
            _fulgurisBlobDownload.onError('FileReader failed');
        };
        reader.readAsDataURL(blob);
    }

    // Try stored blob first (survives URL.revokeObjectURL)
    var stored = window._fulgurisBlobStore && window._fulgurisBlobStore[url];
    if (stored) {
        readBlob(stored);
        return;
    }

    // Fallback: try fetch() in case the URL is still valid
    fetch(url).then(function(response) {
        if (!response.ok) {
            throw new Error('Fetch returned status ' + response.status);
        }
        return response.blob();
    }).then(function(blob) {
        readBlob(blob);
    }).catch(function(e) {
        _fulgurisBlobDownload.onError('Fetch failed: ' + e.message);
    });
})();
