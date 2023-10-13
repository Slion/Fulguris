@file:JvmName("Injector")

package fulguris.di

import fulguris.App
import fulguris.settings.preferences.ConfigurationPreferences
import android.content.Context
import android.content.res.Configuration

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
            return (applicationContext as App).portraitPreferences
        }
        else {
            return (applicationContext as App).landscapePreferences
        }
    }
