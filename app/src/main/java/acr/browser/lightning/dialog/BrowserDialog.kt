/*
 * Copyright 7/31/2016 Anthony Restaino
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package acr.browser.lightning.dialog

import acr.browser.lightning.R
import acr.browser.lightning.extensions.*
import acr.browser.lightning.list.RecyclerViewDialogItemAdapter
import acr.browser.lightning.list.RecyclerViewStringAdapter
import acr.browser.lightning.utils.DeviceUtils
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

object BrowserDialog {

    @JvmStatic
    fun show(
        activity: Activity,
        @StringRes title: Int,
        vararg items: DialogItem
    ) = show(activity, activity.getString(title), *items)

    fun showWithIcons(context: Context, title: String?, vararg items: DialogItem) {
        val builder = MaterialAlertDialogBuilder(context)

        val layout = context.inflater.inflate(R.layout.list_dialog, null)

        val titleView = layout.findViewById<TextView>(R.id.dialog_title)
        val recyclerView = layout.findViewById<RecyclerView>(R.id.dialog_list)

        val itemList = items.filter(DialogItem::show)

        val adapter = RecyclerViewDialogItemAdapter(itemList)

        if (title?.isNotEmpty() == true) {
            titleView.text = title
        }

        recyclerView.apply {
            this.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            this.adapter = adapter
            setHasFixedSize(true)
        }

        builder.setView(layout)

        val dialog = builder.resizeAndShow()

        adapter.onItemClickListener = { item ->
            item.onClick()
            dialog.dismiss()
        }
    }

    /**
     * Show a singly selectable list of [DialogItem] with the provided [title]. All items will be
     * shown, and the first [DialogItem] where [DialogItem.show] returns `true` will be
     * the selected item when the dialog is shown. The dialog has an OK button which just dismisses
     * the dialog.
     */
    fun showListChoices(activity: Activity, @StringRes title: Int, vararg items: DialogItem) {
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(title)

            val choices = items.map { activity.getString(it.title) }.toTypedArray()
            val currentChoice = items.indexOfFirst(DialogItem::show)

            setSingleChoiceItems(choices, currentChoice) { _, which ->
                items[which].onClick()
            }
            setPositiveButton(activity.getString(R.string.action_ok), null)
        }.resizeAndShow()
    }

    @JvmStatic
    fun show(activity: Activity, title: String?, vararg items: DialogItem) {
        val builder = MaterialAlertDialogBuilder(activity)

        val layout = activity.inflater.inflate(R.layout.list_dialog, null)

        val titleView = layout.findViewById<TextView>(R.id.dialog_title)
        val recyclerView = layout.findViewById<RecyclerView>(R.id.dialog_list)

        val itemList = items.filter(DialogItem::show)

        val adapter = RecyclerViewStringAdapter(itemList, convertToString = { activity.getString(this.title) })

        if (title?.isNotEmpty() == true) {
            titleView.text = title
        }

        recyclerView.apply {
            this.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            this.adapter = adapter
            setHasFixedSize(true)
        }

        builder.setView(layout)

        val dialog = builder.resizeAndShow()

        adapter.onItemClickListener = { item ->
            item.onClick()
            dialog.dismiss()
        }
    }

    /**
     * Build and show a tabbed dialog based on the provided parameters.
     *
     * @param aActivity The activity requesting that dialog.
     * @param aTitle The dialog title.
     * @param aHideSingleTab Set to true to hide tab layout when a single tab is visible.
     * @param aTabs Define our dialog's tabs.
     */
    @JvmStatic
    fun show(aActivity: Activity, aTitle: String?, aHideSingleTab: Boolean, vararg aTabs: DialogTab) {
        val builder = MaterialAlertDialogBuilder(aActivity)

        // Inflate our layout
        val layout = aActivity.inflater.inflate(R.layout.dialog_tabs, null)
        // Fetch the view we will need to use
        val titleView = layout.findViewById<TextView>(R.id.dialog_title)
        val tabLayout = layout.findViewById<TabLayout>(R.id.dialog_tab_layout)
        val pager = layout.findViewById<ViewPager>(R.id.dialog_viewpager)

        // Set dialog title
        // TODO: Should we rather or also use the actual dialog title? See: AlertDialog.setTitle.
        if (aTitle?.isNotEmpty() == true) {
            titleView.text = aTitle
        } else {
            titleView.isVisible = false
        }

        // Filter out invisible tabs
        val tabList = aTabs.filter(DialogTab::show)
        // Hide our tab layout out if needed
        tabLayout.isVisible = !(aHideSingleTab && tabList.count() == 1)
        // Create our dialog now as our adapter needs it
        val dialog = builder.create()
        // Create our adapter which will be creating our tabs content
        pager.adapter = TabsPagerAdapter(aActivity, dialog, tabList)
        // Hook-in our adapter with our tab layout
        tabLayout.setupWithViewPager(pager)
        // Add icons to our tabs
        var i: Int = 0
        tabList.forEach {
            tabLayout.getTabAt(i)?.setIcon(it.icon)
            i++
        }
        // Our layout is setup, just hook it to our dialog
        dialog.setView(layout)
        setDialogSize(aActivity, dialog)
        dialog.show()
        //builder.resizeAndShow()

        // We want our dialog to close after a configuration change since the resizing is not working properly.
        // We use a bit of magic there to achieve that.
        // After the initial layout we will be closing that dialog next time its size is changed.
        // TODO: Instead of that workaround, find a way to resize our dialogs properly after screen rotation
        layout.onLayoutChange {layout.onSizeChange {dialog.dismiss()}}
    }


    @JvmStatic
    fun showPositiveNegativeDialog(
        activity: Activity,
        @StringRes title: Int,
        @StringRes message: Int,
        messageArguments: Array<Any>? = null,
        positiveButton: DialogItem,
        negativeButton: DialogItem,
        onCancel: () -> Unit
    ) {
        val messageValue = if (messageArguments != null) {
            activity.getString(message, *messageArguments)
        } else {
            activity.getString(message)
        }
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(title)
            setMessage(messageValue)
            setOnCancelListener { onCancel() }
            setPositiveButton(positiveButton.title) { _, _ -> positiveButton.onClick() }
            setNegativeButton(negativeButton.title) { _, _ -> negativeButton.onClick() }
        }.resizeAndShow()
    }

    @JvmStatic
    fun showEditText(
        activity: Activity,
        @StringRes title: Int,
        @StringRes hint: Int,
        @StringRes action: Int,
        textInputListener: (String) -> Unit
    ) = showEditText(activity, title, hint, null, action, textInputListener)

    @JvmStatic
    fun showEditText(
        activity: Activity,
        @StringRes title: Int,
        @StringRes hint: Int,
        currentText: String?,
        @StringRes action: Int,
        textInputListener: (String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<EditText>(R.id.dialog_edit_text)

        editText.setHint(hint)
        if (currentText != null) {
            editText.setText(currentText)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(action
            ) { _, _ -> textInputListener(editText.text.toString()) }
            .resizeAndShow()
    }

    @JvmStatic
    fun setDialogSize(context: Context, dialog: Dialog) {

        // SL: That was really dumb so we comment it out

        /*
        val padding = context.dimen(R.dimen.dialog_padding)
        val screenSize = DeviceUtils.getScreenWidth(context)
        if (maxWidth > screenSize - 2 * padding) {
            maxWidth = screenSize - 2 * padding
        }*/

        //dialog.window?.setLayout(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        //var maxHeight = context.dimen(R.dimen.dialog_max_height)
        //dialog.window?.setLayout(dialog.window?.attributes!!.width, maxHeight)
    }

    /**
     * Show the custom dialog with the custom builder arguments applied.
     */
    fun showCustomDialog(activity: Activity, block: MaterialAlertDialogBuilder.(Activity) -> Unit) : Dialog {
            MaterialAlertDialogBuilder(activity).apply {
                block(activity)
                return resizeAndShow()
            }
    }

}
