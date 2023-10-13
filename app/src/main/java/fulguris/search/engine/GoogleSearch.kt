/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.search.engine

import fulguris.R

/**
 * The Google search engine.
 */
class GoogleSearch : BaseSearchEngine(
    "file:///android_asset/google.webp",
    "https://www.google.com/search?client=fulguris&ie=UTF-8&oe=UTF-8&q=",
    R.string.search_engine_google
)
