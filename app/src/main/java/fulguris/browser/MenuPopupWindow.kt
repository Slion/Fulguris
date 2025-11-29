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

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import androidx.core.view.isVisible
import dagger.hilt.android.EntryPointAccessors
import fulguris.R
import fulguris.activity.WebBrowserActivity
import fulguris.adblock.AbpUserRules
import fulguris.database.bookmark.BookmarkRepository
import fulguris.databinding.MenuCustomBinding
import fulguris.di.HiltEntryPoint
import fulguris.di.configPrefs
import fulguris.extensions.removeFromParent
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.isAppScheme
import fulguris.utils.isSpecialUrl

/**
 * Unified custom menu that can display either MainMenu or TabMenu content
 * based on saved user configuration.
 */
class MenuPopupWindow : PopupWindow {

    val bookmarkModel: BookmarkRepository
    val iUserPreferences: UserPreferences
    val abpUserRules: AbpUserRules
    private val menuConfig: MenuConfiguration

    var iBinding: MenuCustomBinding

    // Current menu mode
    private var currentMode: MenuType = MenuType.MainMenu

    // Incognito status
    private var isIncognito = false

    constructor(
        layoutInflater: LayoutInflater,
        aBinding: MenuCustomBinding = inflate(layoutInflater),
        mode: MenuType = MenuType.MainMenu
    ) : super(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

        iBinding = aBinding
        currentMode = mode

        // Determine incognito status
        isIncognito = (aBinding.root.context as? WebBrowserActivity)?.isIncognito() ?: false

        // Elevation just need to be high enough not to cut the effect defined in our layout
        elevation = 100F
        animationStyle = R.style.AnimationMenu

        // Needed on Android 5 to make sure our pop-up can be dismissed by tapping outside and back button
        setBackgroundDrawable(ColorDrawable())

        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            iBinding.root.context.applicationContext,
            HiltEntryPoint::class.java
        )
        bookmarkModel = hiltEntryPoint.bookmarkRepository
        iUserPreferences = hiltEntryPoint.userPreferences
        abpUserRules = hiltEntryPoint.abpUserRules
        menuConfig = MenuConfiguration(iBinding.root.context)
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
                    iBinding.scrollViewItems.smoothScrollTo(0, iBinding.scrollViewItems.height)
                } else {
                    iBinding.scrollViewItems.smoothScrollTo(0, 0)
                }
            }, aDelay
        )
    }

    /**
     * Register click observer with the given menu item.
     */
    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
        }
    }

    /**
     * Setup toolbar layout based on toolbar position (top or bottom)
     * This should be called after menu creation and when toolbar position changes
     */
    fun setupToolbarLayout() {
        val toolbarsBottom = contentView.context.configPrefs.toolbarsBottom

        // Set animation style
        animationStyle = if (toolbarsBottom) {
            R.style.AnimationMenuBottom
        } else {
            R.style.AnimationMenu
        }

        // Move header to correct position
        if (toolbarsBottom) {
            iBinding.header.removeFromParent()?.addView(iBinding.header)
        } else {
            iBinding.header.removeFromParent()?.addView(iBinding.header, 0)
        }

        // Move scroll view to correct position
        if (toolbarsBottom) {
            iBinding.scrollViewItems.removeFromParent()?.addView(iBinding.scrollViewItems, 0)
        } else {
            iBinding.scrollViewItems.removeFromParent()?.addView(iBinding.scrollViewItems)
        }
    }

    /**
     * Apply menu item visibility and order based on saved user configuration and current mode
     */
    private fun applyMenuItemVisibility() {
        iBinding.layoutMenuItemsContainer.isVisible = true

        // Load saved configuration
        val savedConfig = menuConfig.loadConfiguration()

        // Get items for current mode
        val itemsForCurrentMode = when {
            currentMode == MenuType.FullMenu -> {
                // All mode: show all non-optional items in the order they are defined in MenuItems
                // This preserves the logical grouping (main menu items, then tab menu items)
                val allItems = MenuItems.getAll()
                // Always use the definition order from MenuItems, ignore saved configuration order
                allItems.filter { !it.optional }
                    .map { it.id }
                    .toMutableList()
            }
            savedConfig != null -> {
                // Use saved configuration (already sorted by order)
                savedConfig.filter { it.menu == currentMode }
                    .sortedBy { it.order }
                    .map { it.id }
                    .toMutableList()
            }
            else -> {
                // Use default configuration from MenuItems model
                MenuItems.getAll()
                    .filter { it.defaultMenu == currentMode }
                    .map { it.id }
                    .toMutableList()
            }
        }

        // In incognito mode, ensure EXIT is always in main menu
        if (isIncognito && currentMode == MenuType.MainMenu) {
            // Check if EXIT is present in main menu
            val hasExitInMainMenu = itemsForCurrentMode.contains(MenuItemId.Exit)

            // If EXIT is not in main menu, add it to the end
            // (whether it was in tab menu, hidden, or not present at all)
            if (!hasExitInMainMenu) {
                itemsForCurrentMode.add(MenuItemId.Exit)
            }
        }

        // Hide all menu items first
        hideAllMenuItems()

        // Apply order by removing all views and re-adding them in the correct order
        val menuContainer = iBinding.layoutMenuItems

        // Get all menu item views using viewRes from model
        val allViews = itemsForCurrentMode.mapNotNull { itemId ->
            val menuItem = MenuItems.getItem(itemId)
            if (menuItem != null && menuItem.viewId != 0) {
                iBinding.root.findViewById<View>(menuItem.viewId)
            } else {
                null
            }
        }

        // Remove all menu item views from container
        allViews.forEach { view ->
            menuContainer.removeView(view)
        }

        // Re-add views in the correct order and make them visible
        // Reverse order if toolbars are at bottom
        val orderedViews = if (contentView.context.configPrefs.toolbarsBottom) {
            allViews.reversed()
        } else {
            allViews
        }

        orderedViews.forEach { view ->
            menuContainer.addView(view)
            view.isVisible = true
        }

        // Special handling based on menu mode
        if (currentMode == MenuType.MainMenu) {
            applyMainMenuSpecialRules()
        } else if (currentMode == MenuType.TabMenu) {
            applyTabMenuSpecialRules()
        } else if (currentMode == MenuType.FullMenu) {
            // All mode: apply both main and tab menu rules
            applyMainMenuSpecialRules()
            applyTabMenuSpecialRules()
        }

        scrollToStart()
    }

    /**
     * Hide all menu items
     */
    private fun hideAllMenuItems() {
        // Menu switchers
        iBinding.menuItemMainMenu.isVisible = false
        iBinding.menuItemTabMenu.isVisible = false

        // Main menu items
        iBinding.menuItemSessions.isVisible = false
        iBinding.menuItemBookmarks.isVisible = false
        iBinding.menuItemHistory.isVisible = false
        iBinding.menuItemDownloads.isVisible = false
        iBinding.menuItemNewTab.isVisible = false
        iBinding.menuItemIncognito.isVisible = false
        iBinding.menuItemOptions.isVisible = false
        iBinding.menuItemSettings.isVisible = false
        iBinding.menuItemExit.isVisible = false

        // Tab menu items
        iBinding.menuItemPageHistory.isVisible = false
        iBinding.menuItemFind.isVisible = false
        iBinding.menuItemPrint.isVisible = false
        iBinding.menuItemReaderMode.isVisible = false
        iBinding.menuItemDesktopMode.isVisible = false
        iBinding.menuItemDarkMode.isVisible = false
        iBinding.menuItemAddToHome.isVisible = false
        iBinding.menuItemAddBookmark.isVisible = false
        iBinding.menuItemShare.isVisible = false
        iBinding.menuItemAdBlock.isVisible = false
        iBinding.menuItemTranslate.isVisible = false
        iBinding.menuItemPageRequests.isVisible = false
    }

    /**
     * Show a specific menu item by its ID
     */
    private fun showMenuItem(itemId: MenuItemId) {
        when (itemId) {
            // Menu switchers
            MenuItemId.MainMenu -> iBinding.menuItemMainMenu.isVisible = true
            MenuItemId.TabMenu -> iBinding.menuItemTabMenu.isVisible = true

            // Main menu items
            MenuItemId.Sessions -> iBinding.menuItemSessions.isVisible = true
            MenuItemId.Bookmarks -> iBinding.menuItemBookmarks.isVisible = true
            MenuItemId.History -> iBinding.menuItemHistory.isVisible = true
            MenuItemId.Downloads -> iBinding.menuItemDownloads.isVisible = true
            MenuItemId.NewTab -> iBinding.menuItemNewTab.isVisible = true
            MenuItemId.Incognito -> iBinding.menuItemIncognito.isVisible = true
            MenuItemId.Options -> iBinding.menuItemOptions.isVisible = true
            MenuItemId.Settings -> iBinding.menuItemSettings.isVisible = true
            MenuItemId.Exit -> iBinding.menuItemExit.isVisible = true

            // Tab menu items
            MenuItemId.TabHistory -> iBinding.menuItemPageHistory.isVisible = true
            MenuItemId.Find -> iBinding.menuItemFind.isVisible = true
            MenuItemId.Print -> iBinding.menuItemPrint.isVisible = true
            MenuItemId.ReaderMode -> iBinding.menuItemReaderMode.isVisible = true
            MenuItemId.DesktopMode -> iBinding.menuItemDesktopMode.isVisible = true
            MenuItemId.DarkMode -> iBinding.menuItemDarkMode.isVisible = true
            MenuItemId.AddToHome -> iBinding.menuItemAddToHome.isVisible = true
            MenuItemId.AddBookmark -> iBinding.menuItemAddBookmark.isVisible = true
            MenuItemId.Share -> iBinding.menuItemShare.isVisible = true
            MenuItemId.AdBlock -> iBinding.menuItemAdBlock.isVisible = true
            MenuItemId.Translate -> iBinding.menuItemTranslate.isVisible = true
            MenuItemId.Requests -> iBinding.menuItemPageRequests.isVisible = true
        }
    }

    /**
     * Apply special visibility rules for main menu items based on incognito mode
     */
    private fun applyMainMenuSpecialRules() {
        // Hide certain items in incognito mode (from MenuMain logic)
        if (isIncognito) {
            iBinding.menuItemSessions.isVisible = false
            iBinding.menuItemSettings.isVisible = false
            iBinding.menuItemIncognito.isVisible = false
            // Always ensure EXIT is visible in incognito mode
            iBinding.menuItemExit.isVisible = true
        }
    }

    /**
     * Apply special visibility rules for tab menu items based on current tab state
     */
    private fun applyTabMenuSpecialRules() {
        (contentView.context as WebBrowserActivity).tabsManager.let { tm ->
            tm.currentTab?.let { tab ->
                val isSpecialUrl = tab.url.isSpecialUrl() || tab.url.isAppScheme()

                // Hide certain items for special URLs
                if (isSpecialUrl) {
                    iBinding.menuItemDesktopMode.isVisible = false
                    iBinding.menuItemDarkMode.isVisible = false
                    iBinding.menuItemAddToHome.isVisible = false
                    iBinding.menuItemAddBookmark.isVisible = false
                    iBinding.menuItemShare.isVisible = false
                    iBinding.menuItemAdBlock.isVisible = false
                    iBinding.menuItemTranslate.isVisible = false
                }

                // Hide ad block if not enabled in settings
                if (!iUserPreferences.adBlockEnabled) {
                    iBinding.menuItemAdBlock.isVisible = false
                }
            }
        }

        // Hide certain items in incognito mode
        if ((contentView.context as? WebBrowserActivity)?.isIncognito() == true) {
            iBinding.menuItemReaderMode.isVisible = false
            iBinding.menuItemShare.isVisible = false
            iBinding.menuItemPrint.isVisible = false
            iBinding.menuItemAddToHome.isVisible = false
        }
    }

    /**
     * Switch to a different menu mode
     */
    fun switchMode(mode: MenuType) {
        currentMode = mode
        applyMenuItemVisibility()
    }

    /**
     * Open up this popup menu
     */
    fun show(aAnchor: View) {
        applyMenuItemVisibility()

        (contentView.context as WebBrowserActivity).tabsManager.let {
            // Set desktop mode checkbox according to current tab
            iBinding.menuItemDesktopMode.isChecked = it.currentTab?.desktopMode ?: false
            // Same with dark mode
            iBinding.menuItemDarkMode.isChecked = it.currentTab?.darkMode ?: false
            // And ad block
            iBinding.menuItemAdBlock.isChecked = it.currentTab?.url?.let { url ->
                !abpUserRules.isAllowed(Uri.parse(url))
            } ?: false
        }

        // Get our anchor location
        val anchorLoc = IntArray(2)
        aAnchor.getLocationInWindow(anchorLoc)

        // Show our popup menu from the right side of the screen below our anchor
        val gravity = if (contentView.context.configPrefs.toolbarsBottom) {
            Gravity.BOTTOM or Gravity.RIGHT
        } else {
            Gravity.TOP or Gravity.RIGHT
        }

        val yOffset = if (contentView.context.configPrefs.toolbarsBottom) {
            (contentView.context as WebBrowserActivity).iBinding.root.height - anchorLoc[1] - aAnchor.height
        } else {
            anchorLoc[1]
        }

        showAtLocation(
            aAnchor,
            gravity,
            fulguris.utils.Utils.dpToPx(10F), // Offset from the right screen edge
            yOffset
        )

        scrollToStart(0)
    }

    companion object {
        fun inflate(layoutInflater: LayoutInflater): MenuCustomBinding {
            return MenuCustomBinding.inflate(layoutInflater)
        }
    }
}

