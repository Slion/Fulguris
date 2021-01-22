package acr.browser.lightning.browser.sessions

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.databinding.SessionListBinding
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.extensions.dimBehind
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.utils.FileNameInputFilter
import acr.browser.lightning.utils.ItemDragDropSwipeHelper
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.view.*
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.PopupWindow
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class SessionsPopupWindow : PopupWindow {

    var iUiController: UIController
    var iAdapter: SessionsAdapter
    var iBinding: SessionListBinding
    private var iItemTouchHelper: ItemTouchHelper? = null

    constructor(layoutInflater: LayoutInflater,
                aBinding: SessionListBinding = SessionListBinding.inflate(layoutInflater))
            : super(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

        //view.context.injector.inject(this)

        iBinding = aBinding
        iUiController = aBinding.root.context as UIController
        iAdapter = SessionsAdapter(iUiController)

        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog

        // Needed on Android 5 to make sure our pop-up can be dismissed by tapping outside and back button
        // See: https://stackoverflow.com/questions/46872634/close-popupwindow-upon-tapping-outside-or-back-button
        setBackgroundDrawable(ColorDrawable())


        // Handle click on "add session" button
        aBinding.buttonNewSession.setOnClickListener { view ->
                val dialogView = LayoutInflater.from(aBinding.root.context).inflate(R.layout.dialog_edit_text, null)
                val textView = dialogView.findViewById<EditText>(R.id.dialog_edit_text)
                // Make sure user can only enter valid filename characters
                textView.filters = arrayOf<InputFilter>(FileNameInputFilter())

                BrowserDialog.showCustomDialog(aBinding.root.context as Activity) {
                    setTitle(R.string.session_name_prompt)
                    setView(dialogView)
                    setPositiveButton(R.string.action_ok) { _, _ ->
                        val name = textView.text.toString()
                        // Check if session exists already
                        if (iUiController.getTabModel().isValidSessionName(name)) {
                            // That session does not exist yet, add it then
                            iUiController.getTabModel().iSessions.let {
                                it.add(Session(name, 1))
                                // Switch to our newly added session
                                (view.context as BrowserActivity).apply {
                                    presenter?.switchToSession(name)
                                    // Close session dialog after creating and switching to new session
                                    sessionsMenu.dismiss()
                                }
                                // Update our session list
                                //iAdapter.showSessions(it)
                            }
                        } else {
                            // We already have a session with that name, display an error message
                            context.toast(R.string.session_already_exists)
                        }
                    }
                }
            }

        // Handle save as button
        // TODO: reuse code between, new, save as and edit dialog
        aBinding.buttonSaveSession.setOnClickListener { view ->
            val dialogView = LayoutInflater.from(aBinding.root.context).inflate(R.layout.dialog_edit_text, null)
            val textView = dialogView.findViewById<EditText>(R.id.dialog_edit_text)
            // Make sure user can only enter valid filename characters
            textView.filters = arrayOf<InputFilter>(FileNameInputFilter())

            iUiController.getTabModel().let { tabs ->
                BrowserDialog.showCustomDialog(aBinding.root.context as Activity) {
                    setTitle(R.string.session_name_prompt)
                    setView(dialogView)
                    setPositiveButton(R.string.action_ok) { _, _ ->
                        var name = textView.text.toString()
                        // Check if session exists already
                        if (tabs.isValidSessionName(name)) {
                            // That session does not exist yet, add it then
                            tabs.iSessions?.let {
                                // Save current session session first
                                tabs.saveState()
                                // Add new session
                                it.add(Session(name, tabs.currentSession().tabCount))
                                // Set it as current session
                                tabs.iCurrentSessionName = name
                                // Save current tabs that our newly added session
                                tabs.saveState()
                                // Switch to our newly added session
                                (view.context as BrowserActivity).apply {
                                    // Close session dialog after creating and switching to new session
                                    // TODO: not in edit mode?
                                    sessionsMenu.dismiss()
                                }

                                // Show user we did switch session
                                view.context.apply {
                                    toast(getString(R.string.session_switched, name))
                                }

                                // Update our session list
                                //iAdapter.showSessions(it)
                            }
                        } else {
                            // We already have a session with that name, display an error message
                            context.toast(R.string.session_already_exists)
                        }
                    }
                }
            }
        }


        aBinding.buttonEditSessions.setOnClickListener {
            // Toggle edit mode
            iAdapter.iEditModeEnabledObservable.value?.let { editModeEnabled ->
                // Change button icon
                // TODO: change the text too?
                if (!editModeEnabled) {
                    aBinding.buttonEditSessions.setImageResource(R.drawable.ic_secured);
                } else {
                    aBinding.buttonEditSessions.setImageResource(R.drawable.ic_edit);
                }
                // Notify our observers of edit mode change
                iAdapter.iEditModeEnabledObservable.onNext(!editModeEnabled)
            }
        }

        // Make sure Ctrl + Shift + S closes our menu so that toggle is working
        // TODO: Somehow still not working
        /*
        contentView.isFocusableInTouchMode = true
        contentView.setOnKeyListener { _, keyCode, event ->
            val isCtrlShiftOnly  = KeyEvent.metaStateHasModifiers(event.metaState, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            //(isCtrlShiftOnly && keyCode == KeyEvent.KEYCODE_S).also { if (it) dismiss() }
            if (isCtrlShiftOnly && keyCode == KeyEvent.KEYCODE_S) {
                dismiss()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        */

        // Setup our recycler view
        aBinding.recyclerViewSessions.apply {
            //setLayerType(View.LAYER_TYPE_NONE, null)
            //(itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = iAdapter
            setHasFixedSize(true)
        }

        // Enable drag & drop but not swipe
        val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(iAdapter, true, false)
        iItemTouchHelper = ItemTouchHelper(callback)
        iItemTouchHelper?.attachToRecyclerView(iBinding.recyclerViewSessions)
    }


    /**
     *
     */
    fun show(aAnchor: View) {
        // Disable edit mode when showing our menu
        iAdapter.iEditModeEnabledObservable.onNext(false)
        iBinding.buttonEditSessions.setImageResource(R.drawable.ic_edit);

        //showAsDropDown(aAnchor, 0,-aAnchor.height)
        showAsDropDown(aAnchor, 0, 0)

        dimBehind()
        // Show our sessions
        updateSessions()
    }


    fun updateSessions() {
        //See: https://stackoverflow.com/q/43221847/3969362
        // I'm guessing isComputingLayout is not needed anymore since we moved our update after tab manager initialization
        // TODO: remove it and switch quickly between sessions to see if that still works
        if (!iBinding.recyclerViewSessions.isComputingLayout) {
            iUiController.getTabModel().iSessions.let { iAdapter.showSessions(it) }
        }
    }




}

