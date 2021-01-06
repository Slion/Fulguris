package acr.browser.lightning.browser.sessions

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.databinding.PopupMenuBrowserBinding
import acr.browser.lightning.databinding.SessionListBinding
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.extensions.toast
import acr.browser.lightning.list.VerticalItemAnimator
import acr.browser.lightning.settings.fragment.GeneralSettingsFragment
import acr.browser.lightning.settings.fragment.SummaryUpdater
import acr.browser.lightning.utils.FileUtils
import acr.browser.lightning.utils.ThemeUtils
import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.EditText
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class SessionsPopupWindow : PopupWindow {

    var iUiController: UIController
    var iAdapter: SessionsAdapter

    constructor(layoutInflater: LayoutInflater,
                aBinding: SessionListBinding = SessionListBinding.inflate(layoutInflater))
            : super(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

        //view.context.injector.inject(this)

        iUiController = aBinding.root.context as UIController
        iAdapter = SessionsAdapter(iUiController)

        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog

        // Hide incognito menu item if we are already incognito
        if ((aBinding.root.context as BrowserActivity).isIncognito()) {
            //view.menuItemIncognito.visibility = View.GONE
        }

        // Handle click on add session button
        aBinding.buttonNewSession.setOnClickListener {
                val dialogView = LayoutInflater.from(aBinding.root.context).inflate(R.layout.dialog_edit_text, null)
                val textView = dialogView.findViewById<EditText>(R.id.dialog_edit_text)

                BrowserDialog.showCustomDialog(aBinding.root.context as Activity) {
                    setTitle(R.string.session_name_prompt)
                    setView(dialogView)
                    setPositiveButton(R.string.action_ok) { _, _ ->
                        var name = textView.text.toString()
                        // Check if session exists already
                        if (iUiController.getTabModel().isValidSessionName(name)) {
                            // That session dos not exist yet, add it then
                            iUiController.getTabModel().iSessions?.let {
                                it.add(Session(name, 0))
                                // Update our session list
                                iAdapter.showSessions(it)
                                aBinding.root.invalidate()
                            }
                        } else {
                            // We already have a session with that name, display an error message
                            context.toast(R.string.session_already_exists)
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
                // Notify our observers off edit mode change
                iAdapter.iEditModeEnabledObservable.onNext(!editModeEnabled)
            }
        }

        val animator = VerticalItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = 200
            changeDuration = 0
            removeDuration = 200
            moveDuration = 200
        }

        // Setup our recycler view
        aBinding.sessionList.apply {
            //setLayerType(View.LAYER_TYPE_NONE, null)
            itemAnimator = animator
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = iAdapter
            setHasFixedSize(true)
        }

        // TODO: enable drag & drop
        //val callback: ItemTouchHelper.Callback = TabTouchHelperCallback(tabsAdapter)
        //mItemTouchHelper = ItemTouchHelper(callback)
        //mItemTouchHelper?.attachToRecyclerView(iBinding.tabsList)
    }




    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(rootView: View) {

        (contentView.context as BrowserActivity).tabsManager.let {
            // Set desktop mode checkbox according to current tab
            //contentView.menuItemDesktopMode.isChecked = it.currentTab?.toggleDesktop ?: false

            it.currentTab?.let { tab ->
                // Let user add multiple times the same URL I guess, for now anyway
                // Blocking it is not nice and subscription is more involved I guess
                // See BookmarksDrawerView.updateBookmarkIndicator
                //contentView.menuItemAddBookmark.visibility = if (bookmarkModel.isBookmark(tab.url).blockingGet() || tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
                //contentView.menuItemAddBookmark.visibility = if (tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
            }
        }

        showAtLocation(rootView, Gravity.CENTER, 0, 0);
        dimBehind(this)
        // Show our sessions
        iUiController.getTabModel().iSessions?.let{iAdapter.showSessions(it)}
    }

    /**
     *  TODO: Make this a View extension
     *  See: https://stackoverflow.com/a/46711174/3969362
     */
    private fun dimBehind(popupWindow: PopupWindow) {
        val container = popupWindow.contentView.rootView
        val context: Context = popupWindow.contentView.context
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val p = container.layoutParams as WindowManager.LayoutParams
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        p.dimAmount = 0.3f
        wm.updateViewLayout(container, p)
    }


}

