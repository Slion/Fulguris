package acr.browser.lightning.browser

import acr.browser.lightning.R
import acr.browser.lightning.adblock.AbpUserRules
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.databinding.PopupMenuBrowserBinding
import acr.browser.lightning.di.configPrefs
import acr.browser.lightning.di.injector
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.Utils
import acr.browser.lightning.utils.isAppScheme
import acr.browser.lightning.utils.isSpecialUrl
import android.animation.AnimatorInflater
import android.animation.LayoutTransition
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AnimationUtils
import android.widget.PopupWindow
import androidx.core.view.isVisible
import javax.inject.Inject


class BrowserPopupMenu : PopupWindow {

    @Inject
    internal lateinit var bookmarkModel: BookmarkRepository
    @Inject
    lateinit var iUserPreferences: UserPreferences
    @Inject
    lateinit var abpUserRules: AbpUserRules

    var iBinding: PopupMenuBrowserBinding
    var iIsIncognito = false

    constructor(layoutInflater: LayoutInflater, aBinding: PopupMenuBrowserBinding = BrowserPopupMenu.inflate(layoutInflater))
            : super(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

        aBinding.root.context.injector.inject(this)

        iBinding = aBinding


        // Elevation just need to be high enough not to cut the effect defined in our layout
        elevation = 100F
        //
        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog

        // Needed on Android 5 to make sure our pop-up can be dismissed by tapping outside and back button
        // See: https://stackoverflow.com/questions/46872634/close-popupwindow-upon-tapping-outside-or-back-button
        setBackgroundDrawable(ColorDrawable())

        // Hide incognito menu item if we are already incognito
        iIsIncognito = (aBinding.root.context as BrowserActivity).isIncognito()
        if (iIsIncognito) {
            aBinding.menuItemIncognito.isVisible = false
            // No sessions in incognito mode
            aBinding.menuItemSessions.isVisible = false
        }

        //val radius: Float = getResources().getDimension(R.dimen.default_corner_radius) //32dp

        //iBinding.layoutMenuItems.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        //iBinding.layoutMenuItems.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)
        //iBinding.layoutMenuItems.layoutTransition.disableTransitionType(LayoutTransition.CHANGING)


        //iBinding.layoutMenuItems.layoutTransition.setAnimator(LayoutTransition.CHANGE_APPEARING, animator)
        //iBinding.layoutMenuItems.layoutTransition.setDuration(LayoutTransition.CHANGE_APPEARING, animator.duration)
        //iBinding.layoutMenuItems.layoutTransition.setAnimator(LayoutTransition.CHANGING, animator)
        //iBinding.layoutMenuItems.layoutTransition.setDuration(LayoutTransition.CHANGING, animator.duration)


        /*
        // TODO: That fixes the corner but leaves a square shadow behind
        val toolbar: AppBarLayout = view.findViewById(R.id.header)
        val materialShapeDrawable = toolbar.background as MaterialShapeDrawable
        materialShapeDrawable.shapeAppearanceModel = materialShapeDrawable.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, Utils.dpToPx(16F).toFloat())
                .build()
         */


        iBinding.menuItemMainMenu.setOnClickListener {
            //applyMenuItemAnimations()
            applyMainMenuItemVisibility()
            scrollToStart()
        }


        iBinding.menuItemWebPage.setOnClickListener {

            //applyMenuItemAnimations()

            // Those menu items are always on even for special URLs
            iBinding.menuItemFind.isVisible = true
            iBinding.menuItemPrint.isVisible = true
            iBinding.menuItemReaderMode.isVisible = true
            // Show option to go back to main menu
            iBinding.menuItemMainMenu.isVisible = true
            // Hide app menu items
            iBinding.menuItemWebPage.isVisible = false
            iBinding.menuItemSessions.isVisible = false
            iBinding.menuItemBookmarks.isVisible = false
            iBinding.menuItemHistory.isVisible = false
            iBinding.menuItemDownloads.isVisible = false
            iBinding.menuItemNewTab.isVisible = false
            iBinding.menuItemIncognito.isVisible = false
            iBinding.menuItemSettings.isVisible = false
            iBinding.menuItemExit.isVisible = false


            (contentView.context as BrowserActivity).tabsManager.let { tm ->
                tm.currentTab?.let { tab ->
                    // Let user add multiple times the same URL I guess, for now anyway
                    // Blocking it is not nice and subscription is more involved I guess
                    // See BookmarksDrawerView.updateBookmarkIndicator
                    //contentView.menuItemAddBookmark.visibility = if (bookmarkModel.isBookmark(tab.url).blockingGet() || tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
                    (!(tab.url.isSpecialUrl() || tab.url.isAppScheme())).let {
                        // Those menu items won't be displayed for special URLs
                        iBinding.menuItemDesktopMode.isVisible = it
                        iBinding.menuItemDarkMode.isVisible = it
                        iBinding.menuItemAddToHome.isVisible = it
                        iBinding.menuItemAddBookmark.isVisible = it
                        iBinding.menuItemShare.isVisible = it
			            iBinding.menuItemAdBlock.isVisible = it && iUserPreferences.adBlockEnabled
                    }
                }
            }

            scrollToStart()
        }
    }

