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
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

{
    'use strict';
    // Just call our entry point
    main();

    /**
     * This script entry point
     */
    function main() {
        // Desired viewport width in percentage of the actual viewport width
        var width = $width$; // Replaced from our Kotlin code with desired percentage of actual width
        // We used this to check that our HTML page already available
        //log(document.documentElement.outerHTML);

        // Create our own meta viewport element
        var metaViewport = document.createElement("meta");
        metaViewport.setAttribute("name", "viewport");
        // Support dealing with multiple meta viewport elements, that's notably the case on vimeo.com
        var metaViewports = document.querySelectorAll('meta[name="viewport"]');
        // Remove all existing meta viewport elements
        metaViewports.forEach((aMetaViewport) => {
          //console.log("Fulguris: remove meta viewport: " + aMetaViewport.outerHTML);
          if (aMetaViewport.hasAttribute('data-fulguris')) {
            // We already injected our own meta viewport, don't remove it
            metaViewport = aMetaViewport
            log("Fulguris: found our meta viewport");
          } else {
            // That meta viewport is not ours, remove it
            aMetaViewport.remove();
          }
        });

        // Fetch our HTML head element
        var head = document.getElementsByTagName('head')[0]
        if (head==null) {
            // No head element to inject our meta viewport, bail out then
            log("Fulguris: document has no head yet");
            return;
        }

        // Check if that meta viewport is ours
        if (metaViewport.hasAttribute('data-fulguris')) {
            // We already did our thing, bail out then
            log("Fulguris: meta viewport already set");
            return;
        }

        // Only fiddle with that once
        //metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        metaViewport.setAttribute('content', metaViewportContent(width));
        // Mark this meta element as being from us and remember the original width
        metaViewport.setAttribute('data-fulguris', window.innerWidth);
        // Add meta viewport element to our DOM
        head.appendChild(metaViewport);
    }

    /**
     * Construct meta viewport content from provided width percentage by computing width and scale.
     * @param aWidth The viewport width we should use in percentage of our actual width.
     */
    function metaViewportContent(aWidth) {
        // Compute our width in device independent pixels (DIP) from its percentage our our original width
        var computedWidth = (window.innerWidth * aWidth) / 100;
        // Compute our scale to fit our content to our page
        var scale = window.innerWidth / computedWidth;
        // Some debug logs
        log("Fulguris: width input: " + aWidth);
        log("Fulguris: window.innerWidth: " + window.innerWidth);
        log("Fulguris: computed width: " + computedWidth);
        log("Fulguris: scale: " + scale);
        // Construct meta viewport content attribute
        return 'width=' + computedWidth + ', user-scalable=1, initial-scale=' + scale;
    }

    /**
     * Allow us to easily disable our logs.
     * @param aString String to log
     */
    function log(aString) {
        console.log(aString);
    }

}