package acr.browser.lightning.settings.fragment

import acr.browser.lightning.BuildConfig
import acr.browser.lightning.R
import acr.browser.lightning.adblock.AbpListUpdater
import acr.browser.lightning.adblock.AbpUpdateMode
import acr.browser.lightning.adblock.BloomFilterAdBlocker
import acr.browser.lightning.adblock.source.HostsSourceType
import acr.browser.lightning.adblock.source.selectedHostsSource
import acr.browser.lightning.adblock.source.toPreferenceIndex
import acr.browser.lightning.constant.Schemes
import acr.browser.lightning.di.DiskScheduler
import acr.browser.lightning.di.MainScheduler
import acr.browser.lightning.di.injector
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.dialog.DialogItem
import acr.browser.lightning.extensions.resizeAndShow
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.extensions.withSingleChoiceItems
import acr.browser.lightning.preference.UserPreferences
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.addTextChangedListener
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Maybe
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.*
import javax.inject.Inject

/**
 * Settings for the ad block mechanic.
 */
class AdBlockSettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject @field:MainScheduler internal lateinit var mainScheduler: Scheduler
    @Inject @field:DiskScheduler internal lateinit var diskScheduler: Scheduler
    @Inject internal lateinit var bloomFilterAdBlocker: BloomFilterAdBlocker
    @Inject internal lateinit var abpListUpdater: AbpListUpdater

    private var recentSummaryUpdater: SummaryUpdater? = null
    private val compositeDisposable = CompositeDisposable()
    private var forceRefreshHostsPreference: Preference? = null

    private var abpDao: AbpDao? = null
    private val entitiyPrefs = mutableMapOf<Int, Preference>()
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

            clickableDynamicPreference(
                preference = getString(R.string.pref_key_blocklist_auto_update),
                summary = userPreferences.blockListAutoUpdate.toDisplayString(),
                onClick = { summaryUpdater ->
                        activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
                            setTitle(R.string.blocklist_update)
                            val values = AbpUpdateMode.values().map { Pair(it, it.toDisplayString()) }
                            withSingleChoiceItems(values, userPreferences.blockListAutoUpdate) {
                                userPreferences.blockListAutoUpdate = it
                                summaryUpdater.updateSummary(it.toDisplayString())
                            }
                            setPositiveButton(resources.getString(R.string.action_ok), null)
                            setNeutralButton(R.string.blocklist_update_now) {_,_ ->
                                GlobalScope.launch(Dispatchers.IO) {
                                    abpListUpdater.updateAll(true)
                                }
                            }
                        }?.resizeAndShow()
                }
            )

            // "new list" button
            val newList = Preference(context)
            newList.title = resources.getString(R.string.add_blocklist)
            newList.icon = resources.getDrawable(R.drawable.ic_action_plus)
            newList.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setNeutralButton(R.string.action_cancel, null) // actually the negative button, but looks nicer this way
                    .setNegativeButton(R.string.local_file) { _,_ -> showBlockist(AbpEntity(url = "file")) }
                    .setPositiveButton(R.string.remote_file) { _,_ -> showBlockist(AbpEntity(url = "")) }
                    .setTitle(R.string.add_blocklist)
                    .create()
                dialog.show()
                true
            }
            this.preferenceScreen.addPreference(newList)

            // list of blocklists/entities
            for (entity in abpDao!!.getAll()) {
                val entityPref = Preference(context)
//                val pref = SwitchPreferenceCompat(context) // not working... is there a way to separate clicks on text and switch?
//                pref.isChecked = entity.enabled
                entityPref.title = entity.title
                if (!entity.url.startsWith(Schemes.Fulguris) && entity.lastLocalUpdate > 0)
                    entityPref.summary = resources.getString(R.string.blocklist_last_update, DateFormat.getDateInstance().format(Date(entity.lastLocalUpdate)))
                entityPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showBlockist(entity)
                    true
                }
                entitiyPrefs[entity.entityId] = entityPref
                this.preferenceScreen.addPreference(entitiyPrefs[entity.entityId])
            }
        }
    }

    private fun showBlockist(entity: AbpEntity) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        var dialog: AlertDialog? = null
        builder.setTitle(R.string.action_edit)
        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL

        // edit field for blocklist title
        val title = EditText(context)
        title.inputType = InputType.TYPE_CLASS_TEXT
        title.setText(entity.title)
        title.hint = getString(R.string.hint_title)
        title.addTextChangedListener {
            entity.title = it.toString()
            updateButton(dialog?.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
        }
        linearLayout.addView(title)

        // field for choosing file or url
        when {
            entity.url.startsWith(Schemes.Fulguris) -> {
                val text = TextView(context)
                text.text = resources.getString(R.string.blocklist_built_in)
                linearLayout.addView(text)
            }
            entity.url.startsWith("file") -> {
                val updateButton = Button(context)
                updateButton.text = getString(R.string.title_chooser)
                updateButton.setOnClickListener {
                    // TODO: choose file -> check host file chooser and original implementation in yuzu
                    //  and: if no valid file provided, ok button should not be visible
                    // so... i can show file chooser, and onActivityResult will we called when the file is chosen
                    // but how to get the file to the button (in the dialog!)
                    //  i need the uri, or the content
                    //  uri would be nicer, then i can read it when i want
                    //  and i can display the file name
                    // not so great but should work:
                    //  disable button if no valid name
                    //  store entity properties in some temp variable accessible from onActivityResult
                    //  on (valid!) file chosen, close dialog and immediately add entity
                }
                linearLayout.addView(updateButton)
            }
            entity.url.toHttpUrlOrNull() != null || entity.url == "" -> {
                val url = EditText(context)
                url.inputType = InputType.TYPE_TEXT_VARIATION_URI
                url.setText(entity.url)
                url.hint = getString(R.string.hint_url)
                url.addTextChangedListener {
                    entity.url = it.toString()
                    updateButton(dialog?.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
                }
                linearLayout.addView(url)
            }
        }

        // enabled switch
        val enabled = SwitchCompat(requireContext())
        enabled.text = resources.getString(R.string.enable_blocklist)
        enabled.isChecked = entity.enabled
        linearLayout.addView(enabled)

        // delete button
        // don't show for internal list or when creating a new entity
        if (entity.entityId != 0 && !entity.url.startsWith(Schemes.Fulguris)) {
            val delete = Button(context) // looks ugly, but works
            delete.text = resources.getString(R.string.blocklist_remove)
            // confirm deletion!
            delete.setOnClickListener {
                val confirmDialog = MaterialAlertDialogBuilder(requireContext())
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        abpDao?.delete(entity)
                        dialog?.dismiss()
                        preferenceScreen.removePreference(entitiyPrefs[entity.entityId])
                    }
                    .setTitle(resources.getString(R.string.blocklist_remove_confirm, entity.title))
                    .create()
                confirmDialog.show()
            }
            linearLayout.addView(delete)
        }

        // arbitrary numbers that look ok on my phone -> ok for other phones?
        linearLayout.setPadding(30,10,30,10)

        builder.setView(linearLayout)
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.setPositiveButton(R.string.action_ok) { _,_ ->
            val wasEnabled = entity.enabled
            entity.enabled = enabled.isChecked

            entity.title = title.text.toString()
            val newId = abpDao?.update(entity) // new id if new entity was added

            // set new id for newly added list
            if (entity.entityId == 0 && newId != null)
                entity.entityId = newId

            // check for update (necessary to have correct id!)
            if (entity.url.startsWith("http") && enabled.isChecked && !wasEnabled)
                GlobalScope.launch(Dispatchers.IO) {
                    abpListUpdater.updateAbpEntity(entity)
                }

            if (newId != null && entitiyPrefs[newId] == null) { // not in entityPrefs if new
                val pref = Preference(context)
                entity.entityId = newId
                pref.title = entity.title
                if (!entity.url.startsWith(Schemes.Fulguris) && entity.lastLocalUpdate > 0)
                    pref.summary = resources.getString(R.string.blocklist_last_update, DateFormat.getDateInstance().format(Date(entity.lastLocalUpdate)))
                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showBlockist(entity)
                    true
                }
                entitiyPrefs[newId] = pref
                preferenceScreen.addPreference(entitiyPrefs[newId])
            } else
                entitiyPrefs[entity.entityId]?.title = entity.title
        }
        dialog = builder.create()
        dialog.show()
        updateButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
    }

    // disable ok button if url or title not valid
    private fun updateButton(button: Button?, url: String, title: String?) {
        if (title?.contains("§§") == true || title.isNullOrBlank()) {
            button?.text = resources.getText(R.string.invalid_title)
            button?.isEnabled = false
            return
        }
        if ((url.toHttpUrlOrNull() == null || url.contains("§§")) && !url.startsWith(Schemes.Fulguris) && !url.startsWith("file")) {
            button?.text = resources.getText(R.string.invalid_url)
            button?.isEnabled = false
            return
        }
        button?.text = resources.getString(R.string.action_ok)
        button?.isEnabled = true
    }

    private fun AbpUpdateMode.toDisplayString(): String = getString(when (this) {
        AbpUpdateMode.NONE -> R.string.abp_update_off
        AbpUpdateMode.WIFI_ONLY -> R.string.abp_update_wifi
        AbpUpdateMode.ALWAYS -> R.string.abp_update_on
    })

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
