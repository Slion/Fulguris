package acr.browser.lightning

import acr.browser.lightning.locale.LocaleAwareActivity
import acr.browser.lightning.utils.ThemeUtils
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.StyleRes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

//@AndroidEntryPoint
abstract class ThemedActivity : LocaleAwareActivity() {

    // TODO reduce protected visibility

    protected var accentId: AccentTheme = AccentTheme.DEFAULT_ACCENT
    protected var themeId: AppTheme = AppTheme.LIGHT
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
    abstract fun themeStyle(aTheme: AppTheme): Int

    @StyleRes
    protected abstract fun accentStyle(accentTheme: AccentTheme): Int?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeId = userPreferences.useTheme
        accentId = userPreferences.useAccent

        // set the theme
        applyTheme(provideThemeOverride()?:themeId)
        applyAccent()

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
     *
     */
    protected fun applyTheme(themeId: AppTheme) {
        setTheme(themeStyle(themeId))
        // Check if we have a dark theme
        val mode = resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
        isDarkTheme = themeId == AppTheme.BLACK // Black qualifies as dark theme
                || themeId == AppTheme.DARK // Dark is indeed a dark theme
                // Check if we are using system default theme and it is currently set to dark
                || (themeId == AppTheme.DEFAULT && mode == Configuration.UI_MODE_NIGHT_YES)
    }

    /**
     *
     */
    private fun applyAccent() {
        accentStyle(accentId)?.let { setTheme(it) }
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
