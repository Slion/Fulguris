package acr.browser.lightning.settings.activity

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
}
