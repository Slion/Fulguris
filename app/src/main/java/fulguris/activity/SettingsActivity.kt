/*
 * Copyright 2014 A.C.R. Development
 */
package fulguris.activity

import fulguris.R
import fulguris.extensions.findPreference
import fulguris.extensions.findViewsByType
import fulguris.settings.fragment.AbstractSettingsFragment
import fulguris.settings.fragment.RootSettingsFragment
import fulguris.settings.fragment.ResponsiveSettingsFragment
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import fulguris.extensions.ihs
import timber.log.Timber

const val SETTINGS_CLASS_NAME = "ClassName"

/**
 * TODO: Review title update implementation for both single and dual pane modes
 * Currently it only really works for single pane.
 * Meaning when you go to Portrait or Landscape settings in dual pane mode you don't know where you are.
 */
@AndroidEntryPoint
class SettingsActivity : ThemedSettingsActivity() {

    lateinit var responsive: ResponsiveSettingsFragment
    private var iFragmentClassName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        responsive = ResponsiveSettingsFragment()

        // That could be useful at some point
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, responsive)
                .runOnCommit {
                    responsive.childFragmentManager.addOnBackStackChangedListener {
                        // Triggers when a sub menu is opened, portrait and Landscape settings for instance
                        //updateTitle()
                    }
                }
            .commit()

        // Set our toolbar as action bar so that our title is displayed
        // See: https://stackoverflow.com/questions/27665018/what-is-the-difference-between-action-bar-and-newly-introduced-toolbar
        setSupportActionBar(findViewById(R.id.settings_toolbar))
        setTitle(R.string.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        //supportActionBar?.setDisplayShowTitleEnabled(true)

        iFragmentClassName = savedInstanceState?.getString(SETTINGS_CLASS_NAME)

        // Truncate title in the middle
        findViewById<ViewGroup>(R.id.settings_toolbar).findViewsByType(TextView::class.java).forEach {
            //Timber.d("Toolbar text: ${it.text}")
            it.ellipsize = TextUtils.TruncateAt.MIDDLE
            // it.ellipsize = TextUtils.TruncateAt.MARQUEE
            // it.marqueeRepeatLimit = -1
            // it.isSelected = true
        }
    }


    /**
     *
     */
    override fun onResume() {
        Timber.d("$ihs : onResume")
        super.onResume()

        // At this stage our preferences have been created
        try {
            // Start specified fragment if any
            if (iFragmentClassName == null) {
                val className = intent.extras!!.getString(SETTINGS_CLASS_NAME)
                val classType = Class.forName(className!!)
                startFragment(classType)
            } else {
                val classType = Class.forName(iFragmentClassName)
                startFragment(classType)
            }
            // Prevent switching back to that settings page after screen rotation
            //intent = null
        }
        catch(ex: Exception) {
            // Just ignore
        }

        updateTitleOnLayout()
    }

    /**
     *
     */
    override fun onDestroy() {
        Timber.d("$ihs : onDestroy")
        super.onDestroy()

        //responsive = null
    }

    /**
     *
     */
    fun updateTitleOnLayout(aGoingBack: Boolean = false) {
        findViewById<View>(android.R.id.content).doOnLayout {
            if (aGoingBack) {
                // This code path is not actually being used
                // TODO: clean it up
                updateTitle()
            } else {
                title = responsive.title()
            }
        }
    }

    /**
     * Fetch the currently loaded settings fragment.
     */
    private fun currentFragment() : Fragment? {

        return if (responsive.childFragmentManager.fragments.isNotEmpty() && ((responsive.slidingPaneLayout.isOpen && responsive.slidingPaneLayout.isSlideable) /*||responsive.childFragmentManager.backStackEntryCount>0*/)) {
            responsive.childFragmentManager.fragments.last()
        } else if (responsive.childFragmentManager.fragments.isNotEmpty() && responsive.slidingPaneLayout.isOpen && !responsive.slidingPaneLayout.isSlideable) {
            responsive.childFragmentManager.fragments.first()
        } else {
            supportFragmentManager.findFragmentById(R.id.settings)
        }

    }


    /**
     * Update activity title as define by the current fragment
     * Also still does not work properly on wide screens.
     * TODO: This is not actually being used anymore, consider a clean up
     */
    private fun updateTitle()
    {
        // TODO: could just be defensive, test rotation without it and remove if not needed
        if (responsive.view==null) {
            // Prevent crash upon screen rotation
            return
        }

        if (!responsive.slidingPaneLayout.isOpen /*|| !responsive.slidingPaneLayout.isSlideable*/) {
            setTitle(R.string.settings)
        } else {
            // Make sure title is also set properly when coming back from second level preference screen
            // Notably needed for portrait and landscape configuration settings
            updateTitle(currentFragment())
            //title = responsive.iPreference?.title
        }
    }

    /**
     * Update activity title as defined by the given [aFragment].
     */
    private fun updateTitle(aFragment : Fragment?)
    {
        Timber.d("updateTitle")
        // Needed to update title after language change
        (aFragment as? AbstractSettingsFragment)?.let {
            Timber.d("updateTitle done")
            title = it.title()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Make sure the back button closes the application
        // See: https://stackoverflow.com/questions/14545139/android-back-button-in-the-title-bar
        when (item.itemId) {
            android.R.id.home -> {
                doOnBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        doOnBackPressed()
    }

    /**
     *
     */
    private fun doOnBackPressed() {

        // Deploy workaround to make sure we exit this activity when user hits back from top level fragments
        // You can reproduce that issue by disabling that workaround and going in a nested settings fragment such as Look & Feel > Portrait
        // Then hit back button twice won't exit the settings activity. You can't exit the settings activity anymore.
        val doFinish = (responsive.childFragmentManager.backStackEntryCount==0 && (!responsive.slidingPaneLayout.isOpen || !responsive.slidingPaneLayout.isSlideable))
        //val doFinish = !responsive.slidingPaneLayout.isOpen
        super.onBackPressed()
        if (doFinish) {
            finish()
        } else {
            // Do not update title if we exit the activity to avoid showing broken title before exit
            responsive.popBreadcrumbs()
            updateTitleOnLayout()
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        // Save current activity title so we can set it again after a configuration change
        //outState.putCharSequence(TITLE_TAG, title)
        super.onSaveInstanceState(outState)

        // Persist current fragment to restore it after screen rotation for instance
        // TODO: For some reason that does not work with Portrait and Landscape pages
        responsive.iPreference?.fragment.let {
            outState.putString(SETTINGS_CLASS_NAME, it)
        }

    }



    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }


    /**
     * Start fragment matching the given type.
     * That should only work if the currently loaded fragment is our root/header fragment.
     */
    private fun startFragment(aClass: Class<*>) {
        // We need to find the preference that's associated with that fragment, before we can start it.
        (currentFragment() as? RootSettingsFragment)?.let {
            it.preferenceScreen.findPreference(aClass)?.let { pref ->
                it.onPreferenceTreeClick(pref)
            }
        }
    }

    /**
     *
     */
    override fun setTitle(title: CharSequence?) {
        Timber.d("setTitle: $title")
        super.setTitle(title)
    }
}
