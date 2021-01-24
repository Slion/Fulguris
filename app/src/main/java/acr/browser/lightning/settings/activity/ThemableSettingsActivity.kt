package acr.browser.lightning.settings.activity

import acr.browser.lightning.AppTheme
import acr.browser.lightning.R
import acr.browser.lightning.di.injector
import acr.browser.lightning.extensions.setStatusBarIconsColor
import acr.browser.lightning.preference.UserPreferences
import acr.browser.lightning.utils.ThemeUtils
import acr.browser.lightning.utils.foregroundColorFromBackgroundColor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import javax.inject.Inject

abstract class ThemableSettingsActivity : AppCompatActivity() {

    protected var themeId: AppTheme = AppTheme.LIGHT

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        injector.inject(this)
        themeId = userPreferences.useTheme

        // set the theme
        applyTheme(themeId)

        super.onCreate(savedInstanceState)

        resetPreferences()
    }


    protected fun applyTheme(themeId: AppTheme) {
        when (themeId) {
            AppTheme.LIGHT -> {
                setTheme(R.style.Theme_App_Light_Settings)
                //window.setBackgroundDrawable(ColorDrawable(ThemeUtils.getPrimaryColor(this)))
            }
            AppTheme.DARK -> {
                setTheme(R.style.Theme_App_Dark_Settings)
                //window.setBackgroundDrawable(ColorDrawable(ThemeUtils.getPrimaryColorDark(this)))
            }
            AppTheme.BLACK -> {
                setTheme(R.style.Theme_App_Black_Settings)
                //window.setBackgroundDrawable(ColorDrawable(ThemeUtils.getPrimaryColorDark(this)))
            }
        }
    }


    private fun resetPreferences() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (userPreferences.useBlackStatusBar) {
                window.statusBarColor = Color.BLACK
            } else {
                window.statusBarColor = ThemeUtils.getStatusBarColor(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Make sure icons have the right color
        setStatusBarIconsColor(foregroundColorFromBackgroundColor(ThemeUtils.getPrimaryColor(this))==Color.BLACK && !userPreferences.useBlackStatusBar)
        resetPreferences()
        if (userPreferences.useTheme != themeId) {
            recreate()
        }
    }

}
