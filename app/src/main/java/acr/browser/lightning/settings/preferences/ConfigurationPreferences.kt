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

import acr.browser.lightning.R
import acr.browser.lightning.constant.PrefKeys
import acr.browser.lightning.device.ScreenSize
import acr.browser.lightning.settings.preferences.delegates.*
import android.content.SharedPreferences

/**
 * Provide access to configuration specific preferences.
 * Portrait, Landscape, what have you…
 * Defaults are not needed here and will be applied by the derived class.
 */
abstract class ConfigurationPreferences constructor(
    preferences: SharedPreferences,
    screenSize: ScreenSize
) : ConfigurationDefaults  {

    val iSharedPrefs = preferences

    /**
     * True if the system status bar should be hidden throughout the app, false if it should be
     * visible.
     */
    var hideStatusBar by preferences.booleanPreference(R.string.pref_key_hide_status_bar, getDefaultBoolean(PrefKeys.HideStatusBar))

    /**
     * True if the browser should hide the navigation bar when scrolling, false if it should be
     * immobile.
     */
    //@Suppress("CALLING_NONFINAL") //TODO Find a way to suppress that warning
    var hideToolBar by preferences.booleanPreference(R.string.pref_key_hide_tool_bar, getDefaultBoolean(PrefKeys.HideToolBar))

    /**
     */
    var showToolBarOnScrollUp by preferences.booleanPreference(R.string.pref_key_show_tool_bar_on_scroll_up, getDefaultBoolean(PrefKeys.ShowToolBarWhenScrollUp))

    /**
     */
    var showToolBarOnPageTop by preferences.booleanPreference(R.string.pref_key_show_tool_bar_on_page_top, getDefaultBoolean(PrefKeys.ShowToolBarOnPageTop))

    /**
     *
     */
    var pullToRefresh by preferences.booleanPreference(R.string.pref_key_pull_to_refresh, getDefaultBoolean(PrefKeys.PullToRefresh))

    /**
     * True if the app should put the tab bar inside a drawer.
     * False will put vertical tab bar beside the tab view.
     */
    var tabBarInDrawer by preferences.booleanPreference(R.string.pref_key_tab_bar_in_drawer)

    /**
     * True if the app should use the navigation drawer UI, false if it should use the traditional
     * desktop browser tabs UI.
     */
    var verticalTabBar by preferences.booleanPreference(R.string.pref_key_tab_bar_vertical, !screenSize.isTablet())

    /*
    var verticalTabBar : Boolean = false
        get() = if (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_PORTRAIT) verticalTabBarInPortrait else verticalTabBarInLandscape
        private set
    */

    /**
     *
     */
     var toolbarsBottom by preferences.booleanPreference(R.string.pref_key_toolbars_bottom, getDefaultBoolean(PrefKeys.ToolbarsBottom))

    /**
     * Define viewport width for desktop mode in portrait
     */
    var desktopWidth by preferences.intPreference(R.string.pref_key_desktop_width, getDefaultInteger(PrefKeys.DesktopWidth))

/*
    fun applyDefaults(aDefaults: Map<String,Any>) {
        aDefaults.keys.forEach { key ->
            if (!iSharedPrefs.contains(key)) {
                // Set our default then
                val value = aDefaults[key];
                if (value is Boolean) {
                    iSharedPrefs.edit().putBoolean(key,value).apply()
                } else if (value is Int) {
                    iSharedPrefs.edit().putInt(key,value).apply()
                }
            }
        }

    }
*/
}

