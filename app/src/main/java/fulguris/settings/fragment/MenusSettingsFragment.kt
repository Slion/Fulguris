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

package fulguris.settings.fragment

import android.os.Bundle
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.browser.MenuItems
import fulguris.browser.MenuItemId
import fulguris.browser.MenuConfiguration
import fulguris.browser.MenuType
import fulguris.browser.MenuItemConfig

/**
 * Main menu settings screen - configures both Main Menu and Tab Menu.
 */
@AndroidEntryPoint
class MenusSettingsFragment : AbstractSettingsFragment() {

    // Fixed header preference keys
    private val KEY_HEADER_MAIN = "header_main_menu"
    private val KEY_HEADER_TAB = "header_tab_menu"
    private val KEY_HEADER_HIDDEN = "header_hidden"

    private lateinit var menuConfig: MenuConfiguration

    override fun providePreferencesXmlResource() = R.xml.preference_menus

    override fun titleResourceId(): Int = R.string.settings_title_menus

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // Initialize configuration manager
        menuConfig = MenuConfiguration(requireContext())

        // Populate with header preferences and menu items
        populateMenuItems()
    }

    private fun populateMenuItems() {
        val prefScreen = preferenceScreen
        prefScreen.removeAll()

        var currentOrder = 0

        // Add help preference at the very top
        val helpPref = slions.pref.BasicPreference(requireContext()).apply {
            key = "help_menus"
            summary = getString(R.string.settings_help_menus)
            isSingleLineSummary = false
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            order = currentOrder++
        }
        prefScreen.addPreference(helpPref)

        // Add reset preference
        val resetPref = slions.pref.BasicPreference(requireContext()).apply {
            key = "reset_menus"
            title = getString(R.string.generic_reset)
            summary = getString(R.string.settings_summary_reset_menus)
            icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_reset_settings)
            isIconSpaceReserved = true
            isSingleLineTitle = false
            order = currentOrder++
            setOnPreferenceClickListener {
                // Show confirmation dialog
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.generic_reset)
                    .setMessage(R.string.settings_confirm_reset_menus)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.action_ok) { _, _ ->
                        // Reset to default configuration
                        menuConfig.clearConfiguration()
                        // Reload the menu items
                        populateMenuItems()
                    }
                    .show()
                true
            }
        }
        prefScreen.addPreference(resetPref)

        // Create fixed header preferences with sequential order
        val headerMain = slions.pref.BasicPreference(requireContext()).apply {
            key = KEY_HEADER_MAIN
            title = getString(R.string.settings_title_main_menu)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            setTitleTextColorFromTheme(android.R.attr.colorPrimary)
            order = currentOrder++
        }
        prefScreen.addPreference(headerMain)

        val headerTab = slions.pref.BasicPreference(requireContext()).apply {
            key = KEY_HEADER_TAB
            title = getString(R.string.action_tab_menu)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            setTitleTextColorFromTheme(android.R.attr.colorPrimary)
            order = currentOrder++
        }
        prefScreen.addPreference(headerTab)

        val headerHidden = slions.pref.BasicPreference(requireContext()).apply {
            key = KEY_HEADER_HIDDEN
            title = getString(R.string.settings_title_hidden)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            setTitleTextColorFromTheme(android.R.attr.colorPrimary)
            order = currentOrder++
        }
        prefScreen.addPreference(headerHidden)

        // Get all menu items from MenuItems model
        val allItems = MenuItems.getAll()

        // Try to load saved configuration
        val savedConfig = menuConfig.loadConfiguration()

        if (savedConfig != null) {
            // Use saved configuration
            savedConfig.sortedBy { it.order }.forEach { config ->
                val menuItem = allItems.find { it.id == config.id } ?: return@forEach

                val pref = slions.pref.BasicPreference(requireContext()).apply {
                    title = getString(menuItem.labelId)
                    icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_drag_handle_vertical)
                    titleDrawableStart = menuItem.iconId
                    key = menuItem.id.name
                    isIconSpaceReserved = true
                    isSingleLineTitle = false
                    order = currentOrder++
                }
                prefScreen.addPreference(pref)

                // Update header positions as we build
                when (config.menu) {
                    MenuType.MainMenu -> {
                        // Item is in main menu
                        if (headerTab.order <= pref.order) {
                            headerTab.order = currentOrder++
                        }
                        if (headerHidden.order <= pref.order) {
                            headerHidden.order = currentOrder++
                        }
                    }
                    MenuType.TabMenu -> {
                        // Item is in tab menu
                        if (headerHidden.order <= pref.order) {
                            headerHidden.order = currentOrder++
                        }
                    }
                    MenuType.HiddenMenu -> {
                        // Item is hidden
                    }
                    MenuType.FullMenu -> {
                        // All mode is not used in configuration, treat as hidden
                    }
                }
            }
        } else {
            // Use default configuration from MenuItems model
            val mainMenuItems = mutableListOf<fulguris.browser.MenuItem>()
            val tabMenuItems = mutableListOf<fulguris.browser.MenuItem>()
            val hiddenItems = mutableListOf<fulguris.browser.MenuItem>()

            allItems.forEach { menuItem ->
                when (menuItem.defaultMenu) {
                    MenuType.MainMenu -> mainMenuItems.add(menuItem)
                    MenuType.TabMenu -> tabMenuItems.add(menuItem)
                    MenuType.HiddenMenu -> hiddenItems.add(menuItem)
                    MenuType.FullMenu -> {
                        // All is not a valid defaultMenu, but handle it anyway by treating as hidden
                        hiddenItems.add(menuItem)
                    }
                }
            }

            // Add Main Menu items right after Main Menu header
            mainMenuItems.forEach { menuItem ->
                val pref = slions.pref.BasicPreference(requireContext()).apply {
                    title = getString(menuItem.labelId)
                    icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_drag_handle_vertical)
                    titleDrawableStart = menuItem.iconId
                    key = menuItem.id.name
                    isIconSpaceReserved = true
                    isSingleLineTitle = false
                    order = currentOrder++
                }
                prefScreen.addPreference(pref)
            }

            // Update Tab Menu header order to be after Main Menu items
            headerTab.order = currentOrder++

            // Add Tab Menu items right after Tab Menu header
            tabMenuItems.forEach { menuItem ->
                val pref = slions.pref.BasicPreference(requireContext()).apply {
                    title = getString(menuItem.labelId)
                    icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_drag_handle_vertical)
                    titleDrawableStart = menuItem.iconId
                    key = menuItem.id.name
                    isIconSpaceReserved = true
                    isSingleLineTitle = false
                    order = currentOrder++
                }
                prefScreen.addPreference(pref)
            }

            // Update Hidden header order to be after Tab Menu items
            headerHidden.order = currentOrder++

            // Add Hidden items after Hidden header
            hiddenItems.forEach { menuItem ->
                val pref = slions.pref.BasicPreference(requireContext()).apply {
                    title = getString(menuItem.labelId)
                    icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_drag_handle_vertical)
                    titleDrawableStart = menuItem.iconId
                    key = menuItem.id.name
                    isIconSpaceReserved = true
                    isSingleLineTitle = false
                    order = currentOrder++
                }

                prefScreen.addPreference(pref)
            }
        }
    }

    private fun isHeaderPreference(key: String?): Boolean {
        return key == KEY_HEADER_MAIN || key == KEY_HEADER_TAB || key == KEY_HEADER_HIDDEN
    }

    private fun isFixedPreference(key: String?): Boolean {
        return key == "help_menus" || key == "reset_menus" || isHeaderPreference(key)
    }

    /**
     * Determine which menu type a preference belongs to based on its order
     */
    private fun getMenuTypeForPreference(pref: androidx.preference.Preference): MenuType {
        val prefScreen = preferenceScreen

        // Find header orders
        var mainHeaderOrder = -1
        var tabHeaderOrder = -1
        var hiddenHeaderOrder = -1

        for (i in 0 until prefScreen.preferenceCount) {
            val p = prefScreen.getPreference(i)
            when (p.key) {
                KEY_HEADER_MAIN -> mainHeaderOrder = p.order
                KEY_HEADER_TAB -> tabHeaderOrder = p.order
                KEY_HEADER_HIDDEN -> hiddenHeaderOrder = p.order
            }
        }

        // Determine menu based on order relative to headers
        return when {
            pref.order < tabHeaderOrder -> MenuType.MainMenu
            pref.order < hiddenHeaderOrder -> MenuType.TabMenu
            else -> MenuType.HiddenMenu
        }
    }

    /**
     * Save the current menu configuration
     */
    private fun saveCurrentConfiguration() {
        val prefScreen = preferenceScreen
        val items = mutableListOf<MenuItemConfig>()

        for (i in 0 until prefScreen.preferenceCount) {
            val pref = prefScreen.getPreference(i)

            // Skip headers
            if (isHeaderPreference(pref.key)) continue

            // Get menu item ID
            val menuItemId = try {
                MenuItemId.valueOf(pref.key ?: continue)
            } catch (e: Exception) {
                continue
            }

            // Determine which menu it belongs to
            val menuType = getMenuTypeForPreference(pref)

            items.add(MenuItemConfig(menuItemId, menuType, pref.order))
        }

        menuConfig.saveConfiguration(items)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up drag and drop for reordering menu items
        val recyclerView = listView

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END // Enable swipe left/right
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                val prefScreen = preferenceScreen

                if (position >= 0 && position < prefScreen.preferenceCount) {
                    val pref = prefScreen.getPreference(position)

                    // Disable all movement for fixed preferences (reset button and headers)
                    if (isFixedPreference(pref.key)) {
                        return makeMovementFlags(0, 0)
                    }
                }

                // Default movement flags for regular items
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                val prefScreen = preferenceScreen

                // Get preferences at both positions
                val fromPref = prefScreen.getPreference(fromPosition)
                val toPref = prefScreen.getPreference(toPosition)

                // Don't allow dragging fixed preferences (reset button and headers)
                if (isFixedPreference(fromPref.key)) {
                    return false
                }

                // Block dropping on non-header fixed items (help, reset)
                if (!isHeaderPreference(toPref.key) && isFixedPreference(toPref.key)) {
                    return false
                }

                // Find the Main menu header order
                var mainHeaderOrder = -1
                for (i in 0 until prefScreen.preferenceCount) {
                    val p = prefScreen.getPreference(i)
                    if (p.key == KEY_HEADER_MAIN) {
                        mainHeaderOrder = p.order
                        break
                    }
                }

                // Don't allow moving items above or onto Main menu header
                if (mainHeaderOrder != -1 && toPref.order <= mainHeaderOrder) {
                    return false
                }

                // Determine source and target menu sections
                val fromMenuType = getMenuTypeForPreference(fromPref)
                val toMenuType = getMenuTypeForPreference(toPref)

                // Check menu restrictions for the item
                val menuItemId = try {
                    MenuItemId.valueOf(fromPref.key ?: "")
                } catch (e: Exception) {
                    null
                }

                val menuItem = menuItemId?.let { MenuItems.getItem(it) }

                // Enforce availableInMainMenu and availableInTabMenu restrictions
                // Only check if moving to a different menu section
                if (menuItem != null && fromMenuType != toMenuType) {
                    when (toMenuType) {
                        MenuType.MainMenu -> {
                            if (!menuItem.availableInMainMenu) {
                                return false
                            }
                        }
                        MenuType.TabMenu -> {
                            if (!menuItem.availableInTabMenu) {
                                return false
                            }
                        }
                        MenuType.HiddenMenu -> {
                            // Mandatory items cannot be hidden
                            if (menuItem.mandatory) {
                                return false
                            }
                        }
                        MenuType.FullMenu -> {
                            // All mode is not used in drag and drop, should never reach here
                            // But if it does, don't block the move
                        }
                    }
                }

                val fromOrder = fromPref.order
                val toOrder = toPref.order

                // Simple order swap - works for both same-menu and cross-menu moves
                // The menu section is determined by the order relative to headers
                fromPref.order = toOrder
                toPref.order = fromOrder

                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                // Save configuration when drag ends
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    saveCurrentConfiguration()
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val prefScreen = preferenceScreen

                if (position >= prefScreen.preferenceCount) {
                    return
                }

                val pref = prefScreen.getPreference(position)

                // Check if item is mandatory
                val menuItemId = try {
                    MenuItemId.valueOf(pref.key ?: "")
                } catch (e: Exception) {
                    null
                }

                val menuItem = menuItemId?.let { MenuItems.getItem(it) }
                if (menuItem?.mandatory == true) {
                    // Don't allow swiping mandatory items - restore it
                    listView.adapter?.notifyItemChanged(position)
                    return
                }

                // Find the Hidden header position
                var hiddenHeaderOrder = -1
                for (i in 0 until prefScreen.preferenceCount) {
                    val p = prefScreen.getPreference(i)
                    if (p.key == KEY_HEADER_HIDDEN) {
                        hiddenHeaderOrder = p.order
                        break
                    }
                }

                if (hiddenHeaderOrder == -1) {
                    // Hidden header not found, restore item
                    listView.adapter?.notifyItemChanged(position)
                    return
                }

                // Check if already after Hidden header (in Hidden section)
                if (pref.order > hiddenHeaderOrder) {
                    // Already in Hidden section, don't do anything
                    listView.adapter?.notifyItemChanged(position)
                    return
                }

                // Move to end of list (after all other items)
                var maxOrder = hiddenHeaderOrder
                for (i in 0 until prefScreen.preferenceCount) {
                    val p = prefScreen.getPreference(i)
                    if (p.order > maxOrder && !isHeaderPreference(p.key)) {
                        maxOrder = p.order
                    }
                }

                pref.order = maxOrder + 1

                // Save configuration after swipe
                saveCurrentConfiguration()
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.bindingAdapterPosition
                val prefScreen = preferenceScreen

                if (position >= prefScreen.preferenceCount) {
                    return 0
                }

                val pref = prefScreen.getPreference(position)

                // Don't allow swiping fixed preferences (reset button and headers)
                if (isFixedPreference(pref.key)) {
                    return 0
                }

                // Check if item is mandatory
                val menuItemId = try {
                    MenuItemId.valueOf(pref.key ?: "")
                } catch (e: Exception) {
                    null
                }

                val menuItem = menuItemId?.let { MenuItems.getItem(it) }
                if (menuItem?.mandatory == true) {
                    return 0 // No swipe for mandatory items
                }

                // Find the Hidden header order
                var hiddenHeaderOrder = -1
                for (i in 0 until prefScreen.preferenceCount) {
                    val p = prefScreen.getPreference(i)
                    if (p.key == KEY_HEADER_HIDDEN) {
                        hiddenHeaderOrder = p.order
                        break
                    }
                }

                // Don't allow swiping items that are already after Hidden header (in Hidden section)
                if (hiddenHeaderOrder != -1 && pref.order > hiddenHeaderOrder) {
                    return 0
                }

                return super.getSwipeDirs(recyclerView, viewHolder)
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}

