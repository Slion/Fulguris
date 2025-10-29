package fulguris.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import fulguris.R
import fulguris.activity.IntroActivity
import timber.log.Timber

/**
 * Preference page for configuration onboarding
 * Used by our app intro onboarding activity slide
 */
class ConfigurationPreferenceFragment: PreferenceFragmentCompat() {

    // Track the delayed navigation task so it can be cancelled if user toggles switch back
    private val pendingNavigation = Runnable {
        Timber.d("Executing delayed navigation to next slide")
        (activity as? IntroActivity)?.nextSlide()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Timber.d("ConfigurationPreferenceFragment")
        setPreferencesFromResource(R.xml.preference_configuration_intro, rootKey)

        // Set up listener for configuration toggle
        setupConfigurationListener()
    }

    private fun setupConfigurationListener() {
        // Listen for changes to the configuration switch
        findPreference<slions.pref.SwitchPreference>(getString(R.string.pref_key_configuration))?.setOnPreferenceChangeListener { _, newValue ->
            // Cancel any pending navigation
            view?.removeCallbacks(pendingNavigation)

//            // When user enables configuration, automatically advance to next slide
//            if (newValue == true) {
//                Timber.d("User enabled configuration, scheduling navigation to next slide")
//                // Post with delay to allow the preference animation to complete
//                view?.postDelayed(pendingNavigation, 500)
//            } else {
//                Timber.d("User disabled configuration")
//            }
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
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        // Clean up any pending navigation when view is destroyed
        view?.removeCallbacks(pendingNavigation)
        super.onDestroyView()
    }
}