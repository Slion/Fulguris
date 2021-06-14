package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.di.injector
import acr.browser.lightning.settings.preferences.ConfigurationPreferences
import acr.browser.lightning.settings.preferences.PortraitPreferences
import acr.browser.lightning.utils.portraitSharedPreferencesName
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import javax.inject.Inject

/**
 * Portrait settings configuration screen.
 * Notably use the correct shared preferences file rather than the default one.
 */
class PortraitSettingsFragment : ConfigurationSettingsFragment() {

    @Inject internal lateinit var portraitPreferences: PortraitPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_configuration
    override fun configurationPreferences() : ConfigurationPreferences = portraitPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        injector.inject(this)
        // That's the earliest place we can change our preference file as earlier in onCreate the manager has not been created yet
        preferenceManager.sharedPreferencesName = portraitSharedPreferencesName(requireContext())
        preferenceManager.sharedPreferencesMode = MODE_PRIVATE
        super.onCreatePreferences(savedInstanceState,rootKey)
    }

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_title_portrait
    }
}
