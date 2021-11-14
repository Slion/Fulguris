/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package acr.browser.lightning.search.engine

import acr.browser.lightning.R

/**
 * The Ekoru search engine.
 */
class EkoruSearch : BaseSearchEngine(
    "file:///android_asset/ekoru.webp",
    "https://www.ekoru.org/?ext=styx&q=",
    R.string.search_engine_ekoru
)
