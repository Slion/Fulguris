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
        MenuItemId.MainMenu to MenuItem(
            id = MenuItemId.MainMenu,
            labelId = R.string.action_main_menu,
            iconId = R.drawable.ic_menu,
            viewId = R.id.menuItemMainMenu,
            canBeInMainMenu = false,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu,
            optional = true
        ),
        MenuItemId.TabMenu to MenuItem(
            id = MenuItemId.TabMenu,
            labelId = R.string.action_tab_menu,
            iconId = R.drawable.ic_more_vertical,
            viewId = R.id.menuItemTabMenu,
            canBeInMainMenu = true,
            canBeInTabMenu = false,
            defaultMenu = MenuType.MainMenu,
            optional = true
        ),
        MenuItemId.Sessions to MenuItem(
            id = MenuItemId.Sessions,
            labelId = R.string.action_sessions,
            iconId = R.drawable.ic_sessions,
            viewId = R.id.menuItemSessions,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.Bookmarks to MenuItem(
            id = MenuItemId.Bookmarks,
            labelId = R.string.action_bookmarks,
            iconId = R.drawable.ic_bookmarks,
            viewId = R.id.menuItemBookmarks,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.History to MenuItem(
            id = MenuItemId.History,
            labelId = R.string.action_history,
            iconId = R.drawable.ic_history,
            viewId = R.id.menuItemHistory,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.Downloads to MenuItem(
            id = MenuItemId.Downloads,
            labelId = R.string.action_downloads,
            iconId = R.drawable.ic_file_download,
            viewId = R.id.menuItemDownloads,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.NewTab to MenuItem(
            id = MenuItemId.NewTab,
            labelId = R.string.action_new_tab,
            iconId = R.drawable.ic_action_plus,
            viewId = R.id.menuItemNewTab,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.HiddenMenu,
            preferredMenu = MenuType.MainMenu
        ),
        MenuItemId.Incognito to MenuItem(
            id = MenuItemId.Incognito,
            labelId = R.string.action_incognito,
            iconId = R.drawable.ic_incognito,
            viewId = R.id.menuItemIncognito,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.Options to MenuItem(
            id = MenuItemId.Options,
            labelId = R.string.options,
            iconId = R.drawable.ic_build,
            viewId = R.id.menuItemOptions,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.Settings to MenuItem(
            id = MenuItemId.Settings,
            labelId = R.string.settings,
            iconId = R.drawable.ic_settings,
            viewId = R.id.menuItemSettings,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            mandatory = true,
            defaultMenu = MenuType.MainMenu
        ),
        MenuItemId.Exit to MenuItem(
            id = MenuItemId.Exit,
            labelId = R.string.exit,
            iconId = R.drawable.ic_action_delete,
            viewId = R.id.menuItemExit,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.HiddenMenu,
            preferredMenu = MenuType.MainMenu
        ),

        // Tab menu items (can also appear in both menus)
        MenuItemId.TabHistory to MenuItem(
            id = MenuItemId.TabHistory,
            labelId = R.string.settings_title_page_history,
            iconId = R.drawable.ic_history,
            viewId = R.id.menuItemPageHistory,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.Share to MenuItem(
            id = MenuItemId.Share,
            labelId = R.string.action_share,
            iconId = R.drawable.ic_share,
            viewId = R.id.menuItemShare,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.Find to MenuItem(
            id = MenuItemId.Find,
            labelId = R.string.action_find,
            iconId = R.drawable.ic_find_in_page,
            viewId = R.id.menuItemFind,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.Translate to MenuItem(
            id = MenuItemId.Translate,
            labelId = R.string.action_translate,
            iconId = R.drawable.ic_translate,
            viewId = R.id.menuItemTranslate,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.ReaderMode to MenuItem(
            id = MenuItemId.ReaderMode,
            labelId = R.string.reading_mode,
            iconId = R.drawable.ic_action_reading,
            viewId = R.id.menuItemReaderMode,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.AdBlock to MenuItem(
            id = MenuItemId.AdBlock,
            labelId = R.string.block_ads,
            iconId = R.drawable.ic_block,
            viewId = R.id.menuItemAdBlock,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.DarkMode to MenuItem(
            id = MenuItemId.DarkMode,
            labelId = R.string.theme_dark,
            iconId = R.drawable.ic_dark_mode,
            viewId = R.id.menuItemDarkMode,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.DesktopMode to MenuItem(
            id = MenuItemId.DesktopMode,
            labelId = R.string.agent_desktop,
            iconId = R.drawable.ic_desktop,
            viewId = R.id.menuItemDesktopMode,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.DomainSettings to MenuItem(
            id = MenuItemId.DomainSettings,
            labelId = R.string.settings_title_domains,
            iconId = R.drawable.ic_domain,
            viewId = R.id.menuItemDomainSettings,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.AddBookmark to MenuItem(
            id = MenuItemId.AddBookmark,
            labelId = R.string.action_add_bookmark,
            iconId = R.drawable.ic_bookmark_add,
            viewId = R.id.menuItemAddBookmark,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.Print to MenuItem(
            id = MenuItemId.Print,
            labelId = R.string.action_print,
            iconId = R.drawable.ic_action_print,
            viewId = R.id.menuItemPrint,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.TabMenu
        ),
        MenuItemId.Requests to MenuItem(
            id = MenuItemId.Requests,
            labelId = R.string.action_page_requests,
            iconId = R.drawable.ic_query,
            viewId = R.id.menuItemPageRequests,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
            defaultMenu = MenuType.HiddenMenu,
            preferredMenu = MenuType.TabMenu
        ),
        MenuItemId.AddToHome to MenuItem(
            id = MenuItemId.AddToHome,
            labelId = R.string.action_add_to_homescreen,
            iconId = R.drawable.ic_add_to_home_screen,
            viewId = R.id.menuItemAddToHome,
            canBeInMainMenu = true,
            canBeInTabMenu = true,
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
        all.values.filter { it.canBeInMainMenu }

    /**
     * Get all menu items available for the tab menu
     */
    fun getTabMenu(): List<MenuItem> =
        all.values.filter { it.canBeInTabMenu }

}

