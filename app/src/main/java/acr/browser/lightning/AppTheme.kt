package acr.browser.lightning

import acr.browser.lightning.settings.preferences.IntEnum

/**
 * The available app themes.
 */
enum class AppTheme(override val value: Int) : IntEnum {
    LIGHT(0),
    DARK(1),
    BLACK(2),
    DEFAULT(3)
}
