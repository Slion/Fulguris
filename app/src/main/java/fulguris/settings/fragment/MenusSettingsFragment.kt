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
import timber.log.Timber
import androidx.preference.Preference

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

    private fun getPreference(aViewHolder: RecyclerView.ViewHolder): Preference {
        return preferenceScreen.getPreference(aViewHolder.bindingAdapterPosition)
    }

    private fun getOrder(aViewHolder: RecyclerView.ViewHolder): Int {
        return preferenceScreen.getPreference(aViewHolder.bindingAdapterPosition).order
    }

    /**
     * Tracks the current drag operation state
     */
    private data class DragOperation(
        var active: Boolean = false,
        var startPosition: Int = -1,
        var startOrder: Int = -1,
        var currentPosition: Int = -1
    )

    private var iDrag = DragOperation()


    /**
     *
     */
    private fun populateMenuItems() {
        val prefScreen = preferenceScreen
        prefScreen.removeAll()

        var currentOrder = 0

        // Add help preference at the very top
        val helpPref = x.Preference(requireContext()).apply {
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
        val resetPref = x.Preference(requireContext()).apply {
            key = "reset_menus"
            title = getString(R.string.settings_title_reset_menus)
            icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_reset_menu)
            isIconSpaceReserved = true
            isSingleLineTitle = false
            order = currentOrder++
            setOnPreferenceClickListener {
                // Show confirmation dialog with reduced gap
                fulguris.dialog.BrowserDialog.show(
                    context = requireContext(),
                    title = R.string.dialog_title_reset_menus,
                    message = R.string.dialog_message_reset_menus,
                    positive = R.string.action_restore,
                    onPositive = {
                        // Reset to default configuration
                        menuConfig.clearConfiguration()
                        // Reload the menu items
                        populateMenuItems()
                    },
                    negative = android.R.string.cancel
                )
                true
            }
        }
        prefScreen.addPreference(resetPref)

        // Create fixed header preferences with sequential order
        val headerMain = x.Preference(requireContext()).apply {
            key = KEY_HEADER_MAIN
            title = getString(R.string.settings_title_main_menu)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            isAllCapsTitle = true
            setTitleTextColorFromTheme(android.R.attr.colorPrimary)
            order = currentOrder++
        }
        prefScreen.addPreference(headerMain)

        // Get all menu items from MenuItems model
        val allItems = MenuItems.getAll()

        // Try to load saved configuration
        val savedConfig = menuConfig.loadConfiguration()

        // Find items that are new (not in saved config)
        val savedItemIds = savedConfig.map { it.id }.toSet()
        val newItems = allItems.filter { it.id !in savedItemIds }

        // Organize new items by their default menu
        val newMainMenuItems = newItems.filter { it.defaultMenu == MenuType.MainMenu }
        val newTabMenuItems = newItems.filter { it.defaultMenu == MenuType.TabMenu }
        val newHiddenItems = newItems.filter { it.defaultMenu == MenuType.HiddenMenu || it.defaultMenu == MenuType.FullMenu }

        // Build menu with saved config, inserting new items in their default positions
        val mainMenuSaved = savedConfig.filter { it.menu == MenuType.MainMenu }.sortedBy { it.order }
        val tabMenuSaved = savedConfig.filter { it.menu == MenuType.TabMenu }.sortedBy { it.order }
        val hiddenSaved = savedConfig.filter { it.menu == MenuType.HiddenMenu || it.menu == MenuType.FullMenu }.sortedBy { it.order }

        // Helper function to create preference
        fun createPref(menuItem: fulguris.browser.MenuItem): x.Preference {
            return x.Preference(requireContext()).apply {
                title = getString(menuItem.labelId)
                icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_drag_handle_vertical)
                titleDrawableStart = menuItem.iconId
                key = menuItem.id.name
                isIconSpaceReserved = true
                isSingleLineTitle = false
                order = currentOrder++
            }
        }

        // Add Main Menu items (saved + new)
        mainMenuSaved.forEach { config ->
            val menuItem = allItems.find { it.id == config.id } ?: return@forEach
            prefScreen.addPreference(createPref(menuItem))
        }
        newMainMenuItems.forEach { menuItem ->
            prefScreen.addPreference(createPref(menuItem))
        }

        // Create tab menu header item
        val headerTab = x.Preference(requireContext()).apply {
            key = KEY_HEADER_TAB
            title = getString(R.string.action_tab_menu)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            isAllCapsTitle = true
            setTitleTextColorFromTheme(android.R.attr.colorPrimary)
            order = currentOrder++
        }
        prefScreen.addPreference(headerTab)

        // Add Tab Menu items (saved + new)
        tabMenuSaved.forEach { config ->
            val menuItem = allItems.find { it.id == config.id } ?: return@forEach
            prefScreen.addPreference(createPref(menuItem))
        }
        newTabMenuItems.forEach { menuItem ->
            prefScreen.addPreference(createPref(menuItem))
        }

        // Create hidden menu header item
        val headerHidden = x.Preference(requireContext()).apply {
            key = KEY_HEADER_HIDDEN
            title = getString(R.string.settings_title_hidden)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            isSelectable = false // Make it non-clickable without graying it out
            isAllCapsTitle = true
            setTitleTextColorFromTheme(android.R.attr.colorPrimary)
            order = currentOrder++
        }
        prefScreen.addPreference(headerHidden)

        // Add Hidden items (saved + new)
        hiddenSaved.forEach { config ->
            val menuItem = allItems.find { it.id == config.id } ?: return@forEach
            prefScreen.addPreference(createPref(menuItem))
        }
        newHiddenItems.forEach { menuItem ->
            prefScreen.addPreference(createPref(menuItem))
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

            /**
             * From [ItemTouchHelper.SimpleCallback.onMove]
             */
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                // Check for invalid positions
                // Invalid positions can happen after dragging an item over a section it can't go in
                // That should result in the drag operation somehow ending
                if (fromPosition == -1 || toPosition == -1) {
                    Timber.e("onMove error from $fromPosition to $toPosition")
                    return false
                }

                // Avoid spamming with logs
                if (iDrag.currentPosition != toPosition) {
                    iDrag.currentPosition = toPosition
                    Timber.d("onMove from $fromPosition to $toPosition")
                }

                val ps = preferenceScreen

                // Get preferences at both positions
                val draggedPref = ps.getPreference(fromPosition)
                val toPref = ps.getPreference(toPosition)

                // Don't allow dragging fixed preferences (reset button and headers)
                if (isFixedPreference(draggedPref.key)) {
                    Timber.e("Should never be moving fixed items as getMovementFlags disallows it")
                    return false
                }

                // Block dropping on non-header fixed items (help, reset)
//                if (!isHeaderPreference(toPref.key) && isFixedPreference(toPref.key)) {
//                    return false
//                }

                // Find the Main menu header order
                val mainHeaderOrder = findPreference<Preference>(KEY_HEADER_MAIN)!!.order

                // Don't allow moving items above or onto Main menu header
                if (toPref.order <= mainHeaderOrder) {
                    return false
                }

                // Determine source and target menu sections
                val fromMenuType = getMenuTypeForPreference(draggedPref)
                val toMenuType = getMenuTypeForPreference(toPref)

                // Check menu restrictions for the item
                val menuItemId = try {
                    MenuItemId.valueOf(draggedPref.key ?: "")
                } catch (e: Exception) {
                    null
                }

                val menuItem = menuItemId?.let { MenuItems.getItem(it) }

                // Enforce availableInMainMenu and availableInTabMenu restrictions
                // Only check if moving to a different menu section
                if (menuItem != null && fromMenuType != toMenuType) {
                    when (toMenuType) {
                        MenuType.MainMenu -> {
                            if (!menuItem.canBeInMainMenu) {
                                return false
                            }
                        }
                        MenuType.TabMenu -> {
                            if (!menuItem.canBeInTabMenu) {
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

                // Handle the reordering
                val prefsToOffset = mutableListOf<Preference>()
                val startPos = if (toPosition > fromPosition) fromPosition else toPosition
                val endPos = if (toPosition > fromPosition) toPosition else fromPosition

                // Collect all preferences EXCEPT the dragged item
                for (i in startPos..endPos) {
                    if (i != fromPosition) {
                        val pref = ps.getPreference(i)
                        prefsToOffset.add(pref)
                    }
                }

                // Compute the increment: +1 for moving up, -1 for moving down
                val increment = if (toPosition > fromPosition) -1 else +1

                if (prefsToOffset.size>1) {
                    Timber.i("onMove jump over ${prefsToOffset.size} items")
                }

                // Shift all affected items
                for (pref in prefsToOffset) {
                    pref.order += increment
                }

                // Put dragged item at target position
                draggedPref.order = iDrag.currentPosition

                return true
            }

            /**
             * From [ItemTouchHelper.SimpleCallback.onSelectedChanged]
             */
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                // Save configuration when drag ends
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    Timber.d("Drag stops at ${iDrag.currentPosition}")
                    iDrag.active = false
                    saveCurrentConfiguration()
                } else if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // We must always keep order and position in sync as that's how we manipulate the preference model
                    iDrag.apply {
                        active = true
                        startPosition = viewHolder!!.bindingAdapterPosition
                        startOrder = getOrder(viewHolder)
                        currentPosition = viewHolder.bindingAdapterPosition
                    }

                    Timber.d("Drag starts at ${iDrag.startPosition} order ${iDrag.startOrder}")
                }
            }

            /**
             * From [ItemTouchHelper.SimpleCallback.onSwiped]
             */
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

                // Find all header orders
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

                if (hiddenHeaderOrder == -1) {
                    // Hidden header not found, restore item
                    listView.adapter?.notifyItemChanged(position)
                    return
                }

                // Determine current menu type
                val currentMenuType = getMenuTypeForPreference(pref)

                // Variable to store the new order for position calculation
                var newOrder: Int = -1

                if (currentMenuType == MenuType.HiddenMenu) {
                    // Item is in Hidden section - move to its preferred menu
                    if (menuItem == null) {
                        listView.adapter?.notifyItemChanged(position)
                        return
                    }

                    val targetMenuType = menuItem.preferredMenu

                    // Ensure target menu type is valid (not FullMenu or HiddenMenu)
                    val finalTargetMenu = when {
                        targetMenuType == MenuType.FullMenu || targetMenuType == MenuType.HiddenMenu -> {
                            // Fall back to MainMenu if preferred is not a valid target
                            if (menuItem.canBeInMainMenu) MenuType.MainMenu
                            else if (menuItem.canBeInTabMenu) MenuType.TabMenu
                            else MenuType.HiddenMenu // Keep hidden if not available anywhere
                        }
                        targetMenuType == MenuType.MainMenu && !menuItem.canBeInMainMenu -> {
                            // Can't go to MainMenu, try TabMenu
                            if (menuItem.canBeInTabMenu) MenuType.TabMenu
                            else MenuType.HiddenMenu
                        }
                        targetMenuType == MenuType.TabMenu && !menuItem.canBeInTabMenu -> {
                            // Can't go to TabMenu, try MainMenu
                            if (menuItem.canBeInMainMenu) MenuType.MainMenu
                            else MenuType.HiddenMenu
                        }
                        else -> targetMenuType
                    }

                    if (finalTargetMenu == MenuType.HiddenMenu) {
                        // Can't move anywhere, restore it
                        listView.adapter?.notifyItemChanged(position)
                        return
                    }

                    // Find the target section to place the item
                    val targetHeaderOrder = if (finalTargetMenu == MenuType.MainMenu) mainHeaderOrder else tabHeaderOrder
                    val nextHeaderOrder = if (finalTargetMenu == MenuType.MainMenu) tabHeaderOrder else hiddenHeaderOrder

                    // Find the maximum order in the target section
                    var maxOrderInTargetSection = targetHeaderOrder
                    for (i in 0 until prefScreen.preferenceCount) {
                        val p = prefScreen.getPreference(i)
                        // Find items between the target header and next header (excluding our moving item)
                        if (p != pref && p.order > targetHeaderOrder && p.order < nextHeaderOrder &&
                            !isHeaderPreference(p.key) && !isFixedPreference(p.key)) {
                            if (p.order > maxOrderInTargetSection) {
                                maxOrderInTargetSection = p.order
                            }
                        }
                    }

                    // Place item right after the last item in target section (or right after header if empty)
                    newOrder = maxOrderInTargetSection + 1

                    // If the new order would overlap with the next header, we need to shift the next header
                    if (newOrder >= nextHeaderOrder) {
                        // Shift the next header and all items after it
                        val shiftAmount = newOrder - nextHeaderOrder + 1
                        for (i in 0 until prefScreen.preferenceCount) {
                            val p = prefScreen.getPreference(i)
                            if (p != pref && p.order >= nextHeaderOrder) {
                                p.order += shiftAmount
                            }
                        }
                    }
                } else {
                    // Item is in MainMenu or TabMenu - move to Hidden section
                    // Move to end of Hidden section
                    var maxOrder = hiddenHeaderOrder
                    for (i in 0 until prefScreen.preferenceCount) {
                        val p = prefScreen.getPreference(i)
                        if (p.order > maxOrder && !isHeaderPreference(p.key)) {
                            maxOrder = p.order
                        }
                    }

                    newOrder = maxOrder + 1
                }

                // Calculate the new position BEFORE changing pref.order
                // Count how many preferences will have a lower order than the item's new position
                var newPosition = 0
                for (i in 0 until prefScreen.preferenceCount) {
                    val p = prefScreen.getPreference(i)
                    if (p != pref && p.order < newOrder) {
                        newPosition++
                    }
                }

                // Now set the new order
                // That's taking notifying the list view adapter of what was changed
                // However I'm guessing because of the swipe the new item position is left blank
                pref.order = newOrder
                //Timber.d("Moving item from $position to $newPosition")
                // That's the only workaround that worked, it nicely triggers the missing item to animate in
                listView.postDelayed({listView.adapter?.notifyItemChanged(newPosition)}, 200)

                // Save configuration after swipe
                saveCurrentConfiguration()
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }

            /**
             * From [ItemTouchHelper.SimpleCallback.getSwipeDirs]
             */
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

                val menuItemId = try {
                    MenuItemId.valueOf(pref.key ?: "")
                } catch (e: Exception) {
                    null
                }

                // Check if item is mandatory
                val menuItem = menuItemId?.let { MenuItems.getItem(it) }
                if (menuItem?.mandatory == true) {
                    return 0 // No swipe for mandatory items
                }

                // Allow swiping for all other items (both to hide and to unhide)
                return super.getSwipeDirs(recyclerView, viewHolder)
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
