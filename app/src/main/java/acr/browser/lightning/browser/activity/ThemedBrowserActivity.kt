package acr.browser.lightning.browser.activity

import acr.browser.lightning.AppTheme
import acr.browser.lightning.R
import acr.browser.lightning.ThemedActivity
import acr.browser.lightning.di.injector
import android.content.Intent
import android.os.Bundle


abstract class ThemedBrowserActivity : ThemedActivity() {

    private var showTabsInDrawer: Boolean = false
    private var shouldRunOnResumeActions = false


    override fun onCreate(savedInstanceState: Bundle?) {
        injector.inject(this)
        showTabsInDrawer = userPreferences.showTabsInDrawer
        super.onCreate(savedInstanceState)
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && shouldRunOnResumeActions) {
            shouldRunOnResumeActions = false
            onWindowVisibleToUserAfterResume()
        }
    }


    override fun onResume() {
        super.onResume()
        resetPreferences()
        shouldRunOnResumeActions = true
        val drawerTabs = userPreferences.showTabsInDrawer
        if (themeId != userPreferences.useTheme || showTabsInDrawer != drawerTabs) {
            restart()
        }
    }

    /**
     * Using this instead of recreate() because it does not work when handling resource changes I guess.
     */
    protected fun restart() {
        finish()
        startActivity(Intent(this, javaClass))
    }

    /**
     * From ThemedActivity
     */
    override fun themeStyle(aTheme: AppTheme): Int {
        return when (aTheme) {
            AppTheme.LIGHT -> R.style.Theme_App_Light
            AppTheme.DARK ->  R.style.Theme_App_Dark
            AppTheme.BLACK -> R.style.Theme_App_Black
            AppTheme.DEFAULT -> R.style.Theme_App_DayNight
        }
    }

}
