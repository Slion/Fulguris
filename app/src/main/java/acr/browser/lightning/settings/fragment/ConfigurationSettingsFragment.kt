package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.di.injector
import acr.browser.lightning.preference.UserPreferences
import android.os.Bundle
import javax.inject.Inject

/**
 * Configuration settings of the app.
 */
class ConfigurationSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_configuration

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

    }
}
