package fulguris.js

import com.anthonycr.mezzanine.FileStream

/**
 * Detects whether a touch event targets a nested CSS scrollable element.
 * Notifies the native side via the _fulgurisScroll JavaScript interface
 * so that pull-to-refresh can be suppressed when appropriate.
 */
@FileStream("src/main/js/NestedScrollDetect.js")
interface NestedScrollDetect {
    fun provideJs(): String
}
