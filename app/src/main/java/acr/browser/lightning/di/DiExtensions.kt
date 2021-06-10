@file:JvmName("Injector")

package acr.browser.lightning.di

import acr.browser.lightning.BrowserApp
import acr.browser.lightning.preference.ConfigurationPreferences
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.fragment.app.Fragment

/**
 * The [AppComponent] attached to the application [Context].
 */
val Context.injector: AppComponent
    get() = (applicationContext as BrowserApp).applicationComponent

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


/**
 * The [AppComponent] attached to the context, note that the fragment must be attached.
 */
val Fragment.injector: AppComponent
    get() = (context!!.applicationContext as BrowserApp).applicationComponent

/**
 * The [AppComponent] attached to the context, note that the fragment must be attached.
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Consumers should switch to support.v4.app.Fragment")
val android.app.Fragment.injector: AppComponent
    get() = (activity!!.applicationContext as BrowserApp).applicationComponent
