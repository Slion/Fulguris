package acr.browser.lightning.settings.fragment

import acr.browser.lightning.Capabilities
import acr.browser.lightning.R
import acr.browser.lightning.browser.SearchBoxDisplayChoice
import acr.browser.lightning.constant.TEXT_ENCODINGS
import acr.browser.lightning.di.injector
import acr.browser.lightning.extensions.resizeAndShow
import acr.browser.lightning.extensions.withSingleChoiceItems
import acr.browser.lightning.isSupported
import acr.browser.lightning.preference.UserPreferences
import acr.browser.lightning.settings.NewTabPosition
import acr.browser.lightning.view.RenderingMode
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import javax.inject.Inject

/**
 * The advanced settings of the app.
 */
class AdvancedSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_advanced

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)


        val incognitoCheckboxPreference = switchPreference(
            preference = SETTINGS_COOKIES_INCOGNITO,
            isEnabled = !Capabilities.FULL_INCOGNITO.isSupported,
            isChecked = if (Capabilities.FULL_INCOGNITO.isSupported) {
                userPreferences.cookiesEnabled
            } else {
                userPreferences.incognitoCookiesEnabled
            },
            summary = if (Capabilities.FULL_INCOGNITO.isSupported) {
                getString(R.string.incognito_cookies_pie)
            } else {
                null
            },
            onCheckChange = { userPreferences.incognitoCookiesEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_ENABLE_COOKIES,
            isChecked = userPreferences.cookiesEnabled,
            onCheckChange = {
                userPreferences.cookiesEnabled = it
                if (Capabilities.FULL_INCOGNITO.isSupported) {
                    incognitoCheckboxPreference.isChecked = it
                }
            }
        )

    }



    companion object {
        private const val SETTINGS_ENABLE_COOKIES = "allow_cookies"
        private const val SETTINGS_COOKIES_INCOGNITO = "incognito_cookies"
    }

}
