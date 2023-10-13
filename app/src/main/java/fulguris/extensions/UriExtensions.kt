package fulguris.extensions

import fulguris.app
import android.net.Uri
import android.provider.OpenableColumns


/**
 * Fetches file name for this Uri
 * See: https://stackoverflow.com/a/25005243/3969362
 */
var Uri.fileName : String?
    get() {
        var result: String? = null
        if (scheme.equals("content")) {
            val cursor = app.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = path
            result?.let{
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    result = it.substring(cut + 1)
                }
            }
        }
        return result
    }

    private set(value) {}