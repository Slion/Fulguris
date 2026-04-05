package fulguris.js

import com.anthonycr.mezzanine.FileStream

/**
 * Reads a blob: URL via fetch(), converts it to a base64 data URL, and
 * posts the result back through the _fulgurisBlobDownload JS interface.
 */
@FileStream("src/main/js/BlobDownload.js")
interface BlobDownload {
    fun provideJs(): String
}
