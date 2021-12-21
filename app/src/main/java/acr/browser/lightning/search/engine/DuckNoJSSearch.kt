/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package acr.browser.lightning.search.engine

import acr.browser.lightning.R

/**
 * The DuckDuckGo search engine.
 */
class DuckNoJSSearch : BaseSearchEngine(
    "file:///android_asset/duckduckgo.webp",
    "https://duckduckgo.com/html/?q=",
    R.string.search_engine_duckduckgo_no_js
)
