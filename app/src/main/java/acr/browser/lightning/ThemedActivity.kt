package acr.browser.lightning

import acr.browser.lightning.di.HiltEntryPoint
import acr.browser.lightning.extensions.setTaskLabel
import acr.browser.lightning.extensions.toBitmap
import acr.browser.lightning.locale.LocaleAwareActivity
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.ThemeUtils
import android.app.ActivityManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.EntryPointAccessors
import fulguris.app
import fulguris.app
import timber.log.Timber

//@AndroidEntryPoint
abstract class ThemedActivity : LocaleAwareActivity() {
    /**
     We need to get our Theme before calling onCreate for settings theme to work.
     However onCreate does the Hilt injections so we did not have access to [LocaleAwareActivity.userPreferences] early enough.
     Fortunately we can access our Hilt entry point early as shown below.
     TODO: Move this in the base class after migrating it to Kotlin.
     */
    private val hiltEntryPoint = EntryPointAccessors.fromApplication(app, HiltEntryPoint::class.java)
    protected val quickUserPrefs: UserPreferences = hiltEntryPoint.userPreferences
    // TODO reduce protected visibility
    protected var accentId: AccentTheme = quickUserPrefs.useAccent
    protected var themeId: AppTheme = quickUserPrefs.useTheme

    /**
     * Override this to provide an alternate theme that should be set for every instance of this
     * activity regardless of the user's preference.
     */
    protected open fun provideThemeOverride(): AppTheme? = null

    protected open fun provideAccentThemeOverride(): AccentTheme? = null

    /**
     * Called after the activity is resumed
     * and the UI becomes visible to the user.
     * Called by onWindowFocusChanged only if
     * onResume has been called.
     */
    protected open fun onWindowVisibleToUserAfterResume() = Unit

    /**
     * Implement this to provide themes resource style ids.
     */
    @StyleRes
    fun themeStyle(aTheme: AppTheme): Int {
        return when (aTheme) {
            AppTheme.LIGHT -> R.style.Theme_App_Light
            AppTheme.DARK ->  R.style.Theme_App_Dark
            AppTheme.BLACK -> R.style.Theme_App_Black
            AppTheme.DEFAULT -> R.style.Theme_App_DayNight
        }
    }

    @StyleRes
    protected open fun accentStyle(accentTheme: AccentTheme): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the theme before onCreate otherwise settings are broken
        // That's apparently not an issue specific to Fulguris
        applyTheme(provideThemeOverride()?:themeId)
        applyAccent()
        // Make sure we reset task description when an activity is created
        //setTaskLabel(getString(R.string.app_name))
        // Looks like the new API has no effect Samsung on Tab S8 so weird
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            Timber.v("setTaskDescription")
//            setTaskDescription(ActivityManager.TaskDescription.Builder()
//                .setLabel(getString(R.string.app_name))
//                .setBackgroundColor(color)
//                .setIcon(R.drawable.ic_lightning)
//                .build())
//        }

        if (quickUserPrefs.taskIcon) {
            val color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK)
            val icon = ResourcesCompat.getDrawable(resources, R.mipmap.ic_launcher, theme)!!.toBitmap(256,256)
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name),icon, color))
        } else {
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name)))
        }


        // NOTE: https://github.com/Slion/Fulguris/issues/308
        // Only now call on create which will do Hilt injections
        super.onCreate(savedInstanceState)
        resetPreferences()
    }

    /**
     *
     */
    protected fun resetPreferences() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (userPreferences.useBlackStatusBar) {
                window.statusBarColor = Color.BLACK
            } else {
                window.statusBarColor = ThemeUtils.getStatusBarColor(this)
            }
        }
    }

    /**
     * Private because one should use [provideThemeOverride] to set our theme.
     * Changing it during the lifetime of the activity or after super.[onCreate] call is not working properly.
     */
    private fun applyTheme(themeId: AppTheme) {
        setTheme(themeStyle(themeId))
    }

    /**
     *
     */
    private fun applyAccent() {
        //accentStyle(accentId)?.let { setTheme(it) }
    }

    /**
     * Using this instead of recreate() because it does not work when handling resource changes I guess.
     */
    protected fun restart() {
        finish()
        startActivity(Intent(this, javaClass))
    }

    /**
     * See [LocaleAwareActivity.onLocaleChanged]
     */
    override fun onLocaleChanged() {
        restart()
    }
}
