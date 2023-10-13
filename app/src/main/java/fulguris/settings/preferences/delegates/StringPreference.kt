package fulguris.settings.preferences.delegates

import fulguris.app
import android.content.SharedPreferences
import androidx.annotation.StringRes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


/**
 * A [String] delegate that is backed by [SharedPreferences].
 */
private class StringPreferenceDelegate(
    private val name: String,
    private val defaultValue: String,
    private val preferences: SharedPreferences
) : ReadWriteProperty<Any, String> {
    override fun getValue(thisRef: Any, property: KProperty<*>): String {
        // Catching exception here as we transition from storing enum as int to storing them as string
         return try { preferences.getString(name, defaultValue)?:"" } catch (e: Throwable) { defaultValue.toString() }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
        preferences.edit().putString(name, value).apply()
    }
}

/**
 * Creates a [String] from [SharedPreferences] with the provided arguments.
 */
fun SharedPreferences.stringPreference(
    name: String,
    defaultValue: String
): ReadWriteProperty<Any, String> = StringPreferenceDelegate(name, defaultValue, this)


/**
 * Creates a [String] from [SharedPreferences] with the provided arguments.
 */
fun SharedPreferences.stringPreference(
        @StringRes stringRes: Int,
        defaultValue: String
): ReadWriteProperty<Any, String> = StringPreferenceDelegate(app.resources.getString(stringRes), defaultValue, this)