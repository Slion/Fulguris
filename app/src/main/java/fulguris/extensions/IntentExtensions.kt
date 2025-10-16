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

package fulguris.extensions

import android.content.Intent
import timber.log.Timber

/**
 * Log detailed information about this Intent for debugging purposes.
 *
 * @param tag Optional tag to prefix the logs with, useful for identifying the context
 */
fun Intent.log(tag: String = "Intent") {
    Timber.d("Intent from $tag")
    Timber.d("- Action: $action")
    Timber.d("- Data: $dataString")
    Timber.d("- Type: $type")
    Timber.d("- Categories: $categories")
    Timber.d("- Component: $component")
    Timber.d("- Package: $`package`")
    Timber.d("- Flags: ${flags.toString(16)}")

    // Log all extras
    extras?.let { bundle ->
        val size = bundle.size()
        Timber.d("- Extras ($size items):")

        if (size == 0) {
            Timber.d("  Bundle is empty")
        } else {
            try {
                val keySet = bundle.keySet()
                if (keySet != null && keySet.isNotEmpty()) {
                    keySet.forEach { key ->
                        try {
                            val value = bundle.get(key)
                            val valueStr = when (value) {
                                is String -> "\"$value\""
                                is Array<*> -> value.contentToString()
                                is IntArray -> value.contentToString()
                                is LongArray -> value.contentToString()
                                is BooleanArray -> value.contentToString()
                                is ByteArray -> "ByteArray[${value.size}]"
                                else -> value.toString()
                            }
                            Timber.d("  [$key] = $valueStr (${value?.javaClass?.simpleName})")
                        } catch (e: Exception) {
                            Timber.e(e, "  Error getting value for key: $key")
                        }
                    }
                } else {
                    Timber.d("  KeySet is null or empty")
                }
            } catch (e: Exception) {
                Timber.e(e, "- Error iterating extras")
            }
        }
    } ?: Timber.d("- No extras bundle")
}

