package acr.browser.lightning.browser

import acr.browser.lightning.R
import acr.browser.lightning.browser.activity.BrowserActivity
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.databinding.PopupMenuBrowserBinding
import acr.browser.lightning.di.injector
import acr.browser.lightning.utils.Utils
import acr.browser.lightning.utils.isSpecialUrl
import android.animation.AnimatorInflater
import android.animation.LayoutTransition
import android.graphics.drawable.ColorDrawable
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
    var iBinding: PopupMenuBrowserBinding

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
        if ((aBinding.root.context as BrowserActivity).isIncognito()) {
            aBinding.menuItemIncognito.visibility = View.GONE
            // No sessions in incognito mode
            aBinding.menuItemSessions.visibility = View.GONE
        }

        //val radius: Float = getResources().getDimension(R.dimen.default_corner_radius) //32dp

        val animator = AnimatorInflater.loadAnimator(iBinding.root.context, R.animator.menu_item_appearing)
        iBinding.layoutMenuItems.layoutTransition.setAnimator(LayoutTransition.APPEARING, animator)
        iBinding.layoutMenuItems.layoutTransition.setDuration(LayoutTransition.APPEARING, animator.duration)

        val animatorDisappearing = AnimatorInflater.loadAnimator(iBinding.root.context, R.animator.menu_item_disappearing)
        iBinding.layoutMenuItems.layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, animatorDisappearing)
        iBinding.layoutMenuItems.layoutTransition.setDuration(LayoutTransition.DISAPPEARING, animatorDisappearing.duration)

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

        iBinding.menuItemWebPage.setOnClickListener {
            // Toggle web page actions visibility
            val willBeVisible = !iBinding.menuItemFind.isVisible

            // Rotate icon using animations
            if (willBeVisible) {
                iBinding.imageExpandable.startAnimation(AnimationUtils.loadAnimation(contentView.context, R.anim.rotate_clockwise_90));
            } else {
                iBinding.imageExpandable.startAnimation(AnimationUtils.loadAnimation(contentView.context, R.anim.rotate_counterclockwise_90));
            }

            // Those menu items are always on even for special URLs
            iBinding.menuItemFind.isVisible = willBeVisible
            iBinding.menuItemPrint.isVisible = willBeVisible
            iBinding.menuItemReaderMode.isVisible = willBeVisible
            //
            (contentView.context as BrowserActivity).tabsManager.let { tm ->
                tm.currentTab?.let { tab ->
                    // Let user add multiple times the same URL I guess, for now anyway
                    // Blocking it is not nice and subscription is more involved I guess
                    // See BookmarksDrawerView.updateBookmarkIndicator
                    //contentView.menuItemAddBookmark.visibility = if (bookmarkModel.isBookmark(tab.url).blockingGet() || tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
                    (!tab.url.isSpecialUrl() && willBeVisible).let {
                        // Those menu items won't be displayed for special URLs
                        iBinding.menuItemDesktopMode.isVisible = it
                        iBinding.menuItemAddToHome.isVisible = it
                        iBinding.menuItemAddBookmark.isVisible = it
                        iBinding.menuItemShare.isVisible = it
                    }
                }
            }
        }
    }


    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(aAnchor: View) {

        // Reset items visibility
        iBinding.menuItemShare.isVisible = false
        iBinding.menuItemAddBookmark.isVisible = false
        iBinding.menuItemFind.isVisible = false
        iBinding.menuItemPrint.isVisible = false
        iBinding.menuItemReaderMode.isVisible = false
        iBinding.menuItemDesktopMode.isVisible = false
        iBinding.menuItemAddToHome.isVisible = false
        iBinding.menuItemWebPage.isVisible = true


        (contentView.context as BrowserActivity).tabsManager.let {
            // Set desktop mode checkbox according to current tab
            iBinding.menuItemDesktopMode.isChecked = it.currentTab?.desktopMode ?: false

        }


        //showAsDropDown(aAnchor, 0,-aAnchor.height)

        // Get our anchor location
        val anchorLoc = IntArray(2)
        aAnchor.getLocationInWindow(anchorLoc)
        // Show our popup menu from the right side of the screen below our anchor
        showAtLocation(aAnchor, Gravity.TOP or Gravity.RIGHT,
                // Offset from the right screen edge
                Utils.dpToPx(10F),
                // Above our anchor
                anchorLoc[1])

    }

    companion object {

        fun inflate(layoutInflater: LayoutInflater): PopupMenuBrowserBinding {
            return PopupMenuBrowserBinding.inflate(layoutInflater)
        }

    }
}

