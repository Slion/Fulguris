package fulguris.settings.fragment

import fulguris.BuildConfig
import fulguris.R
import android.os.Bundle
import androidx.preference.Preference
import androidx.webkit.WebViewFeature
import x.PreferenceFragmentBase

/**
 * TODO: Derive from [AbstractSettingsFragment]
 */
class RootSettingsFragment : PreferenceFragmentBase() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_root, rootKey)

        if (BuildConfig.BUILD_TYPE!="debug") {
            // Hide debug page in release builds
            //findPreference<Preference>(getString(R.string.pref_key_debug))?.isVisible = false
        }

        // Hide Profiles settings on devices where the WebView multi-profile feature is not supported
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            findPreference<Preference>(getString(R.string.pref_key_profiles))?.isVisible = false
        }
    }

    override fun titleResourceId(): Int {
        // TODO: Remove possible redundant usage of R.string.settings in places as we now provide it from here
        return R.string.settings
    }
}
