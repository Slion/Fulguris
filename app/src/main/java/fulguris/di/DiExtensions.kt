@file:JvmName("Injector")

package fulguris.di

import fulguris.App
import fulguris.settings.preferences.ConfigurationPreferences
import android.content.Context
import android.content.res.Configuration
import fulguris.device.ScreenSize
import fulguris.extensions.configId
import fulguris.settings.Config
import fulguris.settings.preferences.ConfigurationCustomPreferences
import timber.log.Timber
import java.io.File

/**
 * Provides access to current configuration settings, typically either portrait or landscape variant.
 */
val Context.configPrefs: ConfigurationPreferences
    get() {
        return (applicationContext as App).configPreferences!!
    }

/**
 * Load configuration preferences corresponding to the current configuration and make it available through our application singleton.
 */
fun Context.updateConfigPrefs() {
    val currentConfigId = this.configId
    Timber.d("updateConfigPrefs - $currentConfigId")

    // Build our list of custom configurations
    // Configurations preferences are saved as XML files in our shared preferences folder with the Config.filePrefix
    val directory = File(applicationInfo.dataDir, "shared_prefs")
    if (directory.exists() && directory.isDirectory) {
        val list = directory.list { _, name -> name.startsWith(Config.filePrefix) }

        list?.forEach { fileName ->
            // Create config object from file name
            val config = Config(fileName)
            // Check if we found the current config
            if (config.id == currentConfigId) {
                // We have specific custom preferences for the current configuration
                // Load it and make it accessible through our application singleton
                (applicationContext as App).apply {
                    configPreferences = ConfigurationCustomPreferences(getSharedPreferences(config.fileName,0), ScreenSize(this@updateConfigPrefs))
                }
                Timber.d("updateConfigPrefs - Found specific config")
                // We found our config, we are done here
                return
            }
        }
    }

    // Specific config was not found, use one the generic ones then.
    // That's either landscape or portrait configuration.
    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        (applicationContext as App).configPreferences = (applicationContext as App).portraitPreferences
    }
    else {
        (applicationContext as App).configPreferences = (applicationContext as App).landscapePreferences
    }

}