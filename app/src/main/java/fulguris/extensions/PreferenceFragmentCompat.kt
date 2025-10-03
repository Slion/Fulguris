package fulguris.extensions

import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import timber.log.Timber

/**
 * Find a preference from a key specified as a string resource.
 */
fun <T : Preference?> PreferenceFragmentCompat.find(@StringRes aId: Int): T? {
    return findPreference<T>(getString(aId))
}

/**
 * Triggers a ripple effect on a preference view to draw user's attention to it
 * without actually clicking it or changing its value.
 *
 * @param preferenceKeyResId The string resource ID of the preference key to highlight
 */
fun PreferenceFragmentCompat.flash(@StringRes preferenceKeyResId: Int) {
    flash(getString(preferenceKeyResId))
}

/**
 * Triggers a ripple effect on a preference view to draw user's attention to it
 * without actually clicking it or changing its value.
 *
 * @param preferenceKey The string of the preference key to highlight
 */
fun PreferenceFragmentCompat.flash(aKey: String) {
    view?.post {
        val preference = findPreference<Preference>(aKey)

        preference?.let { pref ->
            // Get the RecyclerView that holds the preferences
            val recyclerView = listView

            // Find the preference's position in the adapter
            val preferenceScreen = preferenceScreen
            var position = -1
            for (i in 0 until preferenceScreen.preferenceCount) {
                if (preferenceScreen.getPreference(i) == pref) {
                    position = i
                    break
                }
            }

            if (position >= 0 && position < recyclerView.childCount) {
                val targetView = recyclerView.getChildAt(position)
                // Trigger ripple animation by simulating press state
                targetView.isPressed = true
                targetView.isPressed = false
                Timber.d("Triggered ripple effect on preference at position $position (key: $aKey)")
            } else {
                Timber.w("Could not find preference view at position $position (childCount=${recyclerView.childCount}, key: $aKey")
            }
        } ?: run {
            Timber.w("Could not find preference with key: $aKey")
        }
    }
}
