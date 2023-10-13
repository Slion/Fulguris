/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package fulguris.activity

import fulguris.di.HiltEntryPoint
import fulguris.di.UserPrefs
import fulguris.locale.LocaleUtils
import fulguris.settings.preferences.UserPreferences
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import dagger.hilt.android.EntryPointAccessors
import fulguris.app
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

//@AndroidEntryPoint
abstract class LocaleAwareActivity :
    AppCompatActivity() {
    @Volatile
    private var mLastLocale: Locale? = null

    /**
    We need to get our Theme before calling onCreate for settings theme to work.
    However onCreate does the Hilt injections so we did not have access to [LocaleAwareActivity.userPreferences] early enough.
    Fortunately we can access our Hilt entry point early as shown below.
     */
    private val hiltEntryPoint = EntryPointAccessors.fromApplication(app, HiltEntryPoint::class.java)
    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences

    @UserPrefs
    @Inject
    lateinit var userSharedPreferences: SharedPreferences

    /**
     * Is called whenever the application locale has changed. Your Activity must either update
     * all localised Strings, or replace itself with an updated version.
     */
    abstract fun onLocaleChanged()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mLastLocale = fulguris.locale.LocaleUtils.requestedLocale(userPreferences.locale)
        fulguris.locale.LocaleUtils.updateLocale(this, mLastLocale)
        setLayoutDirection(window.decorView, mLastLocale)
    }

    /**
     * Upon configuration change our new config is reset to system locale.
     * Locale.geDefault is also reset to system local apparently.
     * That's also true if locale was previously change on the application context.
     * Therefore we don't bother with application context for now.
     *
     * @param newConfig
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        val requestedLocale = fulguris.locale.LocaleUtils.requestedLocale(userPreferences.locale)
        Timber.v("Config changed - Last locale: $mLastLocale")
        Timber.v("Config changed - Requested locale: $requestedLocale")
        Timber.v("Config changed - New config locale (ignored): " + newConfig.locale)

        // Check if our request local was changed
        if (requestedLocale == mLastLocale) {
            // Requested locale is the same make sure we apply it anew as it was reset in our new config
            fulguris.locale.LocaleUtils.updateLocale(this, mLastLocale)
            setLayoutDirection(window.decorView, mLastLocale)
        } else {
            // Requested locale was changed, we will need to restart our activity then
            localeChanged(requestedLocale)
        }
        super.onConfigurationChanged(newConfig)
    }

    /**
     *
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //onConfigurationChanged(getResources().getConfiguration());
    }

    /**
     *
     * @param aNewLocale
     */
    private fun localeChanged(aNewLocale: Locale) {
        Timber.v("Apply locale: $aNewLocale")
        mLastLocale = aNewLocale
        onLocaleChanged()
    }

    override fun onResume() {
        super.onResume()
        val requestedLocale = fulguris.locale.LocaleUtils.requestedLocale(userPreferences.locale)
        Timber.v("Resume - Last locale: $mLastLocale")
        Timber.v("Resume - Requested locale: $requestedLocale")

        // Check if locale was changed as we were paused, apply new locale as needed
        if (requestedLocale != mLastLocale) {
            localeChanged(requestedLocale)
        }
    }

    companion object {
        private const val TAG = "LocaleAwareActivity"

        /**
         * Force set layout direction to RTL or LTR by Locale.
         *
         * @param view
         * @param locale
         */
        fun setLayoutDirection(view: View?, locale: Locale?) {
            when (TextUtilsCompat.getLayoutDirectionFromLocale(locale)) {
                ViewCompat.LAYOUT_DIRECTION_RTL -> ViewCompat.setLayoutDirection(view!!, ViewCompat.LAYOUT_DIRECTION_RTL)
                ViewCompat.LAYOUT_DIRECTION_LTR -> ViewCompat.setLayoutDirection(view!!, ViewCompat.LAYOUT_DIRECTION_LTR)
                else -> ViewCompat.setLayoutDirection(view!!, ViewCompat.LAYOUT_DIRECTION_LTR)
            }
        }
    }
}