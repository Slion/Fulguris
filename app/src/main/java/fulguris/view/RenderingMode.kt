package fulguris.view

import fulguris.settings.preferences.IntEnum

/**
 * An enum representing the browser's available rendering modes.
 */
enum class RenderingMode(override val value: Int) :
    IntEnum {
    NORMAL(0),
    INVERTED(1),
    GRAYSCALE(2),
    INVERTED_GRAYSCALE(3),
    INCREASE_CONTRAST(4)
}
