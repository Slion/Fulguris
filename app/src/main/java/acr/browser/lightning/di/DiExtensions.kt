@file:JvmName("Injector")

package acr.browser.lightning.di

import acr.browser.lightning.BrowserApp
import acr.browser.lightning.settings.preferences.ConfigurationPreferences
import android.content.Context
import android.content.res.Configuration
import androidx.fragment.app.Fragment

/**
 * Provides access to current configuration settings, typically either portrait or landscape variant.
 */
val Context.configPrefs: ConfigurationPreferences
    get() {
        return configPrefs(resources.configuration)
    }

/**
 * Provides access to the configuration settings matching the given [aConfig].
 * Use this if you want to access settings for the incoming configuration from [onConfigurationChanged].
 */
fun Context.configPrefs(aConfig: Configuration): ConfigurationPreferences
    {
        if (aConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            return (applicationContext as BrowserApp).portraitPreferences
        }
        else {
            return (applicationContext as BrowserApp).landscapePreferences
        }
    }
