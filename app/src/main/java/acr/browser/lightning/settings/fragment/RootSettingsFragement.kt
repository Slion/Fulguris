package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

/**
 * TODO: Derive from [AbstractSettingsFragment]
 */
class RootSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_root, rootKey)

        if (BuildConfig.BUILD_TYPE!="debug") {
            // Hide debug page in release builds
            //findPreference<Preference>(getString(R.string.pref_key_debug))?.isVisible = false
        }
    }
}