    /**
     * Scroll to the start of our menu.
     * Could be the bottom or the top depending if we are using bottom toolbars.
     * Default delay matches items animation.
     */
    private fun scrollToStart(aDelay: Long = 300) {
        iBinding.scrollViewItems.postDelayed(
            {
                if (contentView.context.configPrefs.toolbarsBottom) {
                    iBinding.scrollViewItems.smoothScrollTo(0, iBinding.scrollViewItems.height);
                } else {
                    iBinding.scrollViewItems.smoothScrollTo(0, 0);
                }
            }, aDelay
        )
    }


    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    /**
     * Was needed when we were using our extending menu.
     * It's now unused.
     */
    private fun applyMenuItemAnimations() {

        val animator = AnimatorInflater.loadAnimator(iBinding.root.context, R.animator.menu_item_appearing)
        val animatorFromBottom = AnimatorInflater.loadAnimator(iBinding.root.context, R.animator.menu_item_appearing_from_bottom)

        val animatorDisappearing = AnimatorInflater.loadAnimator(iBinding.root.context, R.animator.menu_item_disappearing)
        val animatorDisappearingFromBottom = AnimatorInflater.loadAnimator(iBinding.root.context, R.animator.menu_item_disappearing_from_bottom)

        // Set animations according to menu position
        if (!contentView.context.configPrefs.toolbarsBottom) {
            iBinding.layoutTabMenuItems.layoutTransition.setAnimator(LayoutTransition.APPEARING, animator)
            iBinding.layoutTabMenuItems.layoutTransition.setDuration(LayoutTransition.APPEARING, animator.duration)
            iBinding.layoutTabMenuItems.layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, animatorDisappearing)
            iBinding.layoutTabMenuItems.layoutTransition.setDuration(LayoutTransition.DISAPPEARING, animatorDisappearing.duration)
        } else {
            iBinding.layoutTabMenuItems.layoutTransition.setAnimator(LayoutTransition.APPEARING, animatorFromBottom)
            iBinding.layoutTabMenuItems.layoutTransition.setDuration(LayoutTransition.APPEARING, animatorFromBottom.duration)
            iBinding.layoutTabMenuItems.layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, animatorDisappearingFromBottom)
            iBinding.layoutTabMenuItems.layoutTransition.setDuration(LayoutTransition.DISAPPEARING, animatorDisappearingFromBottom.duration)
        }
    }

    /**
     * Show menu items corresponding to our main menu.
     */
    private fun applyMainMenuItemVisibility() {
        // Reset items visibility
        iBinding.menuItemWebPage.isVisible = true
        iBinding.menuItemMainMenu.isVisible = false
        //
        iBinding.menuItemShare.isVisible = false
        iBinding.menuItemAddBookmark.isVisible = false
        iBinding.menuItemFind.isVisible = false
        iBinding.menuItemPrint.isVisible = false
        iBinding.menuItemReaderMode.isVisible = false
        iBinding.menuItemDesktopMode.isVisible = false
        iBinding.menuItemDarkMode.isVisible = false
        iBinding.menuItemAddToHome.isVisible = false
	    iBinding.menuItemAdBlock.isVisible = false
        // Basic items
        iBinding.menuItemSessions.isVisible = !iIsIncognito
        iBinding.menuItemBookmarks.isVisible = true
        iBinding.menuItemHistory.isVisible = true
        iBinding.menuItemDownloads.isVisible = true
        iBinding.menuItemNewTab.isVisible = true
        iBinding.menuItemIncognito.isVisible = !iIsIncognito
        iBinding.menuItemSettings.isVisible = true

        iBinding.menuItemExit.isVisible = iUserPreferences.menuShowExit || iIsIncognito
        iBinding.menuItemNewTab.isVisible = iUserPreferences.menuShowNewTab
    }

    /**
     * Open up this popup menu
     */
    fun show(aAnchor: View) {

        applyMainMenuItemVisibility()


        (contentView.context as BrowserActivity).tabsManager.let {
            // Set desktop mode checkbox according to current tab
            iBinding.menuItemDesktopMode.isChecked = it.currentTab?.desktopMode ?: false
            // Same with dark mode
            iBinding.menuItemDarkMode.isChecked = it.currentTab?.darkMode ?: false
            // And ad block
            iBinding.menuItemAdBlock.isChecked = it.currentTab?.url?.let { url -> !abpUserRules.isWhitelisted(Uri.parse(url)) } ?: false
        }

        // Get our anchor location
        val anchorLoc = IntArray(2)
        aAnchor.getLocationInWindow(anchorLoc)
        // Show our popup menu from the right side of the screen below our anchor
        val gravity = if (contentView.context.configPrefs.toolbarsBottom) Gravity.BOTTOM or Gravity.RIGHT else Gravity.TOP or Gravity.RIGHT
        val yOffset = if (contentView.context.configPrefs.toolbarsBottom) (contentView.context as BrowserActivity).iBinding.root.height - anchorLoc[1] - aAnchor.height else anchorLoc[1]
        showAtLocation(aAnchor, gravity,
                // Offset from the right screen edge
                Utils.dpToPx(10F),
                // Above our anchor
                yOffset)

        scrollToStart(0)
    }

    companion object {

        fun inflate(layoutInflater: LayoutInflater): PopupMenuBrowserBinding {
            return PopupMenuBrowserBinding.inflate(layoutInflater)
        }

    }
}

