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

class AbpEntity(
        var entityId: Int = 0,

        var url: String = "",

        // shown in adblock settings
        var title: String? = null,

        // same as last modified? read from header of the blocklist, but does not exist in easylist
        var lastUpdate: String? = null,

        // when the local files were last updated
        var lastLocalUpdate: Long = 0,

        // how long the list is valid
        // check for updates if time since last local update longer than expires
        var expires: Int = -1,

        // actually not used, except in a not so useful way for the internal list -> maybe remove
        var version: String? = null,

        // in the header of the remote file, not actually used for anything -> maybe remove
        // also, there seems to be some decoding problem. ends up as "https" for all entities
        var homePage: String? = null,

        // last modified from header of the list (i.e. when file on server was last updated)
        var lastModified: String? = null,

        var enabled: Boolean = true
) {

    constructor() : this(0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbpEntity) return false
        if (entityId != other.entityId) return false

        return true
    }

    override fun hashCode(): Int {
        return entityId
    }

    // store as string (in shared preferences)
    override fun toString(): String {
        return "$entityId§§$url§§$title§§$lastUpdate§§$lastLocalUpdate§§$expires§§$version§§$homePage§§$lastModified§§$enabled"
    }

}

// and read from string
fun abpEntityFromString(string: String): AbpEntity? {
    val list = string.split("§§")
    if (list.size != 10)
        return null
    if (list[0].toIntOrNull() == null || list[4].toLongOrNull() == null || list[5].toIntOrNull() == null || (list[9] != "true" && list[9] != "false"))
        return null
    return AbpEntity(
        entityId = list[0].toInt(),
        url = list[1],
        title = if (list[2] == "null") null else list[2],
        lastUpdate = if (list[3] == "null") null else list[3],
        lastLocalUpdate = list[4].toLong(),
        expires = list[5].toInt(),
        version = if (list[6] == "null") null else list[6],
        homePage = if (list[7] == "null") null else list[7],
        lastModified = if (list[8] == "null") null else list[8],
        enabled = list[9] == "true",
    )
}
