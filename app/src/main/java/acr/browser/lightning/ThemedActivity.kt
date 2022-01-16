package acr.browser.lightning

import acr.browser.lightning.di.HiltEntryPoint
import acr.browser.lightning.locale.LocaleAwareActivity
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.ThemeUtils
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.StyleRes
import dagger.hilt.android.EntryPointAccessors

//@AndroidEntryPoint
abstract class ThemedActivity : LocaleAwareActivity() {
    /**
     We need to get our Theme before calling onCreate for settings theme to work.
     However onCreate does the Hilt injections so we did not have access to [LocaleAwareActivity.userPreferences] early enough.
     Fortunately we can access our Hilt entry point early as shown below.
     TODO: Move this in the base class after migrating it to Kotlin.
     */
    private val hiltEntryPoint = EntryPointAccessors.fromApplication(BrowserApp.instance.applicationContext, HiltEntryPoint::class.java)
    protected val quickUserPrefs: UserPreferences = hiltEntryPoint.userPreferences
    // TODO reduce protected visibility
    protected var accentId: AccentTheme = quickUserPrefs.useAccent
    protected var themeId: AppTheme = quickUserPrefs.useTheme
    private var isDarkTheme: Boolean = false
    val useDarkTheme get() = isDarkTheme

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
        // Check if we have a dark theme
        isDarkTheme = isDarkTheme(themeId)
    }

    /**
     * Tells if the given [themeId] is dark. Takes into account current system theme if needed.
     * Works even before calling supper.[onCreate].
     */
    protected fun isDarkTheme(themeId: AppTheme) : Boolean {
        val mode = resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
        return themeId == AppTheme.BLACK // Black qualifies as dark theme
                || themeId == AppTheme.DARK // Dark is indeed a dark theme
                // Check if we are using system default theme and it is currently set to dark
                || (themeId == AppTheme.DEFAULT && mode == Configuration.UI_MODE_NIGHT_YES)

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
