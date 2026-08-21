package fulguris.settings.fragment

import android.app.DownloadManager
import android.database.MatrixCursor
import fulguris.TestApplication
import fulguris.extensions.useMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [29])
class DownloadDataTest {

    @Test
    fun `running download decodes null transient metadata and unknown size`() {
        val cursor = downloadCursor(
            id = 42L,
            status = DownloadManager.STATUS_RUNNING,
            localUri = null,
            mimeType = null,
            bytesDownloaded = 0L,
            totalSize = -1L
        )

        val data = cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            DownloadData.fromCursor(it)
        }

        assertThat(data).isEqualTo(
            DownloadData(
                id = 42L,
                title = "active.bin",
                status = DownloadManager.STATUS_RUNNING,
                localUri = null,
                uri = "https://example.com/active.bin",
                bytesDownloaded = 0L,
                totalSize = -1L,
                lastModified = 1234L,
                mimeType = null
            )
        )
    }

    @Test
    fun `active download states decode zero and partial byte counts`() {
        val rows = listOf(
            Triple(DownloadManager.STATUS_RUNNING, 0L, -1L),
            Triple(DownloadManager.STATUS_PENDING, 0L, 0L),
            Triple(DownloadManager.STATUS_PAUSED, 25L, 100L)
        )

        rows.forEachIndexed { index, (status, downloaded, total) ->
            val cursor = downloadCursor(
                id = index + 1L,
                status = status,
                bytesDownloaded = downloaded,
                totalSize = total
            )

            val data = cursor.use {
                assertThat(it.moveToFirst()).isTrue()
                DownloadData.fromCursor(it)
            }

            assertThat(data).isNotNull
            assertThat(data!!.status).isEqualTo(status)
            assertThat(data.bytesDownloaded).isEqualTo(downloaded)
            assertThat(data.totalSize).isEqualTo(total)
        }
    }

    @Test
    fun `completed download preserves full metadata`() {
        val cursor = downloadCursor(
            id = 7L,
            title = "finished.pdf",
            status = DownloadManager.STATUS_SUCCESSFUL,
            localUri = "file:///storage/emulated/0/Download/finished.pdf",
            uri = "https://example.com/finished.pdf",
            bytesDownloaded = 4096L,
            totalSize = 4096L,
            lastModified = 987654321L,
            mimeType = "application/pdf"
        )

        val data = cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            DownloadData.fromCursor(it)
        }

        assertThat(data).isEqualTo(
            DownloadData(
                id = 7L,
                title = "finished.pdf",
                status = DownloadManager.STATUS_SUCCESSFUL,
                localUri = "file:///storage/emulated/0/Download/finished.pdf",
                uri = "https://example.com/finished.pdf",
                bytesDownloaded = 4096L,
                totalSize = 4096L,
                lastModified = 987654321L,
                mimeType = "application/pdf"
            )
        )
    }

    @Test
    fun `missing optional columns use safe defaults`() {
        val cursor = MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_ID,
                DownloadManager.COLUMN_STATUS
            )
        ).apply {
            addRow(arrayOf(11L, DownloadManager.STATUS_PENDING))
        }

        val data = cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            DownloadData.fromCursor(it)
        }

        assertThat(data).isEqualTo(
            DownloadData(
                id = 11L,
                title = null,
                status = DownloadManager.STATUS_PENDING,
                localUri = null,
                uri = null,
                bytesDownloaded = 0L,
                totalSize = -1L,
                lastModified = 0L,
                mimeType = null
            )
        )
    }

    @Test
    fun `row missing download id is skipped without aborting subsequent rows`() {
        val cursor = MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_ID,
                DownloadManager.COLUMN_STATUS
            )
        ).apply {
            addRow(arrayOf(null, DownloadManager.STATUS_RUNNING))
            addRow(arrayOf(99L, DownloadManager.STATUS_SUCCESSFUL))
        }

        val decodedRows = cursor.useMap(DownloadData::fromCursor).filterNotNull()

        assertThat(decodedRows.map(DownloadData::id)).containsExactly(99L)
    }

    @Test
    fun `row missing status is rejected`() {
        val cursor = MatrixCursor(arrayOf(DownloadManager.COLUMN_ID)).apply {
            addRow(arrayOf(5L))
        }

        val data = cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            DownloadData.fromCursor(it)
        }

        assertThat(data).isNull()
    }

    private fun downloadCursor(
        id: Long,
        title: String? = "active.bin",
        status: Int,
        localUri: String? = null,
        uri: String? = "https://example.com/active.bin",
        bytesDownloaded: Long,
        totalSize: Long,
        lastModified: Long = 1234L,
        mimeType: String? = null
    ) = MatrixCursor(
        arrayOf(
            DownloadManager.COLUMN_ID,
            DownloadManager.COLUMN_TITLE,
            DownloadManager.COLUMN_STATUS,
            DownloadManager.COLUMN_LOCAL_URI,
            DownloadManager.COLUMN_URI,
            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
            DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
            DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP,
            DownloadManager.COLUMN_MEDIA_TYPE
        )
    ).apply {
        addRow(
            arrayOf(
                id,
                title,
                status,
                localUri,
                uri,
                bytesDownloaded,
                totalSize,
                lastModified,
                mimeType
            )
        )
    }
}
