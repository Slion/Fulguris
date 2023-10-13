/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.browser

import fulguris.R
import fulguris.adblock.AbpUserRules
import fulguris.activity.WebBrowserActivity
import fulguris.database.bookmark.BookmarkRepository
import fulguris.databinding.MenuWebPageBinding
import fulguris.di.HiltEntryPoint
import fulguris.di.configPrefs
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.Utils
import fulguris.utils.isAppScheme
import fulguris.utils.isSpecialUrl
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import androidx.core.view.isVisible
import dagger.hilt.android.EntryPointAccessors


class MenuWebPage : PopupWindow {

    val bookmarkModel: BookmarkRepository
    val iUserPreferences: UserPreferences
    val abpUserRules: AbpUserRules

    var iBinding: MenuWebPageBinding

    constructor(layoutInflater: LayoutInflater, aBinding: MenuWebPageBinding = inflate(layoutInflater))
            : super(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

        //aBinding.root.context.injector.inject(this)

        iBinding = aBinding


        // Elevation just need to be high enough not to cut the effect defined in our layout
        elevation = 100F
        //
        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog

        // Needed on Android 5 to make sure our pop-up can be dismissed by tapping outside and back button
        // See: https://stackoverflow.com/questions/46872634/close-popupwindow-upon-tapping-outside-or-back-button
        setBackgroundDrawable(ColorDrawable())

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

        val hiltEntryPoint = EntryPointAccessors.fromApplication(iBinding.root.context.applicationContext, HiltEntryPoint::class.java)
        bookmarkModel = hiltEntryPoint.bookmarkRepository
        iUserPreferences = hiltEntryPoint.userPreferences
        abpUserRules = hiltEntryPoint.abpUserRules

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

    /**
     * Register click observer with the given menu item.
     * This gave us the opportunity to dismiss the dialog…
     * …but since we don't do that for all menu items anymore it is kinda useless.
     */
    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
        }
    }

    /**
     * Show menu items corresponding to our main menu.
     */
    private fun applyMainMenuItemVisibility() {

        iBinding.layoutMenuItemsContainer.isVisible = true
        // Those menu items are always on even for special URLs
        iBinding.menuItemFind.isVisible = true
        iBinding.menuItemPrint.isVisible = true
        iBinding.menuItemReaderMode.isVisible = true
        // Show option to go back to main menu
        iBinding.menuItemMainMenu.isVisible = true

        (contentView.context as WebBrowserActivity).tabsManager.let { tm ->
            tm.currentTab?.let { tab ->
                // Let user add multiple times the same URL I guess, for now anyway
                // Blocking it is not nice and subscription is more involved I guess
                // See BookmarksDrawerView.updateBookmarkIndicator
                //contentView.menuItemAddBookmark.visibility = if (bookmarkModel.isBookmark(tab.url).blockingGet() || tab.url.isSpecialUrl()) View.GONE else View.VISIBLE
                var isSpecial = false
                (!(tab.url.isSpecialUrl() || tab.url.isAppScheme())).let {
                    // Those menu items won't be displayed for special URLs
                    iBinding.menuItemDesktopMode.isVisible = it
                    iBinding.menuItemDarkMode.isVisible = it
                    iBinding.menuItemAddToHome.isVisible = it
                    iBinding.menuItemAddBookmark.isVisible = it
                    iBinding.menuItemShare.isVisible = it
                    iBinding.menuItemAdBlock.isVisible = it && iUserPreferences.adBlockEnabled
                    iBinding.menuItemTranslate.isVisible = it
                    isSpecial = !it
                }
            }
        }

        if ((iBinding.root.context as? WebBrowserActivity)?.isIncognito() == true) {
            // Incognito only works for that activity
            // So no reader mode as it starts another activity
            // TODO: We could try get reading mode working in incognito by creating another activity which starts in the same process I guess
            iBinding.menuItemReaderMode.isVisible = false
            // Also hide share and print as security feature when in incognito mode
            iBinding.menuItemShare.isVisible = false
            iBinding.menuItemPrint.isVisible = false
            iBinding.menuItemAddToHome.isVisible = false
        }

        scrollToStart()
    }

    /**
     * Open up this popup menu
     */
    fun show(aAnchor: View) {

        applyMainMenuItemVisibility()


        (contentView.context as WebBrowserActivity).tabsManager.let {
            // Set desktop mode checkbox according to current tab
            iBinding.menuItemDesktopMode.isChecked = it.currentTab?.desktopMode ?: false
            // Same with dark mode
            iBinding.menuItemDarkMode.isChecked = it.currentTab?.darkMode ?: false
            // And ad block
            iBinding.menuItemAdBlock.isChecked = it.currentTab?.url?.let { url -> !abpUserRules.isAllowed(Uri.parse(url)) } ?: false
        }

        // Get our anchor location
        val anchorLoc = IntArray(2)
        aAnchor.getLocationInWindow(anchorLoc)
        // Show our popup menu from the right side of the screen below our anchor
        val gravity = if (contentView.context.configPrefs.toolbarsBottom) Gravity.BOTTOM or Gravity.RIGHT else Gravity.TOP or Gravity.RIGHT
        val yOffset = if (contentView.context.configPrefs.toolbarsBottom) (contentView.context as WebBrowserActivity).iBinding.root.height - anchorLoc[1] - aAnchor.height else anchorLoc[1]
        showAtLocation(aAnchor, gravity,
                // Offset from the right screen edge
                fulguris.utils.Utils.dpToPx(10F),
                // Above our anchor
                yOffset)

        scrollToStart(0)
    }

    companion object {

        fun inflate(layoutInflater: LayoutInflater): MenuWebPageBinding {
            return MenuWebPageBinding.inflate(layoutInflater)
        }

    }
}

