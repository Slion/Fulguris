package fulguris.settings.preferences.delegates

import fulguris.app
import android.content.SharedPreferences
import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A [Boolean] delegate that is backed by [SharedPreferences].
 */
private class BooleanPreferenceDelegate(
    private val name: String,
    private val defaultValue: Boolean,
    private val preferences: SharedPreferences
) : ReadWriteProperty<Any, Boolean> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Boolean =
        preferences.getBoolean(name, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
        preferences.edit().putBoolean(name, value).apply()
    }

}

/**
 * Creates a [Boolean] from [SharedPreferences] with the provided arguments.
 */
fun SharedPreferences.booleanPreference(
    name: String,
    defaultValue: Boolean = false
): ReadWriteProperty<Any, Boolean> = BooleanPreferenceDelegate(name, defaultValue, this)

/**
 * Creates a [Boolean] from [SharedPreferences] with the provided arguments.
 * NOTE: Using Resources.getSystems from here is not working so we need our app instance to access resources.
 */
fun SharedPreferences.booleanPreference(
        @StringRes stringRes: Int,
        defaultValue: Boolean = false
): ReadWriteProperty<Any, Boolean> = BooleanPreferenceDelegate(app.resources.getString(stringRes), defaultValue, this)

/**
 * Creates a [Boolean] from [SharedPreferences] with the provided arguments.
 */
fun SharedPreferences.booleanPreference(
        @StringRes stringRes: Int,
        @BoolRes boolRes: Int
): ReadWriteProperty<Any, Boolean> = BooleanPreferenceDelegate(app.resources.getString(stringRes), app.resources.getBoolean(boolRes), this)
