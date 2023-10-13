package fulguris.extensions

import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat


/**
 * Find a preference from a key specified as a string resource.
 */
fun <T : Preference?> PreferenceFragmentCompat.find(@StringRes aId: Int): T? {
    return findPreference<T>(getString(aId))
}