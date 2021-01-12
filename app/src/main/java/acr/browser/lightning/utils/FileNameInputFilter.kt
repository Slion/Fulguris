package acr.browser.lightning.utils

import acr.browser.lightning.BrowserApp
import acr.browser.lightning.R
import acr.browser.lightning.extensions.toast
import android.text.InputFilter
import android.text.Spanned

/**
 * An input filter which can be attached to an EditText widget to filter out invalid filename characters
 * See: https://stackoverflow.com/a/28516488/3969362
 */
class FileNameInputFilter: InputFilter
{
    override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence? {
        if (source.isNullOrBlank()) {
            return null
        }

        // See: https://stackoverflow.com/a/2703882/3969362
        val reservedChars = "?:\"*|/\\<>\u0000"
        // Extract actual source
        val actualSource = source.subSequence(start, end)
        // Filter out unsupported characters
        val filtered = actualSource.filter { c -> reservedChars.indexOf(c) == -1 }
        // Check if something was filtered out
        return if (actualSource.length != filtered.length) {
            // Something was caught by our filter, provide visual feedback
                if (actualSource.length - filtered.length == 1) {
                    // A single character was removed
                    BrowserApp.instance.applicationContext.toast(R.string.invalid_character_removed)
                } else {
                    // Multiple characters were removed
                    BrowserApp.instance.applicationContext.toast(R.string.invalid_characters_removed)
                }
            // Provide filtered results then
            filtered
        } else {
            // Nothing was caught in our filter
            null
        }
    }
}