/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris

import fulguris.settings.preferences.IntEnum

/**
 * The available accent themes.
 */
enum class AccentTheme(override val value: Int) :
    IntEnum {
    DEFAULT_ACCENT(0),
    PINK(1),
    PURPLE(2),
    DEEP_PURPLE(3),
    INDIGO(4),
    BLUE(5),
    LIGHT_BLUE(6),
    CYAN(7),
    TEAL(8),
    GREEN(9),
    LIGHT_GREEN(10),
    LIME(11),
    YELLOW(12),
    AMBER(13),
    ORANGE(14),
    DEEP_ORANGE(15),
    BROWN(16)
}
