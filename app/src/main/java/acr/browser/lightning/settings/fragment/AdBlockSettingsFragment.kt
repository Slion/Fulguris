package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.adblock.AbpListUpdater
import acr.browser.lightning.adblock.BloomFilterAdBlocker
import acr.browser.lightning.adblock.source.HostsSourceType
import acr.browser.lightning.adblock.source.selectedHostsSource
import acr.browser.lightning.adblock.source.toPreferenceIndex
import acr.browser.lightning.di.DiskScheduler
import acr.browser.lightning.di.MainScheduler
import acr.browser.lightning.di.injector
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.dialog.DialogItem
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.preference.UserPreferences
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.text.InputType
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.Preference
import io.reactivex.Maybe
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Okio
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Settings for the ad block mechanic.
 */
class AdBlockSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject @field:MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject @field:DiskScheduler internal lateinit var diskScheduler: Scheduler
    @Inject internal lateinit var bloomFilterAdBlocker: BloomFilterAdBlocker

    private var recentSummaryUpdater: SummaryUpdater? = null
    private val compositeDisposable = CompositeDisposable()
    private var forceRefreshHostsPreference: Preference? = null

    private var abpDao: AbpDao? = null
    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_adblock
    }

    override fun providePreferencesXmlResource(): Int = R.xml.preference_ad_block

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

        checkBoxPreference(
            preference = "cb_block_ads",
            isChecked = userPreferences.adBlockEnabled,
            onCheckChange = { userPreferences.adBlockEnabled = it }
        )

        clickableDynamicPreference(
            preference = "preference_hosts_source",
            isEnabled = BuildConfig.FULL_VERSION,
            summary = if (BuildConfig.FULL_VERSION) {
                userPreferences.selectedHostsSource().toSummary()
            } else {
                getString(R.string.block_ads_upsell_source)
            },
            onClick = ::showHostsSourceChooser
        )

        forceRefreshHostsPreference = clickableDynamicPreference(
            preference = "preference_hosts_refresh_force",
            isEnabled = isRefreshHostsEnabled(),
            onClick = {
                bloomFilterAdBlocker.populateAdBlockerFromDataSource(forceRefresh = true)
            }
        )

        if (context != null) {
            abpDao = AbpDao(requireContext())
            for (entity in abpDao!!.getAll()) {
                val pref = Preference(context)
                pref.title = entity.title
                pref.summary = "Last update: ${entity.lastUpdate}"
                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showBlockist(entity)
                    true
                }
                this.preferenceScreen.addPreference(pref)
            }
            val pref = Preference(context)
            pref.title = "add list"
            pref.summary = "click to add"
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showBlockist(AbpEntity())
                true
            }
            this.preferenceScreen.addPreference(pref)
        }
    }

    private fun showBlockist(entity: AbpEntity) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("edit blocklist")
        val name = EditText(context)
        name.inputType = InputType.TYPE_CLASS_TEXT
        name.setText(entity.title)
        name.hint = "name"
        val url = EditText(context)
        url.inputType = InputType.TYPE_TEXT_VARIATION_URI
        url.setText(entity.url)
        url.hint = "url"
        // allow from file!
        val enabled = SwitchCompat(requireContext())
        enabled.text = "enabled"
        enabled.isChecked = entity.enabled

        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.addView(name)
        ll.addView(url)
        ll.addView(enabled)
        ll.setPadding(30,10,30,10)
        builder.setView(ll)
        builder.setNegativeButton("cancel", null)
        builder.setPositiveButton("ok") { _,_ ->
            entity.enabled = enabled.isChecked
            entity.title = name.text.toString()
            entity.url = url.text.toString() // better check validity?
            abpDao?.update(entity)
            // download updates immediately? should do, but i need the correct id in case a new list was added)
            // and actually update screen (title and new list!)
        }
        // one more button: "from file"
        // and another for delete?
