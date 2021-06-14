package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.di.injector
import acr.browser.lightning.settings.preferences.UserPreferences
import android.os.Bundle
import javax.inject.Inject

/**
 * The advanced settings of the app.
 */
class AdvancedSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_advanced
    }

    override fun providePreferencesXmlResource() = R.xml.preference_advanced

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)
    }


}
