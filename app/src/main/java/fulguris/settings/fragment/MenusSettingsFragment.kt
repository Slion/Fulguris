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

    override fun titleResourceId(): Int = R.string.pref_title_menus

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
            title = getString(R.string.pref_title_reset_menus)
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
            title = getString(R.string.pref_title_main_menu)
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
            title = getString(R.string.pref_title_hidden)
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
    private fun getMenuTypeForPreference(pref: Preference): MenuType {

        // Find header orders
        //val mainHeaderOrder = findPreference<Preference>(KEY_HEADER_MAIN)!!.order
        val tabHeaderOrder = findPreference<Preference>(KEY_HEADER_TAB)!!.order
        val hiddenHeaderOrder = findPreference<Preference>(KEY_HEADER_HIDDEN)!!.order

        // Determine menu based on order relative to headers
        return when {
            pref.order > hiddenHeaderOrder -> MenuType.HiddenMenu
            pref.order > tabHeaderOrder -> MenuType.TabMenu
            else -> MenuType.MainMenu
        }
    }

    /**
     * Move a preference to a specific order position, shifting all items as needed.
     * This handles the reordering logic consistently across drag, swipe, and validation.
     *
     * @param pref The preference to move
     * @param targetOrder The desired order value for the preference
     */
    private fun moveToPosition(pref: Preference, targetOrder: Int) {
        val prefScreen = preferenceScreen
        val currentOrder = pref.order

        if (currentOrder == targetOrder) {
            return // Already at target position
        }

        val movingDown = targetOrder > currentOrder

        // Collect all preferences that need to be shifted
        val prefsToShift = mutableListOf<Preference>()
        val startOrder = if (movingDown) currentOrder else targetOrder
        val endOrder = if (movingDown) targetOrder else currentOrder

        for (i in startOrder .. endOrder) {
            val p = prefScreen.getPreference(i)
            prefsToShift.add(p)
        }

        // Compute the increment: -1 when moving down (items shift up), +1 when moving up (items shift down)
        val increment = if (movingDown) -1 else +1

        // Shift all affected items
        for (p in prefsToShift) {
            p.order += increment
        }

        // Move the target preference to its new position
        pref.order = targetOrder

        Timber.d("moveToPosition: ${pref.key} from $currentOrder to $targetOrder (shifted ${prefsToShift.size} items)")
    }

    /**
     * Move a preference to a specified menu, placing it at the end of that menu section.
     *
     * @param aPref The preference to move
     * @param aMenu The target menu type
     * @return The new position of the item in the preference screen
     */
    private fun moveToMenu(aPref: Preference, aMenu: MenuType): Int {
        val prefScreen = preferenceScreen

        // Lets work out the position we need to love our preference to
        val position = if (aMenu==MenuType.HiddenMenu) {
            // Moving to hidden menu means it needs to be the last item on our screen
            prefScreen.preferenceCount-1
        } else if (aMenu==MenuType.TabMenu) {
            // Moving to tab menu means it takes the position of the hidden menu label
            findPreference<Preference>(KEY_HEADER_HIDDEN)!!.order
        } else {
            // Moving to main menu means it takes the position of the tab menu label
            findPreference<Preference>(KEY_HEADER_TAB)!!.order
        }

        // Use moveToPosition to handle the actual move and shifting
        moveToPosition(aPref, position)
        //
        return position
    }

    /**
     * Validate and fix the current configuration before saving.
     * Ensures:
     * - All items are in valid menus (canBeInMainMenu/canBeInTabMenu)
     * - Mandatory items are not hidden
     * - Items that violate rules are moved back to their default menu
     *
     * @return true if any corrections were made
     */
    private fun checkAndFixConfiguration(): Boolean {
        val prefScreen = preferenceScreen
        var correctionsMade = false

        for (i in 0 until prefScreen.preferenceCount) {
            val pref = prefScreen.getPreference(i)

            // Skip headers and fixed preferences
            if (isFixedPreference(pref.key)) continue

            // Get menu item ID and metadata
            val menuItemId = try {
                MenuItemId.valueOf(pref.key ?: continue)
            } catch (e: Exception) {
                continue
            }

            val menuItem = MenuItems.getItem(menuItemId) ?: continue
            val currentMenu = getMenuTypeForPreference(pref)

            // Check if item is in a valid menu
            val isValid = when (currentMenu) {
                MenuType.MainMenu -> menuItem.canBeInMainMenu
                MenuType.TabMenu -> menuItem.canBeInTabMenu
                MenuType.HiddenMenu -> menuItem.canBeHidden
                MenuType.FullMenu -> true
            }

            if (!isValid) {
                // Move item back to its default menu using moveToMenu helper
                val targetMenu = menuItem.defaultMenu
                moveToMenu(pref, targetMenu)

                Timber.w("Configuration fix: ${menuItem.id} moved to $targetMenu")
                correctionsMade = true
            }
        }

        return correctionsMade
    }

    /**
     * Save the current menu configuration
     */
    private fun saveCurrentConfiguration() {
        // First, validate and fix any configuration issues
        checkAndFixConfiguration()

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
            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }

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
             * From [ItemTouchHelper.SimpleCallback.onSelectedChanged]
             * That's where drag starts and stops.
             */
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                // Save configuration when drag ends
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    Timber.d("Drag stops at ${iDrag.currentPosition}")
                    iDrag.active = false
                    // Need delay otherwise you could get stuck duplicate item view
                    // Notably when moving up tab menu item into tab menu section which triggers a move to main menu
                    view.postDelayed({saveCurrentConfiguration()},300)
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

                val ps = preferenceScreen

                // Get preferences at both positions
                val draggedPref = ps.getPreference(fromPosition)
                val toPref = ps.getPreference(toPosition)

                // Avoid spamming with logs
                if (iDrag.currentPosition != toPosition) {
                    iDrag.currentPosition = toPosition
                    Timber.d("onMove from $fromPosition to $toPosition")
                }

                // Find the Main menu header order
                val mainHeaderOrder = findPreference<Preference>(KEY_HEADER_MAIN)!!.order

                // Don't allow moving items above Main menu header
                if (toPref.order <= mainHeaderOrder) {
                    return false
                }

                // Allow every other placement, we check for consistency when drag ends
                // Use moveToPosition to handle the reordering with proper shifting
                moveToPosition(draggedPref, toPref.order)

                return true
            }

            /**
             * From [ItemTouchHelper.SimpleCallback.onSwiped]
             */
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val prefScreen = preferenceScreen

                val pref = prefScreen.getPreference(position)

                // Should always work as we won't let user swipe preferences that are not items
                val menuItemId = MenuItemId.valueOf(pref.key ?: "")
                val menuItem = MenuItems.getItem(menuItemId)
                // Determine current menu type
                val currentMenuType = getMenuTypeForPreference(pref)

                // Determine target menu
                val targetMenu = if (currentMenuType == MenuType.HiddenMenu) {
                    // Move to preferred menu
                   menuItem!!.preferredMenu
                } else {
                    // Item is in MainMenu or TabMenu - move to Hidden section
                    MenuType.HiddenMenu
                }

                // Use moveToMenu to handle the move with proper shifting
                val newPosition = moveToMenu(pref, targetMenu)

                // Notify adapter to animate the item into its new position
                listView.postDelayed({listView.adapter?.notifyItemChanged(newPosition)}, 200)

                // Save configuration after swipe
                saveCurrentConfiguration()
            }

            /**
             * From [ItemTouchHelper.SimpleCallback.getSwipeDirs]
             */
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.bindingAdapterPosition
                val prefScreen = preferenceScreen

                val pref = prefScreen.getPreference(position)

                // Don't allow swiping fixed preferences and menu section headers
                if (isFixedPreference(pref.key) || isHeaderPreference(pref.key)) {
                    return 0
                }
                // We should be dealing with an actual menu item then
                val menuItemId = MenuItemId.valueOf(pref.key)
                // Check if item is mandatory
                val menuItem = MenuItems.getItem(menuItemId)
                if (menuItem?.canBeHidden == false) {
                    return 0 // No swipe for mandatory items
                }

                // Allow swiping for all other items (both to hide and to unhide)
                return super.getSwipeDirs(recyclerView, viewHolder)
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
