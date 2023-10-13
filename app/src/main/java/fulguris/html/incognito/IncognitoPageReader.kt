/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.html.incognito

import com.anthonycr.mezzanine.FileStream

/**
 * The store for the incognito HTML.
 */
@FileStream("app/src/main/html/private.html")
interface IncognitoPageReader {

    fun provideHtml(): String

}