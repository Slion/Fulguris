package fulguris.settings.fragment

import fulguris.R
import fulguris.settings.preferences.UserPreferences
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The advanced settings of the app.
 */
@AndroidEntryPoint
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

        //injector.inject(this)
    }


}
