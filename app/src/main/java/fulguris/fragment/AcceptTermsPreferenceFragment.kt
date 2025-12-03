package fulguris.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import fulguris.R
import fulguris.activity.IntroActivity
import fulguris.extensions.openBrowserChooser
import timber.log.Timber

/**
 * Preference page displaying terms and conditions and privacy policy links
 * Used by our app intro onboarding activity slide of the same name
 */
class AcceptTermsPreferenceFragment: PreferenceFragmentCompat() {

    // Track the delayed navigation task so it can be cancelled if user toggles switch back
    private val pendingNavigation = Runnable {
        Timber.d("Executing delayed navigation to next slide")
        (activity as? IntroActivity)?.nextSlide()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Timber.Forest.d("AcceptTermsFragment")
        setPreferencesFromResource(R.xml.preference_accept_terms, rootKey)

        // Set up listener for accept terms toggle
        setupAcceptTermsListener()
    }

    private fun setupAcceptTermsListener() {
        // Listen for changes to the accept terms switch
        findPreference<x.SwitchPreference>(getString(R.string.pref_key_accept_terms))?.setOnPreferenceChangeListener { _, newValue ->
            // Cancel any pending navigation
            view?.removeCallbacks(pendingNavigation)

            // When user accepts terms, automatically advance to next slide
            if (newValue == true) {
                Timber.d("User accepted terms, scheduling navigation to next slide")
                // Post with delay to allow the preference animation to complete
                view?.postDelayed(pendingNavigation, 500)
            } else {
                Timber.d("User toggled terms acceptance off")
            }
            true
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel pending navigation when user navigates away from this slide
        view?.removeCallbacks(pendingNavigation)
        Timber.d("onPause: Cancelled pending navigation")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    override fun onDestroyView() {
        // Clean up any pending navigation when view is destroyed
        view?.removeCallbacks(pendingNavigation)
        super.onDestroyView()
    }
}