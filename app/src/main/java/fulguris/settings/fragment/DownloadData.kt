package fulguris.settings.fragment

import android.app.DownloadManager
import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

internal data class DownloadData(
    val id: Long,
    val title: String?,
    val status: Int,
    val localUri: String?,
    val uri: String?,
    val bytesDownloaded: Long,
    val totalSize: Long,
    val lastModified: Long,
    val mimeType: String?
) {
    companion object {
        fun fromCursor(cursor: Cursor): DownloadData? {
            val id = cursor.requiredLong(DownloadManager.COLUMN_ID)?.takeIf { it >= 0L }
                ?: return null
            val status = cursor.requiredInt(DownloadManager.COLUMN_STATUS) ?: return null

            return DownloadData(
                id = id,
                title = cursor.optionalString(DownloadManager.COLUMN_TITLE),
                status = status,
                localUri = cursor.optionalString(DownloadManager.COLUMN_LOCAL_URI),
                uri = cursor.optionalString(DownloadManager.COLUMN_URI),
                bytesDownloaded = cursor.optionalLong(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    0L
                ),
                totalSize = cursor.optionalLong(DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1L),
                lastModified = cursor.optionalLong(
                    DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP,
                    0L
                ),
                mimeType = cursor.optionalString(DownloadManager.COLUMN_MEDIA_TYPE)
            )
        }

        private fun Cursor.requiredLong(columnName: String): Long? {
            val index = getColumnIndex(columnName)
            return if (index >= 0) getLongOrNull(index) else null
        }

        private fun Cursor.requiredInt(columnName: String): Int? {
            val index = getColumnIndex(columnName)
            return if (index >= 0) getIntOrNull(index) else null
        }

        private fun Cursor.optionalLong(columnName: String, defaultValue: Long): Long {
            val index = getColumnIndex(columnName)
            return if (index >= 0) getLongOrNull(index) ?: defaultValue else defaultValue
        }

        private fun Cursor.optionalString(columnName: String): String? {
            val index = getColumnIndex(columnName)
            return if (index >= 0) getStringOrNull(index) else null
        }
    }
}
