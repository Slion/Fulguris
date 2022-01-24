package acr.browser.lightning.settings.preferences.delegates

import acr.browser.lightning.BrowserApp
import android.content.SharedPreferences
import androidx.annotation.BoolRes
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An [Int] delegate that is backed by [SharedPreferences].
 */
private class IntPreferenceDelegate(
    private val name: String,
    private val defaultValue: Int,
    private val preferences: SharedPreferences
) : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Int =
        preferences.getInt(name, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
        preferences.edit().putInt(name, value).apply()
    }

}

/**
 * Creates a [Int] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.intPreference(
    name: String,
    defaultValue: Int = 0
): ReadWriteProperty<Any, Int> = IntPreferenceDelegate(name, defaultValue, this)


/**
 * Creates a [Int] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.intPreference(
        @StringRes stringRes: Int,
        defaultValue: Int = 0
): ReadWriteProperty<Any, Int> = IntPreferenceDelegate(BrowserApp.instance.resources.getString(stringRes), defaultValue, this)

/**
 * Creates a [Int] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.intResPreference(
    @StringRes stringRes: Int,
    @IntegerRes intRes: Int
): ReadWriteProperty<Any, Int> = IntPreferenceDelegate(BrowserApp.instance.resources.getString(stringRes), BrowserApp.instance.resources.getInteger(intRes), this)

