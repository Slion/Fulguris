/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.search.engine

import fulguris.R

/**
 * The Bing search engine.
 */
class BingSearch : BaseSearchEngine(
    "file:///android_asset/bing.webp",
    "https://www.bing.com/search?q=",
    R.string.search_engine_bing
)
