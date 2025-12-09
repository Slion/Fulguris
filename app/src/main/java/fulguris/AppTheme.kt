package fulguris

import fulguris.settings.preferences.IntEnum

/**
 * The available app themes.
 */
enum class AppTheme(override val value: Int) :
    IntEnum {
    DEFAULT(0),
    LIGHT(1),
    WHITE(2),
    DARK(3),
    BLACK(3),
}
