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

    // Desired viewport width in percentage of the actual viewport width
    var width = $width$;
    // Support dealing with multiple meta viewport elements
    // That's notably the case on vimeo.com
    var metaViewports = document.querySelectorAll('meta[name="viewport"]');
    // Remove all existing meta viewport elements
    metaViewports.forEach((aMetaViewport) => {
      //console.log("Fulguris: remove meta viewport: " + aMetaViewport.outerHTML);
      aMetaViewport.remove();
    });

    //console.log("Fulguris: width input: " + width);
    //console.log("Fulguris: create new meta viewport");
    // Create our own meta viewport element
    var metaViewport = document.createElement("meta");
    metaViewport.setAttribute("name", "viewport");
    // Defensive
    if (metaViewport.hasAttribute('data-fulguris')) {
        // We already set it
        //console.log("Fulguris: meta viewport already set");
    } else {
        //console.log("Fulguris: Set Meta Viewport");
        //console.log("Fulguris: window.screen.width: " + window.screen.width);
        //console.log("Fulguris: window.innerWidth: " + window.innerWidth);
        // Dump our page source code
        // We used this to check that our HTML page already available
        //console.log(document.documentElement.outerHTML);

        // Only fiddle with that once
        //metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        metaViewport.setAttribute('content', metaViewportContent(width));
        //metaViewport.setAttribute('content', 'width=device-width');
        metaViewport.setAttribute('data-fulguris', 'desktop-mode');
    }

    // Add meta viewport element to our DOM
    document.getElementsByTagName('head')[0].appendChild(metaViewport);

    // Reapply our meta viewport again whenever our page is resized
    // That was needed at least for Google search result page, not sure why though
    window.addEventListener('resize', (event) => {
        //console.log("Fulguris: window resized: " + window.innerWidth);
        metaViewport.setAttribute('content', metaViewportContent(width));
    });

    /**
    */
    function metaViewportContent(aWidth) {
        var computedWidth = (window.innerWidth * aWidth) / 100;
        //console.log("Fulguris: width input: " + aWidth);
        //console.log("Fulguris: window.innerWidth: " + window.innerWidth);
        //console.log("Fulguris: computed width: " + computedWidth);
        return 'width=' + computedWidth + ', user-scalable=1';
    }

}