package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.di.UserPrefs
import acr.browser.lightning.di.injector
import acr.browser.lightning.utils.IntentUtils
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.SwitchPreferenceCompat
import javax.inject.Inject


class AppsSettingsFragment : AbstractSettingsFragment() {

    //@Inject
    //internal lateinit var userPreferences: UserPreferences

    @Inject
    @UserPrefs
    internal lateinit var preferences: SharedPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_apps

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

        val intentUtils = IntentUtils(activity as Activity)

        // Get all our preferences for external apps and populate our settings page with them
        val allEntries: Map<String, *> = preferences.all
        for ((key, value) in allEntries) {

            if (key.startsWith(getString(R.string.settings_app_prefix))) {
                //Log.d("map values", key + ": " + value.toString())

                val checkBoxPreference = SwitchPreferenceCompat(context)
                checkBoxPreference.title = key.substring(getString(R.string.settings_app_prefix).length)
                checkBoxPreference.key = key
                checkBoxPreference.isChecked = value as Boolean

                // SL: Can't get the icon color to be proper, we always get that white filter it seems
                // Leave it at that for now
                /*
                val intent = intentUtils.intentForUrl(null, "http://" + checkBoxPreference.title)
                val pm = (activity as Activity).packageManager

                val pkgAppsList: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                if (pkgAppsList.size > 0) {
                    checkBoxPreference.icon = pkgAppsList[0].activityInfo.loadIcon(pm)
                    checkBoxPreference.icon.colorFilter = null
                    DrawableCompat.setTint(checkBoxPreference.icon, Color.RED);
                    DrawableCompat.setTintList(checkBoxPreference.icon, null);
                    //Resources.Theme()
                    //DrawableCompat.applyTheme(checkBoxPreference.icon, )
                }
                */
                preferenceScreen.addPreference(checkBoxPreference)
            }
        }
    }
}
