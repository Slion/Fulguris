(function () {
    'use strict';

    //console.log("Set Meta Viewport");

    var width = $width$;
    var metaViewport = document.querySelector('meta[name="viewport"]')
    if (!metaViewport) {
        // No meta viewport on that page, just add one then
        metaViewport = document.createElement("meta");
        metaViewport.setAttribute("name", "viewport");
        document.getElementsByTagName('head')[0].appendChild(metaViewport);
    }

    metaViewport.setAttribute('content', 'width='+ width + ', initial-scale=' + (window.screen.width / width));
}());