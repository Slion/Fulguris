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

import fulguris.R
import fulguris.constant.PrefKeys
import fulguris.device.ScreenSize
import android.content.SharedPreferences
import fulguris.settings.preferences.delegates.booleanPreference
import fulguris.settings.preferences.delegates.enumPreference
import fulguris.settings.preferences.delegates.floatPreference

/**
 * Base class that provides access to configuration specific preferences.
 * Derived class notably includes Portrait and Landscape variants.
 */
abstract class ConfigurationPreferences constructor(
    val preferences: SharedPreferences,
    screenSize: ScreenSize
) : ConfigurationDefaults {

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
    var tabBarInDrawer by preferences.booleanPreference(R.string.pref_key_tab_bar_in_drawer, !screenSize.isTablet())

    /**
     * True if the app should use the navigation drawer UI, false if it should use the traditional
     * desktop browser tabs UI.
     */
    var verticalTabBar by preferences.booleanPreference(R.string.pref_key_tab_bar_vertical, !screenSize.isTablet())

    /**
     *
     */
     var toolbarsBottom by preferences.booleanPreference(R.string.pref_key_toolbars_bottom, getDefaultBoolean(PrefKeys.ToolbarsBottom))

    /**
     * Define viewport width for desktop mode. Expressed in percentage of the actual viewport width.
     * When set to 100% we use actual viewport width, the HTML page is not tempered with.
     * When set to something other than 100% we will enable wide viewport mode and inject JS code to set HTML meta viewport element accordingly.
     */
    var desktopWidth by preferences.floatPreference(R.string.pref_key_desktop_width, getDefaultFloat(PrefKeys.DesktopWidth))


    /**
     * Define if we render around display cutouts.
     *
     * See: https://developer.android.com/reference/android/view/WindowManager.LayoutParams
     * See: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
     */
    var cutoutMode by preferences.enumPreference(R.string.pref_key_cutout_mode, getDefaultCutoutMode())

}

