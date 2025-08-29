package fulguris.html

import com.anthonycr.mezzanine.FileStream

/**
 * The store for the list view HTML.
 */
@FileStream("src/main/html/list.html")
interface ListPageReader {

    fun provideHtml(): String

}
