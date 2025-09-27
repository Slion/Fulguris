package fulguris.settings.fragment

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.preference.PreferenceFragmentCompat

/**
 * Base class for settings fragments.
 */
open class Fragment(aResId: Int) : PreferenceFragmentCompat() {

    private val iResId = aResId

    @CallSuper
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(iResId, rootKey)
    }
}