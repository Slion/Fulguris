package fulguris.js

import com.anthonycr.mezzanine.FileStream

/**
 * Observes changes to theme-color and color-scheme meta tags in the HTML document.
 * Reports changes via console messages that are parsed by WebPageChromeClient.
 */
@FileStream("src/main/js/ThemeColor.js")
interface ThemeColor {
    fun provideJs(): String
}