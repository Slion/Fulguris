package fulguris.activity

import fulguris.AccentTheme
import fulguris.AppTheme
import fulguris.R
import fulguris.extensions.getDrawable
import fulguris.extensions.toBitmap
import fulguris.utils.ThemeUtils
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.StyleRes

//@AndroidEntryPoint
abstract class ThemedActivity : LocaleAwareActivity() {

    // TODO: Do we still need those? Are they working? Do we want to fix them?
    protected var accentId: AccentTheme = userPreferences.useAccent
    protected var themeId: AppTheme = userPreferences.useTheme

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
            AppTheme.DARK -> R.style.Theme_App_Dark
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
        // NOTE: https://github.com/Slion/Fulguris/issues/308
        // Only now call on create which will do Hilt injections
        super.onCreate(savedInstanceState)
        setDefaultTaskDescriptor()
        resetPreferences()
    }

    /**
     *
     */
    private fun setDefaultTaskDescriptor() {
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

        if (userPreferences.taskIcon) {
            //val color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK)
            val color = getColor(R.color.ic_launcher_background)
            val icon = getDrawable(R.drawable.ic_lightning_flavored, android.R.attr.state_enabled).toBitmap(aBackground =  color)
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name),icon, color))
        } else {
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name)))
        }
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
