package acr.browser.lightning.extensions

import android.text.TextUtils
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup


/**
 * Find the first preference whose fragment property matches the template argument.
 * That allows us to fetch preferences from a preference screen without using ids.
 */
inline fun <reified T : PreferenceFragmentCompat?> PreferenceGroup.findPreference(): Preference? {
    return findPreference(T::class.java)
}


/**
 * Find the first preference whose fragment property matches the template argument.
 * That allows us to fetch preferences from a preference screen without using ids.
 */
fun PreferenceGroup.findPreference(aClass: Class<*>): Preference? {
    val preferenceCount: Int = preferenceCount
    for (i in 0 until preferenceCount) {
        val preference: Preference = getPreference(i)
        if (preference.fragment == aClass.name) {
            return preference
        }
    }
    return null
}