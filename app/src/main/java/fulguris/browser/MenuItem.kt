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

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes

/**
 * Represents a menu item with its properties
 *
 * @param id Unique identifier for this menu item
 * @param labelId String resource ID for the item label
 * @param iconId Drawable resource ID for the item icon
 * @param viewId View resource ID (R.id.menuItem*) in menu_custom.xml, 0 if not in layout
 * @param canBeInMainMenu Whether this item can appear in the main menu
 * @param canBeInTabMenu Whether this item can appear in the tab menu
 * @param canBeHidden Whether this item must always be present and cannot be removed
 * @param defaultMenu Which menu this item appears in by default (MainMenu, TabMenu, or Hidden)
 * @param preferredMenu Which menu to use when swiping hidden items back (defaults to defaultMenu for non-hidden items)
 * @param optional Optional items are not shown in the full menu (MenuType.FullMenu)
 */
data class MenuItem(
    val id: MenuItemId,
    @StringRes val labelId: Int,
    @DrawableRes val iconId: Int,
    @IdRes val viewId: Int = 0,
    val canBeInMainMenu: Boolean,
    val canBeInTabMenu: Boolean,
    val canBeHidden: Boolean = true,
    val defaultMenu: MenuType = MenuType.HiddenMenu,
    val preferredMenu: MenuType = defaultMenu,
    val optional: Boolean = false
)

