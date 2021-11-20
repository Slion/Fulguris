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

package acr.browser.lightning.settings.activity

import acr.browser.lightning.AccentTheme
import acr.browser.lightning.AppTheme
import acr.browser.lightning.R
import acr.browser.lightning.ThemedActivity
import acr.browser.lightning.extensions.setStatusBarIconsColor
import acr.browser.lightning.utils.ThemeUtils
import acr.browser.lightning.utils.foregroundColorFromBackgroundColor
import android.graphics.Color


abstract class ThemedSettingsActivity : ThemedActivity() {

    override fun onResume() {
        super.onResume()
        // Make sure icons have the right color
        window.setStatusBarIconsColor(foregroundColorFromBackgroundColor(ThemeUtils.getPrimaryColor(this)) == Color.BLACK && !userPreferences.useBlackStatusBar)
        resetPreferences()
        if (userPreferences.useTheme != themeId) {
            recreate()
        }

        if (userPreferences.useAccent != accentId) {
            recreate()
        }
    }

    /**
     * From ThemedActivity
     */
    override fun themeStyle(aTheme: AppTheme): Int {
        return when (aTheme) {
            AppTheme.LIGHT -> R.style.Theme_App_Light_Settings
            AppTheme.DARK ->  R.style.Theme_App_Dark_Settings
            AppTheme.BLACK -> R.style.Theme_App_Black_Settings
            AppTheme.DEFAULT -> R.style.Theme_App_DayNight_Settings
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
