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

package fulguris.activity

import fulguris.AccentTheme
import fulguris.R
import android.os.Bundle

//@AndroidEntryPoint
abstract class ThemedBrowserActivity : ThemedActivity() {

    private var shouldRunOnResumeActions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        //injector.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && shouldRunOnResumeActions) {
            shouldRunOnResumeActions = false
            onWindowVisibleToUserAfterResume()
        }
    }

    override fun onResume() {
        super.onResume()
        resetPreferences()
        shouldRunOnResumeActions = true
        if (themeId != userPreferences.useTheme) {
            restart()
        }

        if (accentId != userPreferences.useAccent) {
            restart()
        }
    }

    override fun accentStyle(accentTheme: AccentTheme): Int? {
        return when (accentTheme) {
            AccentTheme.DEFAULT_ACCENT -> null
            AccentTheme.PINK -> R.style.Accent_Pink
            AccentTheme.PURPLE ->  R.style.Accent_Puple
            AccentTheme.DEEP_PURPLE -> R.style.Accent_Deep_Purple
            AccentTheme.INDIGO -> R.style.Accent_Indigo
            AccentTheme.BLUE -> R.style.Accent_Blue
            AccentTheme.LIGHT_BLUE -> R.style.Accent_Light_Blue
            AccentTheme.CYAN -> R.style.Accent_Cyan
            AccentTheme.TEAL -> R.style.Accent_Teal
            AccentTheme.GREEN -> R.style.Accent_Green
            AccentTheme.LIGHT_GREEN -> R.style.Accent_Light_Green
            AccentTheme.LIME -> R.style.Accent_Lime
            AccentTheme.YELLOW -> R.style.Accent_Yellow
            AccentTheme.AMBER -> R.style.Accent_Amber
            AccentTheme.ORANGE -> R.style.Accent_Orange
            AccentTheme.DEEP_ORANGE -> R.style.Accent_Deep_Orange
            AccentTheme.BROWN -> R.style.Accent_Brown
        }
    }

}
