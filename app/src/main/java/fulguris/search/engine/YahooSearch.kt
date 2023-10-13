/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.search.engine

import fulguris.R

/**
 * The Yahoo search engine.
 */
class YahooSearch : BaseSearchEngine(
    "file:///android_asset/yahoo.webp",
    "https://search.yahoo.com/search?p=",
    R.string.search_engine_yahoo
)
