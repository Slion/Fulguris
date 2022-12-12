package acr.browser.lightning.settings.fragment

import acr.browser.lightning.settings.activity.SettingsActivity
import android.os.Handler
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceHeaderFragmentCompat


/**
 * Give us single pane on narrow screens and two pane settings on wider screens.
 */
class ResponsiveSettingsFragment(aFragmentClassName: String?=null) : PreferenceHeaderFragmentCompat() {

    private val iInitialDetailFragmentName: String? = aFragmentClassName


    val iRootSettingsFragment = RootSettingsFragment()

    override fun onCreatePreferenceHeader(): PreferenceFragmentCompat {
        // Provide left pane headers fragment

/*
        iInitialDetailFragmentName?.let {
            return childFragmentManager.fragmentFactory.instantiate(
                    requireContext().classLoader,
                    it ) as PreferenceFragmentCompat
        }*/

        return iRootSettingsFragment
    }

    override fun onPreferenceStartFragment(
            caller: PreferenceFragmentCompat,
            pref: Preference
    ): Boolean {
        // TODO: Find a place to do that without post delay
        Handler().postDelayed({(activity as? SettingsActivity)?.updateTitle()},450)
        return super.onPreferenceStartFragment(caller,pref)
    }

    override fun onCreateInitialDetailFragment(): Fragment? {

        /*
        // If we specified fragment just open it then
        iInitialDetailFragmentName?.let {
            return childFragmentManager.fragmentFactory.instantiate(
                    requireContext().classLoader,
                    it )
        }
        */

        return super.onCreateInitialDetailFragment()
    }

}
