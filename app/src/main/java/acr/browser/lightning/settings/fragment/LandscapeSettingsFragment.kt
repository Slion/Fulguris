package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.di.injector
import acr.browser.lightning.settings.preferences.ConfigurationPreferences
import acr.browser.lightning.settings.preferences.LandscapePreferences
import acr.browser.lightning.utils.landscapeSharedPreferencesName
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import javax.inject.Inject

/**
 * Landscape settings configuration screen.
 * Notably use the correct shared preferences file rather than the default one.
 */
class LandscapeSettingsFragment : ConfigurationSettingsFragment() {

    @Inject internal lateinit var landscapePreferences: LandscapePreferences

    override fun providePreferencesXmlResource() = R.xml.preference_configuration
    override fun configurationPreferences() : ConfigurationPreferences = landscapePreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        injector.inject(this)
        // That's the earliest place we can change our preference file as earlier in onCreate the manager has not been created yet
        preferenceManager.sharedPreferencesName = landscapeSharedPreferencesName(requireContext())
        preferenceManager.sharedPreferencesMode = MODE_PRIVATE
        super.onCreatePreferences(savedInstanceState,rootKey)
    }

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_landscape
    }
}
