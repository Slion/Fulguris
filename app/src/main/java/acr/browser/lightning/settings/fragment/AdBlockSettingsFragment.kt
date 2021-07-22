package acr.browser.lightning.settings.fragment

import acr.browser.lightning.R
import acr.browser.lightning.adblock.AbpBlocker
import acr.browser.lightning.adblock.AbpListUpdater
import acr.browser.lightning.adblock.AbpUpdateMode
//import acr.browser.lightning.adblock.BloomFilterAdBlocker
import acr.browser.lightning.constant.Schemes
import acr.browser.lightning.di.DiskScheduler
import acr.browser.lightning.di.MainScheduler
import acr.browser.lightning.di.injector
import acr.browser.lightning.extensions.resizeAndShow
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.extensions.withSingleChoiceItems
import acr.browser.lightning.settings.preferences.UserPreferences
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import kotlinx.coroutines.*
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
//    @Inject internal lateinit var bloomFilterAdBlocker: BloomFilterAdBlocker
    @Inject internal lateinit var abpListUpdater: AbpListUpdater
    @Inject internal lateinit var abpBlocker: AbpBlocker

    private val compositeDisposable = CompositeDisposable()

    private lateinit var abpDao: AbpDao
    private val entitiyPrefs = mutableMapOf<Int, Preference>()

    // if blocklist changed, they need to be reloaded, but this should happen only once
    //  if reloadLists is true, list reload will be launched onDestroy
    private var reloadLists = false

    // updater is launched in background, and lists should not be reloaded while updater is running
    //  int since multiple lists could be updated at the same time
    private var updatesRunning = 0

    // uri of temporary blocklist file
    private var fileUri: Uri? = null

    // Our preferences filters category, will contains our filters file entries
    private lateinit var filtersCategory: PreferenceGroup

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

        switchPreference(
            preference = getString(R.string.pref_key_content_control),
            isChecked = userPreferences.adBlockEnabled,
            onCheckChange = {
                userPreferences.adBlockEnabled = it
                // update enabled lists when enabling blocker
                if (it) updateEntity(null, false)
            }
        )

        filtersCategory = findPreference<PreferenceGroup>(getString(R.string.pref_key_content_control_filters))!!

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
                        }?.resizeAndShow()
                }
            )

            clickableDynamicPreference(
                preference = getString(R.string.pref_key_blocklist_auto_update_frequency),
                summary = userPreferences.blockListAutoUpdateFrequency.toUpdateFrequency(),
                onClick = { summaryUpdater ->
                    activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
                        setTitle(R.string.blocklist_update_frequency)
                        // TODO: can't show message and singleChoice at the same time -> what do?
                        //setMessage(R.string.blocklist_update_description)
                        val values = listOf(
                            Pair(1, resources.getString(R.string.block_remote_frequency_daily)),
                            Pair(7, resources.getString(R.string.block_remote_frequency_weekly)),
                            Pair(30, resources.getString(R.string.block_remote_frequency_monthly))
                        )
                        withSingleChoiceItems(values, userPreferences.blockListAutoUpdateFrequency) {
                            userPreferences.blockListAutoUpdateFrequency = it
                            summaryUpdater.updateSummary(it.toUpdateFrequency())
                        }
                        setPositiveButton(resources.getString(R.string.action_ok), null)
                        setNeutralButton(R.string.blocklist_update_now) {_,_ ->
                            updateEntity(null, true)
                        }
                    }?.resizeAndShow()
                }
            )

            // "new list" button
            val newList = Preference(context)
            newList.title = resources.getString(R.string.add_blocklist)
            newList.icon = ResourcesCompat.getDrawable(resources,R.drawable.ic_action_plus,requireActivity().theme)
            newList.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setNeutralButton(R.string.action_cancel, null) // actually the negative button, but looks nicer this way
                    .setNegativeButton(R.string.local_file) { _,_ -> showBlockList(AbpEntity(url = "file")) }
                    .setPositiveButton(R.string.remote_file) { _,_ -> showBlockList(AbpEntity(url = "")) }
                    .setTitle(R.string.add_blocklist)
                    .setMessage(R.string.add_blocklist_hint)
                    .create()
                dialog.show()
                true
            }
            filtersCategory.addPreference(newList)
            newList.dependency = getString(R.string.pref_key_content_control)

            // list of blocklists/entities
            for (entity in abpDao.getAll()) {
                val entityPref = Preference(context)
//                val pref = SwitchPreferenceCompat(context) // not working... is there a way to separate clicks on text and switch?
//                pref.isChecked = entity.enabled
                entityPref.title = entity.title
                entityPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showBlockList(entity)
                    true
                }
                entitiyPrefs[entity.entityId] = entityPref
                updateSummary(entity)
                filtersCategory.addPreference(entitiyPrefs[entity.entityId])
                entityPref.dependency = getString(R.string.pref_key_content_control)
            }
        }
    }

    private fun updateSummary(entity: AbpEntity) {
        if (!entity.url.startsWith(Schemes.Fulguris) && entity.lastLocalUpdate > 0)
            entitiyPrefs[entity.entityId]?.summary = resources.getString(R.string.blocklist_last_update, DateFormat.getDateTimeInstance().format(Date(entity.lastLocalUpdate)))
    }

    // update entity and adjust displayed last update time
    private fun updateEntity(abpEntity: AbpEntity?, forceUpdate: Boolean) {
        GlobalScope.launch(Dispatchers.IO) {
            ++updatesRunning
            val updated = if (abpEntity == null) abpListUpdater.updateAll(forceUpdate) else abpListUpdater.updateAbpEntity(abpEntity, forceUpdate)
            if (updated) {
                reloadBlockLists()

                // update the "last updated" times
                activity?.runOnUiThread {
                    for (entity in abpDao.getAll())
                        updateSummary(entity)
                }
            }
            --updatesRunning
        }
    }


    private fun showBlockList(entity: AbpEntity) {
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

        var needsUpdate = false

        // field for choosing file or url
        when {
            entity.url.startsWith(Schemes.Fulguris) -> {
                val text = TextView(context)
                text.text = resources.getString(R.string.blocklist_built_in)
                linearLayout.addView(text)
            }
            entity.url.startsWith("file") -> {
                val fileChooseButton = Button(context)
                fileChooseButton.text = if (entity.url == "file") getString(R.string.title_chooser)
                    else getString(R.string.local_file_replace)
                fileChooseButton.setOnClickListener {
                    // show file chooser
                    //  no storage permission necessary
                    fileUri = null
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = TEXT_MIME_TYPE
                    }
                    startActivityForResult(intent, FILE_REQUEST_CODE)

                    // wait until file was chosen
                    lifecycleScope.launch {
                        while (fileUri == null)
                            delay(200)

                        // don't update if it's the fake http uri provided on file chooser cancel
                        if (fileUri?.scheme == "file") {
                            entity.url = fileUri.toString()
                            updateButton(dialog?.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
                            fileChooseButton.text = getString(R.string.local_file_chosen)
                            needsUpdate = true
                        }
                    }
                }
                linearLayout.addView(fileChooseButton)
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
                        abpDao.delete(entity)
                        dialog?.dismiss()
                        preferenceScreen.removePreference(entitiyPrefs[entity.entityId])
                        reloadBlockLists()
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
            val newId = abpDao.update(entity) // new id if new entity was added, otherwise newId == entity.entityId

            // set new id for newly added list
            if (entity.entityId == 0) {
                entity.entityId = newId
                needsUpdate = true
            }

            // check for update (necessary to have correct id!)
            if ((entity.url.startsWith("http") && enabled.isChecked && !wasEnabled) || needsUpdate)
                updateEntity(entity, needsUpdate)
            if (enabled.isChecked != wasEnabled)
                reloadBlockLists()

            if (entitiyPrefs[newId] == null) { // not in entityPrefs if new
                val pref = Preference(context)
                entity.entityId = newId
                pref.title = entity.title
                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    showBlockList(entity)
                    true
                }
                entitiyPrefs[newId] = pref
                updateSummary(entity)
                filtersCategory.addPreference(pref)
                pref.dependency = getString(R.string.pref_key_content_control)
            } else
                entitiyPrefs[entity.entityId]?.title = entity.title
        }
        dialog = builder.create()
        dialog.show()
        updateButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
    }

    // list should be reloaded only once
    //  this is done when leaving settings screen
    //  joint lists are removed immediately to avoid using them if app is stopped without leaving the setting screen
    private fun reloadBlockLists() {
        reloadLists = true
        abpBlocker.removeJointLists()
    }

    // disable ok button if url or title not valid
    private fun updateButton(button: Button?, url: String, title: String?) {
        if (title?.contains("§§") == true || title.isNullOrBlank()) {
            button?.text = resources.getText(R.string.invalid_title)
            button?.isEnabled = false
            return
        }
        // TODO: if user enters 'file:' or 'fulguris' for a http url, ok button will be shown (of course update will fail)
        if ((url.toHttpUrlOrNull() == null || url.contains("§§")) && !url.startsWith(Schemes.Fulguris) && !url.startsWith("file:")) {
            button?.text = if (url.startsWith("file")) "no file chosen" else resources.getText(R.string.invalid_url)
            button?.isEnabled = false
            return
        }
        button?.text = resources.getString(R.string.action_ok)
        button?.isEnabled = true
    }

    private fun AbpUpdateMode.toDisplayString(): String = getString(when (this) {
        AbpUpdateMode.NONE -> R.string.blocklist_update_off
        AbpUpdateMode.WIFI_ONLY -> R.string.blocklist_update_wifi
        AbpUpdateMode.ALWAYS -> R.string.blocklist_update_on
    })

    private fun Int.toUpdateFrequency() = when(this) {
        1 -> resources.getString(R.string.block_remote_frequency_daily)
        7 -> resources.getString(R.string.block_remote_frequency_weekly)
        30 -> resources.getString(R.string.block_remote_frequency_monthly)
        else -> "" //should not happen
    }


override fun onDestroy() {
        super.onDestroy()
        // reload lists after updates are done
        if (reloadLists || updatesRunning > 0) {
            GlobalScope.launch(Dispatchers.Default) {
                while (updatesRunning > 0)
                    delay(200)
                if (reloadLists)
                    abpBlocker.loadLists()
            }
        }
        compositeDisposable.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val dataUri = data?.data ?: return
                val cacheDir = activity?.externalCacheDir ?: return
                val inputStream = activity?.contentResolver?.openInputStream(dataUri) ?: return
                try {
                    // copy file to temporary file, like done by lightning
                    val outputFile = File(cacheDir, BLOCK_LIST_FILE)
                    val input = inputStream.source()
                    outputFile.sink().buffer().writeAll(input)
                    fileUri = Uri.fromFile(outputFile)
                    return
                } catch (exception: IOException) {
                    return
                }
            } else {
                activity?.toast(R.string.action_message_canceled)
                // set some fake uri to cancel wait-for-file loop
                fileUri = Uri.parse("http://no.file")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        private const val FILE_REQUEST_CODE = 100
        private const val BLOCK_LIST_FILE = "local_blocklist.txt"
        private const val TEXT_MIME_TYPE = "text/*"
    }
}
