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

package fulguris.settings.preferences

import fulguris.constant.PrefKeys
import fulguris.device.ScreenSize
import fulguris.di.PrefsPortrait
import android.content.SharedPreferences
import fulguris.enums.CutoutMode
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

    override fun getDefaultFloat(aKey: String) : Float {
        return LandscapePreferences.iDefaults[aKey] as Float
    }

    override fun getDefaultCutoutMode(): CutoutMode {
        return CutoutMode.Default
    }

    override fun getDefaults(): Map<String, Any> {
        return iDefaults
    }

    companion object {
        // Define our defaults
        // Needs to be static as it is accessed by the base class constructor through the virtual functions above
        val iDefaults = mapOf(
            PrefKeys.HideStatusBar to false,
            PrefKeys.HideToolBar to false,
            PrefKeys.ShowToolBarWhenScrollUp to false,
            PrefKeys.ShowToolBarOnPageTop to true,
            PrefKeys.PullToRefresh to true,
            PrefKeys.ToolbarsBottom to false,
            PrefKeys.DesktopWidth to 200F
            // Omitted the following as they have non static default values specified in the base class
            //PrefKeys.TabBarVertical to !screenSize.isTablet(),
            //PrefKeys.TabBarInDrawer to !screenSize.isTablet(),
        )
    }


}

