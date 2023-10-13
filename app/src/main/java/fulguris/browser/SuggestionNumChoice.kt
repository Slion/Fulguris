/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.browser

import fulguris.settings.preferences.IntEnum

/**
 * The available Suggestion number choices.
 */
enum class SuggestionNumChoice(override val value: Int) :
    IntEnum {
    TWO(0),
    THREE(1),
    FOUR(2),
    FIVE(3),
    SIX(4),
    SEVEN(5),
    EIGHT(6),
    NINE(7),
    TEN(8)
}
