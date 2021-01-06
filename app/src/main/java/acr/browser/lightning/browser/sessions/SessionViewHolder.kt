package acr.browser.lightning.browser.sessions

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.utils.ItemTouchHelperViewHolder
import acr.browser.lightning.view.BackgroundDrawable
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable


/**
 * The [RecyclerView.ViewHolder] for both vertical and horizontal tabs.
 * That represents an item in our list, basically one tab.
 */
class SessionViewHolder(
        view: View,
        private val iUiController: UIController
) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener, ItemTouchHelperViewHolder {


    // Using view binding won't give us much
    val textName: TextView = view.findViewById(R.id.text_name)
    val buttonEdit: ImageView = view.findViewById(R.id.button_edit)
    val buttonDelete: View = view.findViewById(R.id.button_delete)
    val layout: LinearLayout = view.findViewById(R.id.layout_background)

    private var previousBackground: BackgroundDrawable? = null

    init {
        buttonDelete.setOnClickListener{

        }

        // Edit session name
        buttonEdit.setOnClickListener{
            val dialogView = LayoutInflater.from(it.context).inflate(R.layout.dialog_edit_text, null)
            val textView = dialogView.findViewById<EditText>(R.id.dialog_edit_text)
            // Init our text field with current name
            textView.setText(session()?.name)
            textView.selectAll()
            //textView.requestFocus()

            var dialog : Dialog? = null

            //textView.showSoftInputOnFocus
            /*
            textView.setOnFocusChangeListener{ view, hasFocus ->
                if (hasFocus) {
                    dialog?.window?.setSoftInputMode(SOFT_INPUT_STATE_VISIBLE);
                }
            }
             */

            dialog = BrowserDialog.showCustomDialog(it.context as Activity) {
                setTitle(R.string.session_name_prompt)
                setView(dialogView)
                setPositiveButton(R.string.action_ok) { _, _ ->
                    val newName = textView.text.toString()
                    // Check if session exists already to display proper error message
                    if (iUiController.getTabModel().isValidSessionName(newName)) {
                        // Proceed with session rename
                        iUiController.getTabModel().renameSession(textName.tag as Int,newName)
                        // Change name on our item view
                        textName.text = sessionLabel()
                    } else {
                        // We already have a session with that name, display an error message
                        context.toast(R.string.session_already_exists)
                    }
                }
            }

            /*
            dialog.setOnShowListener {
                val imm = dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT);
            }

             */

            //TODO: use on show listener?
            // TODO: we need to review our dialog APIs
            // See: https://stackoverflow.com/a/12997855/3969362
            // Trying to make it so that virtual keyboard opens up as the dialog opens
            val imm = it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            //imm.showSoftInput(textView, InputMethodManager.SHOW_FORCED);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)

        }

        // Session item click
        layout.setOnClickListener{
            // User wants to switch session
            //TODO: turn this into a switch session method of our presenter?
            iUiController.getTabModel().apply {
                // Save current states
                saveState()
                // Change current session
                iCurrentSessionName = session()?.name
                // Save it again to preserve new current session name
                saveSessions()
            }
            // Then reload our tabs
            (it.context as BrowserActivity).apply {
                presenter?.setupTabs(null)
                // Dismiss sessions menu if edit mode is disabled
                if (!isEditModeEnabled()) {
                    sessionsMenu.dismiss()
                }
            }
        }

        layout.setOnLongClickListener(this)
        // Is that the best way to access our preferences?
        //imageDelete.visibility = if ((view.context as BrowserActivity).userPreferences.showCloseTabButton) View.VISIBLE else View.GONE
    }


    private fun session() = iUiController.getTabModel().iSessions?.get(textName.tag as Int)

    /**
     * Provide the session label as shown to the user.
     * It includes session name and tab count.
     */
    fun sessionLabel() = session()?.name + " - " + session()?.tabCount



    //TODO: should we have dedicated click handlers instead of a switch?
    override fun onClick(v: View) {
        if (v === buttonDelete) {
            //uiController.tabCloseClicked(adapterPosition)
        } else if (v === layout) {
        }
    }

    override fun onLongClick(v: View): Boolean {
        //uiController.showCloseDialog(adapterPosition)
        //return true
        return false
    }

    // From ItemTouchHelperViewHolder
    // Start dragging
    override fun onItemSelected() {
        // Do some fancy for smoother transition
        previousBackground = layout.background as BackgroundDrawable
        previousBackground?.let {
            layout.background = BackgroundDrawable(itemView.context, if (it.isSelected) R.attr.selectedBackground else R.attr.colorPrimaryDark, R.attr.colorControlHighlight).apply{startTransition(300)}
        }
    }

    // From ItemTouchHelperViewHolder
    // Stopped dragging
    override fun onItemClear() {
        // Here sadly no transition
        layout.background = previousBackground
    }

    /**
     * Tell this view holder to start observing edit mode changes.
     */
    fun observeEditMode(observable: Observable<Boolean>): Disposable {
        return observable
                //.debounce(SEARCH_TYPING_INTERVAL, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                // TODO: Is that needed? Is it not the default somehow?
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { editMode ->
                    if (editMode) {
                        buttonEdit.visibility = View.VISIBLE
                        buttonDelete.visibility = View.VISIBLE
                    } else {
                        buttonEdit.visibility = View.GONE
                        buttonDelete.visibility = View.GONE
                    }
                }
    }

    /**
     * Tell if edit mode is currently enabled
     */
    fun isEditModeEnabled() = buttonEdit.visibility == View.VISIBLE

}
