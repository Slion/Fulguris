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

(function () {
    'use strict';

    var width = $width$;
    var metaViewport = document.querySelector('meta[name="viewport"]')
    if (!metaViewport) {
        // No meta viewport on that page, just add one then
        metaViewport = document.createElement("meta");
        metaViewport.setAttribute("name", "viewport");
        document.getElementsByTagName('head')[0].appendChild(metaViewport);
    }

    if (metaViewport.hasAttribute('data-fulguris')) {
        // We already set it
        console.log("Fulguris: meta viewport already set");
    } else {
        console.log("Fulguris: Set Meta Viewport");
        console.log("Fulguris: window.screen.width: " + window.screen.width);
        console.log("Fulguris: window.innerWidth: " + innerWidth);
        // Dump our page source code
        // We used this to check that our HTML page already available
        //console.log(document.documentElement.outerHTML);

        // Only fiddle with that once
        metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        //metaViewport.setAttribute('content', 'width='+ width);
        //metaViewport.setAttribute('content', 'width=device-width');
        metaViewport.setAttribute('data-fulguris', 'desktop-mode');
    }

    // Reapply our meta viewport again whenever our page is resized
    // That was needed at least for Google search result page, not sure why though
    window.addEventListener('resize', (event) => {
        console.log("Fulguris: window resized: " + window.innerWidth);
        //metaViewport.setAttribute('content', 'width='+ width);
        metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.innerWidth / width));
        //metaViewport.setAttribute('content', 'width=device-width');
    });

}());