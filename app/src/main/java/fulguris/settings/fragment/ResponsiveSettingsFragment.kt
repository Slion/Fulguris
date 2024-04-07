package fulguris.settings.fragment

import fulguris.R
import fulguris.activity.SettingsActivity
import android.annotation.SuppressLint
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceHeaderFragmentCompat
import slions.pref.BasicPreference
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

    /**
     *
     */
    @SuppressLint("MissingSuperCall")
    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        //super.onPreferenceStartFragment(caller, pref)

        if (caller.id == R.id.preferences_header) {
            // A preference was selected in our root/header
            // That means our breadcrumbs need to be reset to the root level
            resetBreadcrumbs()
        }

        iPreference = pref

        // No actual fragment specified, just a back action
        // Notably used when deleting custom configuration
        if (pref.fragment == "back") {
            if (childFragmentManager.backStackEntryCount >=1) {
                // Go back to previous fragment if any
                childFragmentManager.popBackStack()
                // Adjust and update our breadcrumb
                iTitleStack.removeLast()
                (activity as? SettingsActivity)?.updateTitleOnLayout()
            }

            return true
        }

        // TODO: Do we still need to use either AbstractSettingsFragment or addOnBackStackChangedListener
        // Stack our breadcrumb if any, otherwise just stack our title
        (if (pref is BasicPreference && pref.breadcrumb.isNotEmpty()) pref.breadcrumb else pref.title)?.let {
            iTitleStack.add(it.toString())
        }

        // Trigger title update on next layout
        (activity as? SettingsActivity)?.updateTitleOnLayout()

        // Launch specified fragment
        // NOTE: This code is taken from the super implementation to which we added the animations
        if (caller.id == R.id.preferences_header) {
            Timber.d("onPreferenceStartFragment: caller is header")
            // Opens the preference header.
            openPreferenceHeader(pref)
            return true
        }
        if (caller.id == R.id.preferences_detail) {
            Timber.d("onPreferenceStartFragment: caller is detail")
            // Opens an preference in detail pane.
            val frag = childFragmentManager.fragmentFactory.instantiate(
                requireContext().classLoader,
                pref.fragment!!
            )
            frag.arguments = pref.extras

            childFragmentManager.commit {
                setReorderingAllowed(true)
                setCustomAnimations(R.anim.slide_in_from_right,
                    R.anim.slide_out_to_left,
                    R.anim.slide_in_from_left,
                    R.anim.slide_out_to_right)
                replace(R.id.preferences_detail, frag)
                addToBackStack(null)
            }
            return true
        }
        return false

    }

    /**
     * Taken from base class to add animations
     */
    private fun openPreferenceHeader(header: Preference) {
        if (header.fragment == null) {
            if (header.intent == null) return
            // TODO: Change to use WindowManager ActivityView API
            header.intent?.let {
                startActivity(it)
            }
            return
        }
        val fragment = header.fragment?.let {
            childFragmentManager.fragmentFactory.instantiate(
                requireContext().classLoader,
                it
            )
        }

        fragment?.apply {
            arguments = header.extras
        }

        // Clear back stack
        if (childFragmentManager.backStackEntryCount > 0) {
            val entry = childFragmentManager.getBackStackEntryAt(0)
            childFragmentManager.popBackStack(entry.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        childFragmentManager.commit {
            setReorderingAllowed(true)
            // Don't do animation when the pane is not open
            // The opening of the pane itself is the animation
            if (slidingPaneLayout.isOpen) {
                // Define animations when opening settings from our root left pane
                setCustomAnimations(R.anim.slide_in_from_right,
                    R.anim.slide_out_to_left,
                    R.anim.slide_in_from_left,
                    R.anim.slide_out_to_right)
            }
            replace(R.id.preferences_detail, fragment!!)
            slidingPaneLayout.openPane()
        }
    }

    /**
     *
     */
    private fun resetBreadcrumbs() {
        Timber.d("resetBreadcrumbs: ${iTitleStack.count()}")
        // Only keep our root title
        while (iTitleStack.count()>1) {
            iTitleStack.removeLast()
        }
    }

    /**
     * Called by the activity whenever we go back
     */
    fun popBreadcrumbs() {
        Timber.d("popBreadcrumbs: ${iTitleStack.count()}")
        if (iTitleStack.count()>1) {
            iTitleStack.removeLast()
        }
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
