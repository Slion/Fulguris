/**
 * Theme Color Observer for Fulguris Web Browser
 *
 * This script monitors and reports changes to theme-color and color-scheme meta tags
 * in HTML documents. It detects:
 * - Initial meta tag values on page load
 * - Changes to existing meta tag content attributes
 * - Newly added meta tags to the document
 *
 * All detected values are reported via console.debug() with the prefix 'fulguris:'
 * which are then parsed by WebPageChromeClient.onConsoleMessage() on the Android side.
 *
 * Usage:
 * This script is used by Fulguris's Color Mode feature to dynamically adapt the browser UI
 * to match the website's theme. When a website specifies theme-color or color-scheme meta tags,
 * the browser can apply these colors to the toolbar, status bar, and other UI elements,
 * creating a seamless visual experience between the web content and the browser chrome.
 *
 * The script is injected into web pages when userPreferences.colorModeEnabled is true,
 * and runs during the onProgressChanged phase (after 10% page load) in WebPageChromeClient.
 * 
 * Message format:
 * - "fulguris: meta-theme-color: <color-value>"
 * - "fulguris: meta-color-scheme: <scheme-value>"
 */
(function() {
    'use strict';
    
    // Get current theme-color and color-scheme
    var metaThemeColor = document.querySelector('meta[name="theme-color"]');
    var metaColorScheme = document.querySelector('meta[name="color-scheme"]');
    var currentThemeColor = metaThemeColor ? metaThemeColor.content : null;
    var currentColorScheme = metaColorScheme ? metaColorScheme.content : null;

    // Send initial values via console
    if (currentThemeColor) {
        console.debug('fulguris: meta-theme-color: ' + currentThemeColor);
    }
    if (currentColorScheme) {
        console.debug('fulguris: meta-color-scheme: ' + currentColorScheme);
    }

    /**
     * Attaches a MutationObserver to a meta tag node to watch for content changes.
     * @param {Node} node - The meta tag element to observe
     */
    function observeMetaTag(node) {
        if (node && node.nodeType === Node.ELEMENT_NODE) {
            attributeObserver.observe(node, { attributes: true, attributeFilter: ['content'] });
        }
    }

    /**
     * Monitors attribute changes on meta tags, specifically the 'content' attribute.
     * Reports changes to theme-color and color-scheme via console.debug.
     */
    var attributeObserver = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            if (mutation.type === 'attributes' && mutation.attributeName === 'content') {
                var tagName = mutation.target.getAttribute('name');
                var newValue = mutation.target.content;
                if (tagName === 'theme-color') {
                    console.debug('fulguris: meta-theme-color: ' + newValue);
                } else if (tagName === 'color-scheme') {
                    console.debug('fulguris: meta-color-scheme: ' + newValue);
                }
            }
        });
    });

    /**
     * Monitors DOM changes in document.head to detect newly added meta tags.
     * When theme-color or color-scheme meta tags are added, reports them and starts observing.
     */
    var headObserver = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            mutation.addedNodes.forEach(function(node) {
                if (node.nodeName === 'META') {
                    var tagName = node.getAttribute('name');
                    if (tagName === 'theme-color' || tagName === 'color-scheme') {
                        var newValue = node.content;
                        if (tagName === 'theme-color') {
                            console.debug('fulguris: meta-theme-color: ' + newValue);
                        } else if (tagName === 'color-scheme') {
                            console.debug('fulguris: meta-color-scheme: ' + newValue);
                        }
                        observeMetaTag(node);
                    }
                }
            });
        });
    });

    // Start observing existing meta tags
    if (metaThemeColor) {
        observeMetaTag(metaThemeColor);
    }
    if (metaColorScheme) {
        observeMetaTag(metaColorScheme);
    }

    // Observe document.head for new meta tags
    if (document.head) {
        headObserver.observe(document.head, { childList: true, subtree: true });
    }
})();
