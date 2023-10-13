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

package fulguris.preference

import fulguris.R
import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import timber.log.Timber
import android.content.SharedPreferences

/**
 * [EnumListPreference] makes it easier to setup a [ListPreference] from an enum class.
 * Like [ListPreference] it stores its current value as a string through [SharedPreferences].
 * Values are derived from the Enum entry names through reflection.
 * Make sure you do not change the names of the Enum entries without understanding the consequences.
 * The XML resource must provide the enumClassName attribute as a string.
 * The defaultValue attribute should be the name of one of the Enum entries.
 *
 * Apparently that's the one constructor called by the framework when inflating resources.
 * See: [PreferenceInflater.CONSTRUCTOR_SIGNATURE]
*/
class EnumListPreference (context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    /**
     * Name of the enum class defining this preference
     */
     var enumClassName: String = ""
        set(value) {
            field = value
            generateEntryValuesFromEnum()
        }

    init {
        // Get attributes from XML
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.EnumListPreference)
        attributes.let {
            enumClassName = it.getString(R.styleable.EnumListPreference_enumClassName).toString()
            it.recycle()
            Timber.d(enumClassName)
        }
    }

    /**
     *
     */
    private fun generateEntryValuesFromEnum() {
        // TODO: Check if [Class.isEnum] and provide error logs if needed
        // See: https://stackoverflow.com/a/52316667/3969362
        // Load entry values from specified enum class
        entryValues = (Class.forName(enumClassName)?.enumConstants as Array<Enum<*>>).map { it.name }.toTypedArray()
    }

}


