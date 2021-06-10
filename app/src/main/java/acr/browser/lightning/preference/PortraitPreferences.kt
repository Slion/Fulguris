package acr.browser.lightning.preference

import acr.browser.lightning.AppTheme
import acr.browser.lightning.BrowserApp
import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.browser.ProxyChoice
import acr.browser.lightning.browser.SearchBoxDisplayChoice
import acr.browser.lightning.browser.SearchBoxModel
import acr.browser.lightning.constant.DEFAULT_ENCODING
import acr.browser.lightning.constant.Uris
import acr.browser.lightning.constant.PrefKeys
import acr.browser.lightning.device.ScreenSize
import acr.browser.lightning.di.PrefsLandscape
import acr.browser.lightning.di.PrefsPortrait
import acr.browser.lightning.di.UserPrefs
import acr.browser.lightning.preference.delegates.*
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.search.engine.GoogleSearch
import acr.browser.lightning.settings.NewTabPosition
import acr.browser.lightning.utils.FileUtils
import acr.browser.lightning.view.RenderingMode
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
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

