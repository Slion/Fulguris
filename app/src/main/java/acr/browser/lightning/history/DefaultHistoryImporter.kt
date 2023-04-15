package acr.browser.lightning.history

import acr.browser.lightning.database.History
import acr.browser.lightning.database.history.HistoryExporter
import java.io.InputStream
import javax.inject.Inject

/**
 * A [HistoryImporter] that imports history files that were produced by [HistoryExporter].
 */
class DefaultHistoryImporter @Inject constructor() : HistoryImporter {

    override fun importHistory(inputStream: InputStream): List<History.Entry> {
        return HistoryExporter.importHistoryFromFileStream(inputStream)
    }

}
