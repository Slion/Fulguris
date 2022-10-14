/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.settings.activity

import acr.browser.lightning.R
import acr.browser.lightning.extensions.findPreference
import acr.browser.lightning.settings.fragment.AbstractSettingsFragment
import acr.browser.lightning.settings.fragment.RootSettingsFragment
import acr.browser.lightning.settings.fragment.ResponsiveSettingsFragment
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint

private const val TITLE_TAG = "settingsActivityTitle"
const val SETTINGS_CLASS_NAME = "ClassName"

/**
 * TODO: Review title update implementation for both single and dual pane modes
 * Currently it only really works for single pane.
 * Meaning when you go to Portrait or Landscape settings in dual pane mode you don't know where you are.
 */
@AndroidEntryPoint
class SettingsActivity : ThemedSettingsActivity(),
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private lateinit var responsive: ResponsiveSettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setTitle(R.string.settings)

        responsive = ResponsiveSettingsFragment()

        // TODO: savedInstanceState is not null after screen rotation
        // I guess we could use it to save and restore the current state so that we remain on the same page after screen rotation
        //if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, responsive)
                    .runOnCommit {
                        responsive.childFragmentManager.addOnBackStackChangedListener {
                            // Triggers when a sub menu is opened, portrait and Landscape settings for instance
                            updateTitle()
                        }
                    }
                .commit()
        //}

        // Set our toolbar as action bar so that our title is displayed
        // See: https://stackoverflow.com/questions/27665018/what-is-the-difference-between-action-bar-and-newly-introduced-toolbar
        setSupportActionBar(findViewById(R.id.settings_toolbar))

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        //supportActionBar?.setDisplayShowTitleEnabled(true)
    }


    override fun onResume() {
        super.onResume()
        // At this stage our preferences have been created
        try {
            // Start specified fragment if any
            val className = intent.extras!!.getString(SETTINGS_CLASS_NAME)
            val classType = Class.forName(className!!)
            startFragment(classType)
        }
        catch(ex: Exception) {
            // Just ignore
        }

        updateTitle()
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
     * TODO: The whole title update stuff is a mess, try fix it.
     * Also still does not work properly on wide screens.
     */
    fun updateTitle()
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
        }
    }

    /**
     * Update activity title as defined by the given [aFragment].
     */
    private fun updateTitle(aFragment : Fragment?)
    {
        // Needed to update title after language change
        (aFragment as? AbstractSettingsFragment)?.let {
            setTitle(it.titleResourceId())
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Make sure the back button closes the application
        // See: https://stackoverflow.com/questions/14545139/android-back-button-in-the-title-bar
        when (item.itemId) {
            android.R.id.home -> {
                // Deploy workaround to make sure we exit this activity when user hits back from top level fragments
                // You can reproduce that issue by disabling that workaround and going in a nested settings fragment such as Look & Feel > Portrait
                // Then hit back button twice won't exit the settings activity. You can't exit the settings activity anymore.
                val doFinish = (responsive.childFragmentManager.backStackEntryCount==0 && (!responsive.slidingPaneLayout.isOpen || !responsive.slidingPaneLayout.isSlideable))
                //val doFinish = !responsive.slidingPaneLayout.isOpen
                onBackPressed()
                if (doFinish) {
                    finish()
                }

                Handler().postDelayed({ updateTitle() }, 100)

                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        // Save current activity title so we can set it again after a configuration change
        //outState.putCharSequence(TITLE_TAG, title)
        super.onSaveInstanceState(outState)

    }


    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }


    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        startFragment(caller,pref)
        return true
    }


    /**
     * Start fragment matching the given type.
     * That should only work if the currently loaded fragment is our root/header fragment.
     */
    private fun startFragment(aClass: Class<*>) {
        // We need to find the preference that's associated with that fragment, before we can start it.
        (currentFragment() as? RootSettingsFragment)?.let {
            it.preferenceScreen.findPreference(aClass)?.let { pref ->
                startFragment(it,pref)
            }
        }
    }



    /**
     * Start the fragment associated with the given [Preference].
     * Boiler plate code taken from [onPreferenceStartFragment], the framework function it overrides as well as its caller.
     *
     * [aTarget] My understanding is that this is the fragment that will be replaced by the one the are starting.
     * [aPref] Preference associated with the fragment being started.
     */

    private fun startFragment(aTarget: PreferenceFragmentCompat, aPref: Preference) {
        // Instantiate the new Fragment
        val args = aPref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            aPref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(aTarget, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
                .runOnCommit { updateTitle() }
            .commit()

        //updateTitle(fragment)
    }



}
