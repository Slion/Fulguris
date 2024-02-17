package fulguris.settings.fragment

import fulguris.Capabilities
import fulguris.R
import fulguris.database.history.HistoryRepository
import fulguris.di.DatabaseScheduler
import fulguris.di.MainScheduler
import fulguris.dialog.BrowserDialog
import fulguris.dialog.DialogItem
import fulguris.extensions.snackbar
import fulguris.isSupported
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.WebUtils
import fulguris.view.WebPageTab
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Completable
import io.reactivex.Scheduler
import javax.inject.Inject

@AndroidEntryPoint
class PrivacySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var historyRepository: HistoryRepository
    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject @field:DatabaseScheduler
    internal lateinit var databaseScheduler: Scheduler
    @Inject @field:MainScheduler
    internal lateinit var mainScheduler: Scheduler

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_privacy
    }

    override fun providePreferencesXmlResource() = R.xml.preference_privacy

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        //injector.inject(this)

        // Hide analytics option if corresponding Firebase class not present
        try {
            Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
        } catch (ex: Exception) {
            findPreference<Preference>(getString(R.string.pref_key_analytics))?.isVisible = false
        }

        // Hide crash report option if corresponding Firebase class not present
        try {
            Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
        } catch (ex: Exception) {
            findPreference<Preference>(getString(R.string.pref_key_crash_report))?.isVisible = false
        }

        clickablePreference(preference = SETTINGS_CLEARCACHE, onClick = this::clearCache)
        clickablePreference(preference = SETTINGS_CLEARHISTORY, onClick = this::clearHistoryDialog)
        clickablePreference(preference = SETTINGS_CLEARCOOKIES, onClick = this::clearCookiesDialog)
        clickablePreference(preference = SETTINGS_CLEARWEBSTORAGE, onClick = this::clearWebStorage)

        switchPreference(
            preference = SETTINGS_LOCATION,
            isChecked = userPreferences.locationEnabled,
            onCheckChange = { userPreferences.locationEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_SAVEPASSWORD,
            isChecked = userPreferences.savePasswordsEnabled,
            onCheckChange = { userPreferences.savePasswordsEnabled = it }
        // From Android O auto-fill framework is used instead
        ).isVisible = Build.VERSION.SDK_INT < Build.VERSION_CODES.O

        switchPreference(
            preference = SETTINGS_CACHEEXIT,
            isChecked = userPreferences.clearCacheExit,
            onCheckChange = { userPreferences.clearCacheExit = it }
        )

        switchPreference(
            preference = SETTINGS_HISTORYEXIT,
            isChecked = userPreferences.clearHistoryExitEnabled,
            onCheckChange = { userPreferences.clearHistoryExitEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_COOKIEEXIT,
            isChecked = userPreferences.clearCookiesExitEnabled,
            onCheckChange = { userPreferences.clearCookiesExitEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_WEBSTORAGEEXIT,
            isChecked = userPreferences.clearWebStorageExitEnabled,
            onCheckChange = { userPreferences.clearWebStorageExitEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_DONOTTRACK,
            isChecked = userPreferences.doNotTrackEnabled,
            onCheckChange = { userPreferences.doNotTrackEnabled = it }
        )

        switchPreference(
            preference = getString(R.string.pref_key_webrtc),
            isChecked = userPreferences.webRtcEnabled && Capabilities.WEB_RTC.isSupported,
            isEnabled = Capabilities.WEB_RTC.isSupported,
            onCheckChange = { userPreferences.webRtcEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_IDENTIFYINGHEADERS,
            isChecked = userPreferences.removeIdentifyingHeadersEnabled,
            summary = "${WebPageTab.HEADER_REQUESTED_WITH}, ${WebPageTab.HEADER_WAP_PROFILE}",
            onCheckChange = { userPreferences.removeIdentifyingHeadersEnabled = it }
        )

        switchPreference(
            preference = SETTINGS_INCOGNITO,
            isChecked = userPreferences.incognito,
            onCheckChange = { userPreferences.incognito = it }
        )

    }

    private fun clearHistoryDialog() : Boolean {
        BrowserDialog.showPositiveNegativeDialog(
            aContext = activity as Activity,
            title = R.string.title_clear_history,
            message = R.string.dialog_history,
            positiveButton = DialogItem(title = R.string.action_yes) {
                clearHistory()
                    .subscribeOn(databaseScheduler)
                    .observeOn(mainScheduler)
                    .subscribe {
                        (activity as Activity).snackbar(R.string.message_clear_history)
                    }
            },
            negativeButton = DialogItem(title = R.string.action_no) {},
            onCancel = {}
        )
        return true
    }

    private fun clearCookiesDialog() : Boolean {
        BrowserDialog.showPositiveNegativeDialog(
            aContext = activity as Activity,
            title = R.string.title_clear_cookies,
            message = R.string.dialog_cookies,
            positiveButton = DialogItem(title = R.string.action_yes) {
                WebUtils.clearCookies {
                        if (it) {
                            (activity as Activity).snackbar(R.string.message_cookies_cleared)
                        } else {
                            (activity as Activity).snackbar(R.string.message_cookies_clear_error)
                        }
                    }
            },
            negativeButton = DialogItem(title = R.string.action_no) {},
            onCancel = {}
        )

        return true
    }

    private fun clearCache() : Boolean {
        WebView(requireNotNull(activity)).apply {
            clearCache(true)
            destroy()
        }
        (activity as Activity).snackbar(R.string.message_cache_cleared)
        return true
    }

    private fun clearHistory(): Completable = Completable.fromAction {
        val activity = activity
        if (activity != null) {
            // TODO: 6/9/17 clearHistory is not synchronous
            WebUtils.clearHistory(activity, historyRepository, databaseScheduler)
        } else {
            throw RuntimeException("Activity was null in clearHistory")
        }
    }

    private fun clearWebStorage() : Boolean {
        WebUtils.clearWebStorage()
        (activity as Activity).snackbar(R.string.message_web_storage_cleared)
        return true
    }

    companion object {
        private const val SETTINGS_LOCATION = "location"
        private const val SETTINGS_SAVEPASSWORD = "password"
        private const val SETTINGS_CACHEEXIT = "clear_cache_exit"
        private const val SETTINGS_HISTORYEXIT = "clear_history_exit"
        private const val SETTINGS_COOKIEEXIT = "clear_cookies_exit"
        private const val SETTINGS_CLEARCACHE = "clear_cache"
        private const val SETTINGS_CLEARHISTORY = "clear_history"
        private const val SETTINGS_CLEARCOOKIES = "clear_cookies"
        private const val SETTINGS_CLEARWEBSTORAGE = "clear_webstorage"
        private const val SETTINGS_WEBSTORAGEEXIT = "clear_webstorage_exit"
        private const val SETTINGS_DONOTTRACK = "do_not_track"
        private const val SETTINGS_IDENTIFYINGHEADERS = "remove_identifying_headers"
        private const val SETTINGS_INCOGNITO = "start_incognito"
    }

}
