/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.search.engine

import fulguris.R

/**
 * The Searx search engine.
 */
class SearxSearch : BaseSearchEngine(
    "file:///android_asset/searx.webp",
    "https://searx.prvcy.eu/search?q=",
    R.string.search_engine_searx
)
