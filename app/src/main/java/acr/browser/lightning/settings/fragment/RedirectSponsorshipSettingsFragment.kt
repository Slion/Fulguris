package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference

/**
 * Sponsorship settings for non Google Play Store variants.
 * We just redirect users to Google Play Store if they want to sponsor us.
 */
abstract class RedirectSponsorshipSettingsFragment : AbstractSettingsFragment() {

    override fun providePreferencesXmlResource() = R.xml.preference_sponsorship

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        addPreferenceLinkToGooglePlayStoreFiveStarsReview()
        addPreferenceShareLink()
        addPreferenceLinkToCrowdin()
        addPreferenceLinkToGitHubSponsor()
        addPreferenceLinkToGooglePlayStore()
    }

    /**
     * Add a preference that opens up our play store page.
     */
    private fun addPreferenceLinkToGooglePlayStore() {
        // We invite user to installer our Google Play Store release
        val pref = Preference(context)
        pref.title = resources.getString(R.string.pref_title_no_sponsorship)
        pref.summary = resources.getString(R.string.pref_summary_no_sponsorship)
        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_arrow, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // Open up Fulguris play store page
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.slions.fulguris.full.playstore")))
            true
        }
        preferenceScreen.addPreference(pref)
    }






}
