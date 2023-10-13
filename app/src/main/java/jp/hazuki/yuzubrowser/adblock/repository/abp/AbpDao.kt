/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.adblock.repository.abp

import fulguris.adblock.AbpListUpdater
import android.content.Context

class AbpDao(val context: Context) {
    val prefs = context.getSharedPreferences("ad_block_settings", Context.MODE_PRIVATE)

    fun getAll(): List<AbpEntity> {
        val set = prefs.getStringSet(ABP_ENTITIES, ABP_DEFAULT_ENTITIES)
        val list = mutableListOf<AbpEntity>()
        set!!.forEach { list.add(abpEntityFromString(it) ?: return@forEach) }
        // return sorted list to have consistent order shown in settings
        list.sortBy { it.entityId }
        return list
    }

    // update also handles new entities, they should have index 0 to avoid duplicates (can't happen in a db...)
    fun update(abpEntity: AbpEntity): Int {
        val list = getAll() as MutableList

        // check whether entity exists
        for (index in list.indices) {
            if (list[index].equals(abpEntity)) { // compares id only
                list[index] = abpEntity
                prefs.edit().putStringSet(ABP_ENTITIES, list.map { it.toString() }.toSet()).apply()
                return abpEntity.entityId
            }
        }

        // if entity has index 0, find a valid unique ne id
        if (abpEntity.entityId == 0) {
            val ids =  list.map { it.entityId }
            var i = 1
            while (ids.contains(i)) {
                ++i
            }
            abpEntity.entityId = i
        }
        list.add(abpEntity)
        prefs.edit().putStringSet(ABP_ENTITIES, list.map { it.toString() }.toSet()).apply()
        return abpEntity.entityId
    }

    fun delete(abpEntity: AbpEntity) {
        val list = getAll() as MutableList
        list.removeAll { it.entityId == abpEntity.entityId }
        AbpListUpdater(context).removeFiles(abpEntity)

        if (list.isEmpty())
            prefs.edit().remove(ABP_ENTITIES).apply()
        else
            prefs.edit().putStringSet(ABP_ENTITIES, list.map { it.toString() }.toSet()).apply()
    }

}

// add some default block lists
const val ABP_ENTITIES = "abpEntities"
val ABP_ENTITY_EASYLIST = AbpEntity(title = "EasyList", entityId = 2, url = "https://easylist.to/easylist/easylist.txt", homePage = "https://easylist.to")
val ABP_ENTITY_EASYPRIVACY = AbpEntity(title = "EasyPrivacy", entityId = 3, url = "https://easylist.to/easylist/easyprivacy.txt", homePage = "https://easylist.to")
val ABP_ENTITY_URLHAUS = AbpEntity(title = "Urlhaus Malicious URL Blocklist", entityId = 4, url = "https://raw.githubusercontent.com/curbengh/urlhaus-filter/master/urlhaus-filter-agh-online.txt", homePage = "https://gitlab.com/curben/urlhaus-filter")
val ABP_ENTITY_STEVEN_BLACK = AbpEntity(title = "StevenBlack hosts list", entityId = 5, url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", homePage = "https://github.com/StevenBlack/hosts", enabled = false)

val ABP_DEFAULT_ENTITIES = setOf(
    ABP_ENTITY_EASYLIST.toString(),
    ABP_ENTITY_EASYPRIVACY.toString(),
    ABP_ENTITY_URLHAUS.toString(),
    ABP_ENTITY_STEVEN_BLACK.toString(),
)
