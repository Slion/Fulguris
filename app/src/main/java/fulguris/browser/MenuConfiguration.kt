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

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.edit

/**
 * Represents the configuration state of a menu item
 */
data class MenuItemConfig(
    val id: MenuItemId,
    val menu: MenuType,
    val order: Int
)

/**
 * Manages persistence of menu configuration
 */
class MenuConfiguration(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        private const val PREF_MENU_CONFIG = "menu_config"
        private const val SEPARATOR_ITEM = "|"
        private const val SEPARATOR_FIELD = ":"
    }

    /**
     * Save the current menu configuration
     * Format: "ITEM_ID:MENU_TYPE:ORDER|ITEM_ID:MENU_TYPE:ORDER|..."
     */
    fun saveConfiguration(items: List<MenuItemConfig>) {
        val configString = items.joinToString(SEPARATOR_ITEM) { item ->
            "${item.id.name}${SEPARATOR_FIELD}${item.menu.name}${SEPARATOR_FIELD}${item.order}"
        }
        prefs.edit { putString(PREF_MENU_CONFIG, configString) }
    }

    /**
     * Load the saved menu configuration
     * Returns empty list if no configuration is saved
     */
    fun loadConfiguration(): List<MenuItemConfig> {
        val configString = prefs.getString(PREF_MENU_CONFIG, null) ?: return emptyList()

        if (configString.isEmpty()) return emptyList()

        return try {
            configString.split(SEPARATOR_ITEM).mapNotNull { itemStr ->
                val parts = itemStr.split(SEPARATOR_FIELD)
                if (parts.size != 3) return@mapNotNull null

                val id = MenuItemId.valueOf(parts[0])
                val menu = MenuType.valueOf(parts[1])
                val order = parts[2].toInt()

                MenuItemConfig(id, menu, order)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear the saved configuration (reset to defaults)
     */
    fun clearConfiguration() {
        prefs.edit { remove(PREF_MENU_CONFIG) }
    }

    /**
     * Check if a configuration exists
     */
    fun hasConfiguration(): Boolean {
        return prefs.contains(PREF_MENU_CONFIG)
    }
}

