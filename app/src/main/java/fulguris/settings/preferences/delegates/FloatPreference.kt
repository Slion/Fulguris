package fulguris.settings.preferences.delegates

import fulguris.app
import android.content.SharedPreferences
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * An [Float] delegate that is backed by [SharedPreferences].
 */
private class FloatPreferenceDelegate(
    private val name: String,
    private val defaultValue: Float,
    private val preferences: SharedPreferences
) : ReadWriteProperty<Any, Float> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Float =
        preferences.getFloat(name, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) {
        preferences.edit().putFloat(name, value).apply()
    }

}

/**
 * Creates a [Float] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.floatPreference(
    name: String,
    defaultValue: Float = 0F
): ReadWriteProperty<Any, Float> = FloatPreferenceDelegate(name, defaultValue, this)


/**
 * Creates a [Float] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.floatPreference(
        @StringRes stringRes: Int,
        defaultValue: Float = 0F
): ReadWriteProperty<Any, Float> = FloatPreferenceDelegate(app.resources.getString(stringRes), defaultValue, this)

/**
 * Creates a [Float] from [SharedPreferences] with the provide arguments.
 */
fun SharedPreferences.floatResPreference(
    @StringRes stringRes: Int,
    @IntegerRes intRes: Int
): ReadWriteProperty<Any, Float> = FloatPreferenceDelegate(app.resources.getString(stringRes), app.resources.getInteger(intRes).toFloat(), this)

