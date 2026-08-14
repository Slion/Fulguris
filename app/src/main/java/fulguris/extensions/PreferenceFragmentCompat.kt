package fulguris.extensions

import androidx.annotation.StringRes
import androidx.core.view.doOnPreDraw
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
 * @param times Number of times to flash (default: 2)
 * @param delayMs Delay in milliseconds between flashes (default: 800)
 */
fun PreferenceFragmentCompat.flash(@StringRes preferenceKeyResId: Int, times: Int = 2, delayMs: Long = 800, focus: Boolean = true) {
    flash(getString(preferenceKeyResId), times, delayMs, focus)
}

/**
 * Triggers a ripple effect on a preference view to draw user's attention to it
 * without actually clicking it or changing its value.
 *
 * @param aKey The string of the preference key to highlight
 * @param times Number of times to flash (default: 2)
 * @param delayMs Delay in milliseconds between flashes (default: 800)
 */
fun PreferenceFragmentCompat.flash(aKey: String, times: Int = 2, delayMs: Long = 800, focus: Boolean = true) {
    val preference = findPreference<Preference>(aKey)

    preference?.let { pref ->
        // Get the RecyclerView that holds the preferences
        val recyclerView = listView

        // Find the preference's position in the RecyclerView adapter (flattened list)
        // This needs to account for nested preferences in categories
        var position = findPreferencePositionInAdapter(preferenceScreen, pref)

        Timber.d("Flash: Searching for preference key=$aKey, calculated position=$position, recyclerView.childCount=${recyclerView.childCount}, times=$times, delayMs=$delayMs, focus=$focus")

        if (position >= 0 && position < recyclerView.childCount) {
            val targetView = recyclerView.getChildAt(position)
            // Trigger ripple animation multiple times
            flashView(targetView, times, 0, aKey, delayMs, focus)
        } else {
            Timber.w("Could not find preference view at position $position (childCount=${recyclerView.childCount}, key: $aKey)")
            // Try alternative approach: use the RecyclerView's layout manager
            recyclerView.post {
                val layoutManager = recyclerView.layoutManager
                val adapterPosition = findPreferenceAdapterPosition(preferenceScreen, pref)
                Timber.d("Flash: Adapter position=$adapterPosition")
                if (adapterPosition >= 0) {
                    layoutManager?.findViewByPosition(adapterPosition)?.let { targetView ->
                        flashView(targetView, times, 0, aKey, delayMs, focus)
                    } ?: Timber.w("Could not find view at adapter position $adapterPosition")
                }
            }
        }
    } ?: run {
        Timber.w("Could not find preference with key: $aKey")
    }
}

/**
 * Moves the D-pad focus to the row of a preference.
 * Used to keep focus on a row that just changed state (e.g. a switch that was
 * toggled) so the cursor doesn't jump to the first preference when the row rebinds.
 *
 * @param preferenceKeyResId The string resource ID of the preference key
 */
fun PreferenceFragmentCompat.requestFocusOnPreference(@StringRes preferenceKeyResId: Int) {
    requestFocusOnPreference(getString(preferenceKeyResId))
}

/**
 * Moves the D-pad focus to the row of a preference.
 *
 * @param aKey The string of the preference key to focus
 */
fun PreferenceFragmentCompat.requestFocusOnPreference(aKey: String) {
    findPreference<Preference>(aKey)?.let { pref ->
        val recyclerView = listView
        // Wait until the next layout pass so this runs after the RecyclerView
        // rebinds the row following a toggle
        recyclerView.doOnPreDraw { view: android.view.View ->
            val position = findPreferencePositionInAdapter(preferenceScreen, pref)
            val rowView = if (position >= 0 && position < recyclerView.childCount) {
                recyclerView.getChildAt(position)
            } else {
                val adapterPosition = findPreferenceAdapterPosition(preferenceScreen, pref)
                recyclerView.layoutManager?.findViewByPosition(adapterPosition)
            }
            rowView?.requestFocus()
            true
        }
    }
}

/**
 * Flash a view multiple times with a delay between flashes.
 */
private fun flashView(view: android.view.View, totalTimes: Int, currentCount: Int, key: String, delayMs: Long, focus: Boolean = true) {
    // Trigger ripple animation by simulating press state
    view.isPressed = true
    view.isPressed = false

    Timber.d("Triggered ripple effect ${currentCount + 1}/$totalTimes on preference (key: $key)")

    if (currentCount + 1 >= totalTimes) {
        Timber.d("Completed $totalTimes flashes for preference (key: $key)")
        // Move focus to the flashed preference once the flashes are done, so TV users
        // know where to look without the focus fighting the ripple animation
        if (focus) view.requestFocus()
        return
    }

    // Schedule next flash after a delay
    view.postDelayed({
        flashView(view, totalTimes, currentCount + 1, key, delayMs, focus)
    }, delayMs)
}

/**
 * Recursively find the position of a preference in the flattened adapter list.
 */
private fun findPreferencePositionInAdapter(root: androidx.preference.PreferenceGroup, target: Preference, currentPos: Int = 0): Int {
    var position = currentPos

    for (i in 0 until root.preferenceCount) {
        val pref = root.getPreference(i)

        if (pref == target) {
            return position
        }

        position++

        // If this preference is a group, search its children
        if (pref is androidx.preference.PreferenceGroup) {
            val foundPos = findPreferencePositionInAdapter(pref, target, 0)
            if (foundPos >= 0) {
                // Found in child group, return adjusted position
                return position - 1 + foundPos
            }
            // Add children count to position
            position += countVisiblePreferences(pref) - 1
        }
    }

    return -1 // Not found
}

/**
 * Count visible preferences in a group recursively.
 */
private fun countVisiblePreferences(group: androidx.preference.PreferenceGroup): Int {
    var count = 0
    for (i in 0 until group.preferenceCount) {
        val pref = group.getPreference(i)
        if (pref.isVisible) {
            count++
            if (pref is androidx.preference.PreferenceGroup) {
                count += countVisiblePreferences(pref)
            }
        }
    }
    return count
}

/**
 * Find adapter position for a preference.
 */
private fun findPreferenceAdapterPosition(root: androidx.preference.PreferenceGroup, target: Preference): Int {
    return findPreferencePositionInAdapter(root, target, 0)
}

