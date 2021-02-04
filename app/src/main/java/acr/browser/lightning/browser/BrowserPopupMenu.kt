package acr.browser.lightning.browser

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.databinding.PopupMenuBrowserBinding
import acr.browser.lightning.di.injector
import acr.browser.lightning.utils.isSpecialUrl
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import kotlinx.android.synthetic.main.popup_menu_browser.view.*
import javax.inject.Inject


class BrowserPopupMenu : PopupWindow {

    @Inject
    internal lateinit var bookmarkModel: BookmarkRepository

    constructor(layoutInflater: LayoutInflater, view: View = BrowserPopupMenu.inflate(layoutInflater))
            : super(view, WRAP_CONTENT, WRAP_CONTENT, true) {

        view.context.injector.inject(this)

        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog

        // Needed on Android 5 to make sure our pop-up can be dismissed by tapping outside and back button
        // See: https://stackoverflow.com/questions/46872634/close-popupwindow-upon-tapping-outside-or-back-button
        setBackgroundDrawable(ColorDrawable())

        // Hide incognito menu item if we are already incognito
        if ((view.context as BrowserActivity).isIncognito()) {
            view.menuItemIncognito.visibility = View.GONE
            // No sessions in incognito mode
            view.menuItemSessions.visibility = View.GONE
        }

    }


    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(aAnchor: View) {

        (contentView.context as BrowserActivity).tabsManager.let {
            // Set desktop mode checkbox according to current tab
            contentView.menuItemDesktopMode.isChecked = it.currentTab?.desktopMode ?: false

            it.currentTab?.let { tab ->
                // Let user add multiple times the same URL I guess, for now anyway
                // Blocking it is not nice and subscription is more involved I guess
                // See BookmarksDrawerView.updateBookmarkIndicator
                //contentView.menuItemAddBookmark.visibility = if (bookmarkModel.isBookmark(tab.url).blockingGet() || tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
                contentView.menuItemAddBookmark.visibility = if (tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
            }
        }

        showAsDropDown(aAnchor, 0,-aAnchor.height)
    }

    companion object {

        private const val margin = 15

        fun inflate(layoutInflater: LayoutInflater): View {
            return PopupMenuBrowserBinding.inflate(layoutInflater).root
        }

    }
}

