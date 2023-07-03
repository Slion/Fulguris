package fulguris.preference

import acr.browser.lightning.R
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


