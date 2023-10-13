package fulguris.settings.fragment

import fulguris.R
import fulguris.extensions.findViewByType
import fulguris.extensions.onceOnLayoutChange
import fulguris.extensions.snackbar
import fulguris.settings.preferences.DeveloperPreferences
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.FileUtils
import fulguris.utils.Utils.startActivityForFolder
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DebugSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var developerPreferences: DeveloperPreferences
    @Inject internal lateinit var userPreferences: UserPreferences

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.debug_title
    }

    override fun providePreferencesXmlResource() = R.xml.preference_debug

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        //injector.inject(this)

        switchPreference(
            preference = LEAK_CANARY,
            isChecked = developerPreferences.useLeakCanary,
            onCheckChange = { change ->
                activity?.snackbar(R.string.app_restart)
                developerPreferences.useLeakCanary = change
            }
        )

        switchPreference(
                preference = getString(R.string.pref_key_crash_logs),
                isChecked = userPreferences.crashLogs,
                onCheckChange = { change ->
                    //activity?.snackbar(R.string.pref_summary_crash_log)
                    userPreferences.crashLogs = change
                }
        // We will use this to find our view later on
        ).setViewId(R.id.pref_id_crash_logs)

    }

    private fun openCrashLogsFolder() {
        startActivityForFolder(context, fulguris.utils.FileUtils.getFolderCrashLogs().absolutePath)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // NOTE: That setup could fall apart if our recycler item view is destroyed…
        // …or if the item view we are targeting is not yet constructed because off screen.
        listView.onceOnLayoutChange {
            listView.findViewById<View>(R.id.pref_id_crash_logs).apply {
                // Find text container view of our preference
                findViewByType(RelativeLayout::class.java)?.apply {
                    setOnClickListener {
                        // Open crash logs folder when text view is tapped
                        openCrashLogsFolder()
                    }
                }
                findViewById<View>(R.id.icon_frame).setOnClickListener {
                    // Open crash logs folder when icon is tapped
                    // Not perfect because the frame does not tak the whole space… that will do though
                    openCrashLogsFolder()
                }
                // Note, the switch toggle widget ID is: android.R.id.widget_frame
            }
        }
    }

    companion object {
        private const val LEAK_CANARY = "leak_canary_enabled"

    }
}
