package fulguris.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import fulguris.R
import fulguris.extensions.openBrowserChooser
import timber.log.Timber

/**
 * Preference page displaying terms and conditions and privacy policy links
 * Used by our app intro onboarding activity slide of the same name
 */
class AcceptTermsPreferenceFragment: PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Timber.Forest.d("AcceptTermsFragment")
        setPreferencesFromResource(R.xml.preference_accept_terms, rootKey)

        // Set up click listeners for terms and privacy policy
        setupPreferenceClickListeners()
    }

    private fun setupPreferenceClickListeners() {
        // Terms and Conditions preference
        findPreference<Preference>(getString(R.string.pref_key_terms_and_conditions))?.setOnPreferenceClickListener {
            context?.openBrowserChooser( R.string.url_terms_and_conditions)
            true
        }

        // Privacy Policy preference
        findPreference<Preference>(getString(R.string.pref_key_privacy_policy))?.setOnPreferenceClickListener {
            context?.openBrowserChooser(R.string.url_privacy_policy)
            true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }
}