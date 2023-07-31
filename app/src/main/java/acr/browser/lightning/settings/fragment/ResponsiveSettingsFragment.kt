package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.settings.activity.SettingsActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceHeaderFragmentCompat
import fulguris.preference.BasicPreference
import timber.log.Timber


/**
 * Give us single pane on narrow screens and two pane settings on wider screens.
 */
class ResponsiveSettingsFragment : PreferenceHeaderFragmentCompat() {

    private val iRootSettingsFragment = RootSettingsFragment()
    // Keep track of the settings fragment we are currently showing
    // Notably used to remain in the proper settings page after screen rotation
    var iPreference: Preference? = null

    // Breadcrumbs management
    private var iTitleStack: ArrayList<String> = ArrayList<String>()



    override fun onCreatePreferenceHeader(): PreferenceFragmentCompat {
        iTitleStack.add(requireContext().getString(R.string.settings))
        // Provide left pane headers fragment
        return iRootSettingsFragment
    }

    override fun onPreferenceStartFragment(
            caller: PreferenceFragmentCompat,
            pref: Preference
    ): Boolean {

        Timber.d("onPreferenceStartFragment")

        iPreference = pref

        // TODO: Do we still need to use either AbstractSettingsFragment or addOnBackStackChangedListener
        // Stack our breadcrumb if any, otherwise just stack our title
        (if (pref is BasicPreference && pref.breadcrumb.isNotEmpty()) pref.breadcrumb else pref.title)?.let {
            iTitleStack.add(it.toString())
        }

        // Trigger title update on next layout
        (activity as? SettingsActivity)?.updateTitleOnLayout()

        return super.onPreferenceStartFragment(caller,pref)
    }

    /**
     * Called by the activity whenever we go back
     */
    fun popBreadcrumbs() {
        iTitleStack.removeLast()
    }

    /**
     * Provide proper title according to current mode:
     * - Split screen mode shows breadcrumbs
     * - Full screen mode shows only current page title
     */
    fun title(): String {

        Timber.d("titles: $iTitleStack")

        return if (iRootSettingsFragment.isVisible && !slidingPaneLayout.isSlideable && iTitleStack.count()>1) {
            // We effectively disabled that algorithm using 100 for full crumb at start and end
            // We are simply using TextView ellipsis in the middle instead
            // Build our breadcrumbs
            var title = ""
            val sep = " > "
            val short = "…"
            // The last crumb index that should be displayed at the beginning of our title
            val lastFirst = 100
            // The first crumb index that should be displayed at the end of our title
            val firstLast = iTitleStack.lastIndex - 100
            // Build our title, it will look like: First > Second > … > … > Before last > Last
            iTitleStack.forEachIndexed { index, crumb ->
                  if (index==0) {
                      title = crumb
                  } else if (index>lastFirst && index<firstLast) {
                      title += "$sep$short"
                  } else {
                      title += "$sep$crumb"
                  }
            }
            title
        } else {
            iTitleStack.last()
        }
    }
}
