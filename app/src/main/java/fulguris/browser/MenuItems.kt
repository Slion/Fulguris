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

/**
 * Registry of all available menu items for both main menu and tab menu
 */
object MenuItems {

    /**
     * Map of all available menu items indexed by their ID.
     * Orders define the default order of items in our menus.
     * Also used to order items in our full menu.
     */
    private val all = mapOf(
        MenuItemId.MAIN_MENU to MenuItem(
            id = MenuItemId.MAIN_MENU,
            labelId = R.string.action_main_menu,
            iconId = R.drawable.ic_menu,
            viewId = R.id.menuItemMainMenu,
            availableInMainMenu = false,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu,
            optional = true
        ),
        MenuItemId.TAB_MENU to MenuItem(
            id = MenuItemId.TAB_MENU,
            labelId = R.string.action_tab_menu,
            iconId = R.drawable.ic_more_vertical,
            viewId = R.id.menuItemTabMenu,
            availableInMainMenu = true,
            availableInTabMenu = false,
            defaultMenu = MenuType.MainMenu,
            optional = true
        ),
        MenuItemId.SESSIONS to MenuItem(
            id = MenuItemId.SESSIONS,
            labelId = R.string.action_sessions,
            iconId = R.drawable.ic_sessions,
            viewId = R.id.menuItemSessions,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.BOOKMARKS to MenuItem(
            id = MenuItemId.BOOKMARKS,
            labelId = R.string.action_bookmarks,
            iconId = R.drawable.ic_bookmarks,
            viewId = R.id.menuItemBookmarks,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.HISTORY to MenuItem(
            id = MenuItemId.HISTORY,
            labelId = R.string.action_history,
            iconId = R.drawable.ic_history,
            viewId = R.id.menuItemHistory,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.DOWNLOADS to MenuItem(
            id = MenuItemId.DOWNLOADS,
            labelId = R.string.action_downloads,
            iconId = R.drawable.ic_file_download,
            viewId = R.id.menuItemDownloads,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.NEW_TAB to MenuItem(
            id = MenuItemId.NEW_TAB,
            labelId = R.string.action_new_tab,
            iconId = R.drawable.ic_action_plus,
            viewId = R.id.menuItemNewTab,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.HiddenMenu
        ),
        MenuItemId.INCOGNITO to MenuItem(
            id = MenuItemId.INCOGNITO,
            labelId = R.string.action_incognito,
            iconId = R.drawable.ic_incognito,
            viewId = R.id.menuItemIncognito,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.OPTIONS to MenuItem(
            id = MenuItemId.OPTIONS,
            labelId = R.string.options,
            iconId = R.drawable.ic_build,
            viewId = R.id.menuItemOptions,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.SETTINGS to MenuItem(
            id = MenuItemId.SETTINGS,
            labelId = R.string.settings,
            iconId = R.drawable.ic_settings,
            viewId = R.id.menuItemSettings,
            availableInMainMenu = true,
            availableInTabMenu = true,
            mandatory = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.EXIT to MenuItem(
            id = MenuItemId.EXIT,
            labelId = R.string.exit,
            iconId = R.drawable.ic_action_delete,
            viewId = R.id.menuItemExit,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.HiddenMenu
        ),

        // Tab menu items (can also appear in both menus)
        MenuItemId.TAB_HISTORY to MenuItem(
            id = MenuItemId.TAB_HISTORY,
            labelId = R.string.settings_title_page_history,
            iconId = R.drawable.ic_history,
            viewId = R.id.menuItemPageHistory,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.SHARE to MenuItem(
            id = MenuItemId.SHARE,
            labelId = R.string.action_share,
            iconId = R.drawable.ic_share,
            viewId = R.id.menuItemShare,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.FIND to MenuItem(
            id = MenuItemId.FIND,
            labelId = R.string.action_find,
            iconId = R.drawable.ic_find_in_page,
            viewId = R.id.menuItemFind,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.TRANSLATE to MenuItem(
            id = MenuItemId.TRANSLATE,
            labelId = R.string.action_translate,
            iconId = R.drawable.ic_translate,
            viewId = R.id.menuItemTranslate,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.READER_MODE to MenuItem(
            id = MenuItemId.READER_MODE,
            labelId = R.string.reading_mode,
            iconId = R.drawable.ic_action_reading,
            viewId = R.id.menuItemReaderMode,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.AD_BLOCK to MenuItem(
            id = MenuItemId.AD_BLOCK,
            labelId = R.string.block_ads,
            iconId = R.drawable.ic_block,
            viewId = R.id.menuItemAdBlock,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.DARK_MODE to MenuItem(
            id = MenuItemId.DARK_MODE,
            labelId = R.string.dark_theme,
            iconId = R.drawable.ic_dark_mode,
            viewId = R.id.menuItemDarkMode,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.DESKTOP_MODE to MenuItem(
            id = MenuItemId.DESKTOP_MODE,
            labelId = R.string.agent_desktop,
            iconId = R.drawable.ic_desktop,
            viewId = R.id.menuItemDesktopMode,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.ADD_BOOKMARK to MenuItem(
            id = MenuItemId.ADD_BOOKMARK,
            labelId = R.string.action_add_bookmark,
            iconId = R.drawable.ic_bookmark_add,
            viewId = R.id.menuItemAddBookmark,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.PRINT to MenuItem(
            id = MenuItemId.PRINT,
            labelId = R.string.action_print,
            iconId = R.drawable.ic_action_print,
            viewId = R.id.menuItemPrint,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.REQUESTS to MenuItem(
            id = MenuItemId.REQUESTS,
            labelId = R.string.action_page_requests,
            iconId = R.drawable.ic_query,
            viewId = R.id.menuItemPageRequests,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.HiddenMenu
        ),
        MenuItemId.ADD_TO_HOME to MenuItem(
            id = MenuItemId.ADD_TO_HOME,
            labelId = R.string.action_add_to_homescreen,
            iconId = R.drawable.ic_add_to_home_screen,
            viewId = R.id.menuItemAddToHome,
            availableInMainMenu = true,
            availableInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        )
    )

    /**
     * Get a menu item by its ID
     */
    fun getItem(id: MenuItemId): MenuItem? = all[id]

    /**
     * Get all menu items
     */
    fun getAll(): List<MenuItem> = all.values.toList()

    /**
     * Get all menu items available for the main menu
     */
    fun getMainMenu(): List<MenuItem> =
        all.values.filter { it.availableInMainMenu }

    /**
     * Get all menu items available for the tab menu
     */
    fun getTabMenu(): List<MenuItem> =
        all.values.filter { it.availableInTabMenu }

}

