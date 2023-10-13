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
 * All portions of the code written by Stéphane Lenclud are Copyright © 2023 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.settings.preferences.delegates

import fulguris.app
import android.content.SharedPreferences
import androidx.annotation.StringRes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An [Enum] delegate that is backed by [SharedPreferences].
 *
 * This was persisted using an integer by AR.
 * Then changed by SL to persist the enum name as string instead.
 * Thus making it compatible with [ListPreference] and now [EnumListPreference].
 */
class EnumPreference<T>(
    name: String,
    private val defaultValue: T,
    private val clazz: Class<T>,
    preferences: SharedPreferences
) : ReadWriteProperty<Any, T> where T : Enum<T> {

    //private var backingInt: Int by preferences.intPreference(name, defaultValue.value)
    private var backingValue: String by preferences.stringPreference(name, defaultValue.toString())

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return clazz.enumConstants!!.firstOrNull { it.toString() == backingValue } ?: defaultValue
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        backingValue = value.toString()
    }
}

/**
 * Creates a [T] enum from [SharedPreferences] with the provide arguments.
 */
inline fun <reified T> SharedPreferences.enumPreference(
    name: String,
    defaultValue: T
): ReadWriteProperty<Any, T> where T : Enum<T> = EnumPreference(
    name,
    defaultValue,
    T::class.java,
    this
)

/**
 * Creates a [T] enum from [SharedPreferences] with the provide arguments.
 */
inline fun <reified T> SharedPreferences.enumPreference(
    @StringRes name: Int,
    defaultValue: T
): ReadWriteProperty<Any, T> where T : Enum<T> = EnumPreference(
    app.resources.getString(name),
    defaultValue,
    T::class.java,
    this
)
