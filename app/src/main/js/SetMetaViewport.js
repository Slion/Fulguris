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

    //var skipNextResize = false;

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
          //log("remove meta viewport: " + aMetaViewport.outerHTML);
          if (aMetaViewport.hasAttribute('data-fulguris')) {
            // We already injected our own meta viewport, don't remove it
            metaViewport = aMetaViewport
            log("found our meta viewport");
          } else {
            // That meta viewport is not ours, remove it
            log("remove page viewport");
            aMetaViewport.remove();
          }
        });

        // Fetch our HTML head element
        var head = document.getElementsByTagName('head')[0]
        if (head==null) {
            // No head element to inject our meta viewport, bail out then
            log("document has no head yet");
            return;
        }

        var widthOrg = window.innerWidth;

        // Check if that meta viewport is ours
        if (metaViewport.hasAttribute('data-fulguris')) {
            // We already did our thing, bail out then
            log("meta viewport already set");
            widthOrg = metaViewport.getAttribute('data-fulguris');
            log("set it again anyway");
            // We notably can't return here for walmart.com otherwise desktop mode ain't working for some reason
            //return;
        }

        // Only fiddle with that once
        //metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        metaViewport.setAttribute('content', metaViewportContent(width, widthOrg));
        // Mark this meta element as being from us and remember the original width
        // By saving the original width we avoid growing on every resize
        // The downside of that is that if there is a genuine activity resize user will need to reload the page for desktop mode to update
        // TODO: Could we somehow distinguish genuine resize from resize we did trigger and then actually update our original size
        metaViewport.setAttribute('data-fulguris', widthOrg);
        // Add meta viewport element to our DOM
        // Setting this will trigger a resize event
        head.appendChild(metaViewport);
    }

    /**
     * Construct meta viewport content from provided width percentage by computing width and scale.
     * @param aWidth The viewport width we should use in percentage of our actual width.
     */
    function metaViewportContent(aWidthPercent, aWidth) {
        // Compute our width in device independent pixels (DIP) from its percentage of our original width
        var computedWidth = (aWidth * aWidthPercent) / 100;
        // Compute our scale to fit our content to our page
        var scale = aWidth / computedWidth;
        // Some debug logs
        log("width percentage: " + aWidthPercent);
        log("window.innerWidth: " + window.innerWidth);
        log("width original: " + aWidth);
        log("computed width: " + computedWidth);
        log("scale: " + scale);
        // Construct meta viewport content attribute
        return 'width=' + computedWidth + ', user-scalable=1, initial-scale=' + scale;
    }

    /**
     * Allow us to easily disable our logs.
     * @param aString String to log
     */
    function log(aString) {
        console.log("Fulguris: " + aString);
    }

    // Reapply our meta viewport again whenever our page is resized
    // That was needed at least for Google search result page, not sure why though
    window.addEventListener('resize', (event) => {
        log("window resized: " + window.innerWidth);
        //metaViewport.setAttribute('content', 'width='+ width);
        //metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        //metaViewport.setAttribute('content', 'width=device-width');
        main()
    });


    window.addEventListener('load', (event) => {
        log("window load: " + window.innerWidth);
        //metaViewport.setAttribute('content', 'width='+ width);
        //metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        //metaViewport.setAttribute('content', 'width=device-width');
        main()
    });

}