//        builder.show()
        // need to adjust button colors, they are same as background by default (why?)
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GRAY)

    }

    private fun updateRefreshHostsEnabledStatus() {
        forceRefreshHostsPreference?.isEnabled = isRefreshHostsEnabled()
    }

    private fun isRefreshHostsEnabled() = userPreferences.selectedHostsSource() is HostsSourceType.Remote

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    private fun HostsSourceType.toSummary(): String = when (this) {
        HostsSourceType.Default -> getString(R.string.block_source_default)
        is HostsSourceType.Local -> getString(R.string.block_source_local_description, file.path)
        is HostsSourceType.Remote -> getString(R.string.block_source_remote_description, httpUrl)
    }

    private fun showHostsSourceChooser(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showListChoices(
                activity as Activity,
            R.string.block_ad_source,
            DialogItem(
                title = R.string.block_source_default,
                isConditionMet = userPreferences.selectedHostsSource() == HostsSourceType.Default,
                onClick = {
                    userPreferences.hostsSource = HostsSourceType.Default.toPreferenceIndex()
                    summaryUpdater.updateSummary(userPreferences.selectedHostsSource().toSummary())
                    updateForNewHostsSource()
                }
            ),
            DialogItem(
                title = R.string.block_source_local,
                isConditionMet = userPreferences.selectedHostsSource() is HostsSourceType.Local,
                onClick = {
                    showFileChooser(summaryUpdater)
                }
            ),
            DialogItem(
                title = R.string.block_source_remote,
                isConditionMet = userPreferences.selectedHostsSource() is HostsSourceType.Remote,
                onClick = {
                    showUrlChooser(summaryUpdater)
                }
            )
        )
    }

    private fun showFileChooser(summaryUpdater: SummaryUpdater) {
        this.recentSummaryUpdater = summaryUpdater
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = TEXT_MIME_TYPE
        }

        startActivityForResult(intent, FILE_REQUEST_CODE)
    }

    private fun showUrlChooser(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showEditText(
                activity as Activity,
            title = R.string.block_source_remote,
            hint = R.string.hint_url,
            currentText = userPreferences.hostsRemoteFile,
            action = R.string.action_ok,
            textInputListener = {
                val url = it.toHttpUrlOrNull()
                    ?: return@showEditText run { activity?.toast(R.string.problem_download) }
                userPreferences.hostsSource = HostsSourceType.Remote(url).toPreferenceIndex()
                userPreferences.hostsRemoteFile = it
                summaryUpdater.updateSummary(it)
                updateForNewHostsSource()
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                data?.data?.also { uri ->
                    compositeDisposable += readTextFromUri(uri)
                        .subscribeOn(diskScheduler)
                        .observeOn(mainScheduler)
                        .subscribeBy(
                            onComplete = { activity?.toast(R.string.action_message_canceled) },
                            onSuccess = { file ->
                                userPreferences.hostsSource = HostsSourceType.Local(file).toPreferenceIndex()
                                userPreferences.hostsLocalFile = file.path
                                recentSummaryUpdater?.updateSummary(userPreferences.selectedHostsSource().toSummary())
                                updateForNewHostsSource()
                            }
                        )
                }
            } else {
                activity?.toast(R.string.action_message_canceled)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateForNewHostsSource() {
        bloomFilterAdBlocker.populateAdBlockerFromDataSource(forceRefresh = true)
        updateRefreshHostsEnabledStatus()
    }

    private fun readTextFromUri(uri: Uri): Maybe<File> = Maybe.create {
        val externalFilesDir = activity?.getExternalFilesDir("")
            ?: return@create it.onComplete()
        val inputStream = activity?.contentResolver?.openInputStream(uri)
            ?: return@create it.onComplete()

        try {
            val outputFile = File(externalFilesDir, AD_HOSTS_FILE)

            val input = inputStream.source()
            val output = outputFile.sink().buffer()
            output.writeAll(input)
            return@create it.onSuccess(outputFile)
        } catch (exception: IOException) {
            return@create it.onComplete()
        }
    }

    companion object {
        private const val FILE_REQUEST_CODE = 100
        private const val AD_HOSTS_FILE = "local_hosts.txt"
        private const val TEXT_MIME_TYPE = "text/*"
    }
}
