package acr.browser.lightning.history

import acr.browser.lightning.database.Bookmark
import acr.browser.lightning.database.History
import java.io.InputStream

/**
 * An importer that imports [History.Entry] from an [InputStream]. Supported formats are details of
 * the implementation.
 */
interface HistoryImporter {

    /**
     * Synchronously converts an [InputStream] to a [List] of [History.Entry].
     */
    fun importHistory(inputStream: InputStream): List<History.Entry>

}
