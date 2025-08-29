package fulguris.js

import com.anthonycr.mezzanine.FileStream

/**
 * Reads the theme color from the DOM.
 * TODO: Not sure we use that I think we just embedded the JS in code
 * Search for: evaluateJavascript: theme color extraction
 */
@FileStream("src/main/js/ThemeColor.js")
interface ThemeColor {
    fun provideJs(): String
}