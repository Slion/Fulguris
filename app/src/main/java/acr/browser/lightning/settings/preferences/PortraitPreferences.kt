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

