/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package acr.browser.lightning.search.engine

import acr.browser.lightning.R

/**
 * The Brave search engine.
 */
class BraveSearch : BaseSearchEngine(
    "file:///android_asset/brave.webp",
    "https://search.brave.com/search?q=",
    R.string.search_engine_brave
)
