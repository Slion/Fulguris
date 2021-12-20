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

package acr.browser.lightning.settings.preferences

import acr.browser.lightning.constant.PrefKeys
import acr.browser.lightning.device.ScreenSize
import acr.browser.lightning.di.PrefsPortrait
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provide access to portrait specific preferences.
 */
@Singleton
 class PortraitPreferences @Inject constructor(
    @PrefsPortrait preferences: SharedPreferences,
    screenSize: ScreenSize
) : ConfigurationPreferences(preferences, screenSize) {

    // Looks like this is the right way to go


    override fun getDefaultBoolean(aKey: String) : Boolean {
        return iDefaults[aKey] as Boolean
    }

    override fun getDefaultInteger(aKey: String) : Int {
        return iDefaults[aKey] as Int
    }

    override fun getDefaults(): Map<String, Any> {
        return iDefaults
    }

    companion object {
        // Needs to be static as it is accessed by the base class constructor through virtual functions
        val iDefaults = mapOf(
            PrefKeys.HideStatusBar to false,
            PrefKeys.HideToolBar to false,
            PrefKeys.ShowToolBarWhenScrollUp to false,
            PrefKeys.ShowToolBarOnPageTop to true,
            PrefKeys.PullToRefresh to true,
            //PrefKeys.TabBarVertical to !screenSize.isTablet(),
            PrefKeys.ToolbarsBottom to false,
            PrefKeys.DesktopWidth to 1024,
        )
    }

}

