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
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.text.InputType
import android.widget.*
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

            // TODO: does it work?
            clickableDynamicPreference(
                preference = getString(R.string.pref_key_blocklist_auto_update),
                summary = userPreferences.blockListAutoUpdate.toDisplayString(),
                onClick = { summaryUpdater ->
                        activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
                            setTitle("update mode")
                            val values = AbpUpdateMode.values().map { Pair(it, it.toDisplayString()) }
                            withSingleChoiceItems(values, userPreferences.blockListAutoUpdate) {
                                userPreferences.blockListAutoUpdate = it
                                summaryUpdater.updateSummary(it.toDisplayString())
                            }
                            setPositiveButton(resources.getString(R.string.action_ok), null)
                            setNeutralButton("update all now") {_,_ ->
                                // TODO: background! (or is it?)
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
                val dialog = AlertDialog.Builder(context)
                    .setNeutralButton(R.string.action_cancel, null) // actually the negative button, but looks nicer this way
                    .setNegativeButton("from file") { _,_ -> showBlockist(AbpEntity(url = "file")) }
                    .setPositiveButton("from url") { _,_ -> showBlockist(AbpEntity(url = "")) }
                    .setTitle(R.string.add_blocklist)
                    .create()
                dialog.show()
                // TODO: avoid manually setting button color, but how to do it correctly?
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.GRAY)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GRAY)
                true
            }
            this.preferenceScreen.addPreference(newList)

            // list of blocklists/entities
            for (entity in abpDao!!.getAll()) {
                val entityPref = Preference(context)
//                val pref = SwitchPreferenceCompat(context) // not working... is there a way to separate clicks on text and switch?
//                pref.isChecked = entity.enabled
                entityPref.title = entity.title
                if (!entity.url.startsWith(Schemes.Fulguris))
                    entityPref.summary = "Last update: ${entity.lastModified}" // this is local file update date, last update is taken from the list
                entityPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showBlockist(entity)
                    true
                }
                entitiyPrefs[entity.entityId] = entityPref
                this.preferenceScreen.addPreference(entitiyPrefs[entity.entityId])
            }
        }
    }

    private fun AbpUpdateMode.toDisplayString(): String = getString(when (this) {
        AbpUpdateMode.NONE -> R.string.abp_update_off
        AbpUpdateMode.WIFI_ONLY -> R.string.abp_update_wifi
        AbpUpdateMode.ALWAYS -> R.string.abp_update_on
    })

    // TODO: this is getting too large, and has duplicates -> clean up
    private fun showBlockist(entity: AbpEntity) {
        val builder = AlertDialog.Builder(context)
        var dialog: AlertDialog? = null
        builder.setTitle("edit blocklist")
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL

        val name = EditText(context)
        name.inputType = InputType.TYPE_CLASS_TEXT
        name.setText(entity.title)
        name.hint = getString(R.string.hint_title)
        ll.addView(name)
        // some listener that checks for §§? or do when clicking ok?

        when {
            entity.url.startsWith(Schemes.Fulguris) -> {
                val text = TextView(context)
                text.text = "internal list"
                ll.addView(text)
            }
            entity.url.startsWith("file") -> {
                val updateButton = Button(context)
                updateButton.text = getString(R.string.title_chooser)
                updateButton.setOnClickListener {
                    // TODO: choose file -> check host file chooser and original implementation in yuzu
                    //  and: if no valid file provided, ok button should not be visible
                    }
                ll.addView(updateButton)
            }
            entity.url.toHttpUrlOrNull() != null || entity.url == "" -> {
                val url = EditText(context)
                url.inputType = InputType.TYPE_TEXT_VARIATION_URI
                url.setText(entity.url)
                url.hint = getString(R.string.hint_url)
                url.addTextChangedListener {
                    entity.url = it.toString()
                    // disable ok button if url not valid
                    dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = it.toString().toHttpUrlOrNull() != null
                    dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.text = if (it.toString().toHttpUrlOrNull() != null)
                        resources.getString(R.string.action_ok)
                    else
                        "invalid url"
                }
                ll.addView(url)
            }
        }

        val enabled = SwitchCompat(requireContext())
        enabled.text = "enabled"
        enabled.isChecked = entity.enabled
        ll.addView(enabled)

        // only show if not creating a new entity
        if (entity.entityId != 0 && !entity.url.startsWith(Schemes.Fulguris)) {
            val delete = Button(context) // looks ugly, but works
            delete.text = "delete list"
            delete.setOnClickListener {
                // confirm delete!
                val really = AlertDialog.Builder(context)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton("delete") { _, _ ->
                        abpDao?.delete(entity)
                        dialog?.dismiss()
                        preferenceScreen.removePreference(entitiyPrefs[entity.entityId])
                    }
                    .setTitle("really delete list ${entity.title}?")
                    .create()
                really.show()
                really.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
                really.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GRAY)
            }
            ll.addView(delete)
        }

        // arbitrary numbers that look ok on my phone -> ok for other phones?
        //  what unit is this? dp? px?
        ll.setPadding(30,10,30,10)
        builder.setView(ll)
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.setPositiveButton(R.string.action_ok) { _,_ ->
            if (name.text.toString().contains("§§") || entity.url.contains("§§"))
                AlertDialog.Builder(context) // how to not accept click on ok? menu dialog should remain open
            // so better disable the ok button as long as §§ is found? but needs some kind of text change listener
            // -> https://stackoverflow.com/questions/2620444/how-to-prevent-a-dialog-from-closing-when-a-button-is-clicked
            //  or add another addTextChangedListener for the name text?
            //  but then take care that always both texts are ok!

            val wasEnabled = entity.enabled
            entity.enabled = enabled.isChecked

            entity.title = name.text.toString()
            val newId = abpDao?.update(entity) // new id if new entity was added

            // set new id for newly added list
            if (entity.entityId == 0 && newId != null)
                entity.entityId = newId

            // check for update (necessary to have correct id!)
            if (enabled.isChecked && !wasEnabled)
                GlobalScope.launch(Dispatchers.IO) {
                    abpListUpdater.updateAbpEntity(entity)
                }

            if (newId != null && entitiyPrefs[newId] == null) { // not in entityPrefs if new
                val pref = Preference(context)
                entity.entityId = newId
                pref.title = entity.title
                if (!entity.url.startsWith(Schemes.Fulguris))
                    pref.summary = "Last update: ${entity.lastModified}"
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
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GRAY)
        if (entity.url == "") { // editText field is not checked on start
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "invalid url"
        }

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
