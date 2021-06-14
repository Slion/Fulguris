package acr.browser.lightning.settings.preferences

import acr.browser.lightning.constant.PrefKeys
import acr.browser.lightning.device.ScreenSize
import acr.browser.lightning.di.PrefsLandscape
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provide access to landscape specific preferences.
 */
@Singleton
class LandscapePreferences @Inject constructor(
    @PrefsLandscape preferences: SharedPreferences,
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
        //@JvmStatic
        val iDefaults = mapOf(
            PrefKeys.HideStatusBar to true,
            PrefKeys.HideToolBar to true,
            PrefKeys.ShowToolBarWhenScrollUp to false,
            PrefKeys.ShowToolBarOnPageTop to true,
            PrefKeys.PullToRefresh to true,
            //PrefKeys.TabBarVertical to !screenSize.isTablet(),
            PrefKeys.ToolbarsBottom to false,
            PrefKeys.DesktopWidth to 1440,
        )
    }

}

