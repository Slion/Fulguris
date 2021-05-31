/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.settings.activity

import acr.browser.lightning.R
import acr.browser.lightning.extensions.findPreference
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

private const val TITLE_TAG = "settingsActivityTitle"
const val SETTINGS_CLASS_NAME = "ClassName"

class SettingsActivity : ThemedSettingsActivity(),
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

        lateinit var iRootFragment: HeaderFragment;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Needed to recreate/language change to be able to update title to new locale
        /** When not instantiating this anew iRootFragment.preferenceScreen was null and we need it to fetch the title on [onResume] */
        iRootFragment = HeaderFragment()
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, iRootFragment)
                    .commit()
        } else {
            // We don't do that anymore as it won't work if language was changed
            //title = savedInstanceState.getCharSequence(TITLE_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.settings)
            }
        }

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
            // Check if we were asked to launch a fragment
            val className = intent.extras!!.getString(SETTINGS_CLASS_NAME)
            //val className = GeneralSettingsFragment::class.java.name
            val classType = Class.forName(className!!)
            //val myInstance = classType.newInstance()
            startFragment(classType)

        }
        catch(ex: Exception) {
            // Just ignore
        }

        // Just leaving some code sample here as those could be useful at some point
        // Start a fragment when class is known at compile time
        //startFragment<GeneralSettingsFragment>()
        // Start a fragment when class is not known at compile time
        //startFragment(GeneralSettingsFragment::class.java)

        try {
            // Needed to update title after language change
            // TODO: Though I'm guessing that won't work for second level fragments, if ever we get to use those.
            val f = supportFragmentManager.findFragmentById(R.id.settings)
            val pref = iRootFragment.preferenceScreen.findPreference(f?.javaClass!!)
            // Make sure we don't override the root title
            if (!pref?.title.isNullOrBlank()) {
                // Reset our title in proper locale
                title = pref?.title;
            }
        }
        catch(ex: Exception) {
            // Just ignore
        }


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Make sure the back button closes the application
        // See: https://stackoverflow.com/questions/14545139/android-back-button-in-the-title-bar
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        //outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit()
        title = pref.title
        return true
    }

    /**
     *
     */
    fun startFragment(@StringRes aPrefId: Int) {
        startFragment(getString(aPrefId))
    }

    /**
     *
     */
    fun startFragment(aPrefId: String) {
        startFragment(iRootFragment.preferenceScreen.findPreference<Preference>(aPrefId)!!)
    }

    /**
     * Start fragment matching the given type.
     */
    inline fun <reified T : PreferenceFragmentCompat?> startFragment() {
        // We need to find the preference that's associated with that fragment, before we can start it.
        startFragment(iRootFragment.preferenceScreen.findPreference<T>()!!)
    }

    /**
     * Start fragment matching the given type.
     */
    fun startFragment(aClass: Class<*>) {
        // We need to find the preference that's associated with that fragment, before we can start it.
        startFragment(iRootFragment.preferenceScreen.findPreference(aClass)!!)
    }

    /**
     * Start the fragment associated with the given [Preference].
     * Boiler plate code taken from [onPreferenceStartFragment], the framework function it overrides as well as its caller.
     */
    fun startFragment(pref: Preference) {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(iRootFragment, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
    }


    class HeaderFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_headers, rootKey)
        }
    }
}
