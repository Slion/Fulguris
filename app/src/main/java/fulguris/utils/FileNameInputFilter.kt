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
 
package fulguris.utils

import fulguris.app
import fulguris.R
import fulguris.extensions.toast
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
                    app.toast(R.string.invalid_character_removed)
                } else {
                    // Multiple characters were removed
                    app.toast(R.string.invalid_characters_removed)
                }
            // Provide filtered results then
            filtered
        } else {
            // Nothing was caught in our filter
            null
        }
    }
}