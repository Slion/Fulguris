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

import android.content.Context

/*
import androidx.room.*

@Dao
interface AbpDao {

    @Query("SELECT * from abp")
    suspend fun getAll(): List<AbpEntity>

    @Insert
    suspend fun inset(abpEntity: AbpEntity): Long

    @Insert
    suspend fun inset(entities: List<AbpEntity>)

    @Delete
    suspend fun delete(abpEntity: AbpEntity)

    @Update
    suspend fun update(abpEntity: AbpEntity)
}
*/
/*
interface AbpDao {

    suspend fun getAll(): List<AbpEntity>

    suspend fun inset(abpEntity: AbpEntity): Long

    suspend fun inset(entities: List<AbpEntity>)

    suspend fun delete(abpEntity: AbpEntity)

    suspend fun update(abpEntity: AbpEntity)
}*/

// (bad?) replacement for the db:
class AbpDao(val context: Context) {
    //val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val prefs = context.getSharedPreferences("ad_block_settings", Context.MODE_PRIVATE)

    fun getAll(): List<AbpEntity> {
        val set = prefs.getStringSet(ABP_ENTITIES, ABP_DEFAULT_ENTITIES)
        val list = mutableListOf<AbpEntity>()
        set!!.forEach { list.add(abpEntityFromString(it) ?: return@forEach) }
        return list
    }

    // update also handles new entities, they should have index 0 to avoid duplicates (can't happen in a db...)
    fun update(abpEntity: AbpEntity) {
        val list = getAll() as MutableList
        for (index in list.indices) {
            if (list[index].equals(abpEntity)) { // compares id only
                list[index] = abpEntity
                prefs.edit().putStringSet(ABP_ENTITIES, list.map { it.toString() }.toSet()).apply()
                return
            }
        }
        // if entity has index 0, find a new one
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
    }

    fun delete(abpEntity: AbpEntity) {
        val list = getAll() as MutableList
        var i = 1
        while (i < list.size) {
            if (list[i].equals(abpEntity))
                list.removeAt(i)
            ++i
        }
        prefs.edit().putStringSet(ABP_ENTITIES, list.map { it.toString() }.toSet()).apply()
    }


}

// pre-fill some stuff, currently only built-in easylist enabled (enable others by manipulating preferences xml)
const val ABP_ENTITIES = "abpEntities"
val ABP_ENTITY_EASYLIST_BUILTIN = AbpEntity(title = "EasyListLocal", entityId = 1, url = "fulguris://easylist", homePage = "https://easylist.to")
val ABP_ENTITY_EASYLIST = AbpEntity(title = "EasyList", entityId = 2, url = "https://easylist.to/easylist/easylist.txt", homePage = "https://easylist.to", enabled = false)
val ABP_ENTITY_EASYPRIVACY = AbpEntity(title = "EasyPrivacy", entityId = 3, url = "https://easylist.to/easylist/easyprivacy.txt", homePage = "https://easylist.to", enabled = false)
val ABP_DEFAULT_ENTITIES = setOf(ABP_ENTITY_EASYLIST_BUILTIN.toString(), ABP_ENTITY_EASYLIST.toString(), ABP_ENTITY_EASYPRIVACY.toString())
