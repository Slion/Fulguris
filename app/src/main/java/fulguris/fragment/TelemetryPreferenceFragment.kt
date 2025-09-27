package fulguris.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R

/**
 * Preference fragment for telemetry settings in the intro flow
 * Contains crash report and analytics toggles
 */
@AndroidEntryPoint
class TelemetryPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_telemetry, rootKey)
    }
}
