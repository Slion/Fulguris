/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.search.engine

import fulguris.R

/**
 * The DuckDuckGo Lite search engine.
 */
class DuckLiteNoJSSearch : BaseSearchEngine(
    "file:///android_asset/duckduckgo.webp",
    "https://duckduckgo.com/lite/?q=",
    R.string.search_engine_duckduckgo_lite_no_js
)
