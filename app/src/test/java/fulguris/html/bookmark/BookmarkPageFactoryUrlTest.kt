package fulguris.html.bookmark

import fulguris.constant.FILE
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for [BookmarkPageFactory.folderPathForPageUrl] — the parsing that used
 * to live in LightningDialogBuilder as
 * `filename.substring(0, filename.length - FILENAME.length - 1)` and crashed
 * with StringIndexOutOfBoundsException (substring(0, -1)) on the root
 * bookmarks page, whose last path segment is just `bookmarks.html`.
 */
class BookmarkPageFactoryUrlTest {

    private val filesDir = "/data/user/0/net.slions.fulguris.full.agent.debug/files"

    @Test
    fun `root page url yields the empty folder path`() {
        // The exact shape of the URL that triggered the crash report.
        val url = "$FILE$filesDir/bookmarks.html"
        assertThat(BookmarkPageFactory.folderPathForPageUrl(url, filesDir)).isEmpty()
    }

    @Test
    fun `top level folder page url yields the folder title`() {
        val url = "$FILE$filesDir/AutoTestCrashFolder/bookmarks.html"
        assertThat(BookmarkPageFactory.folderPathForPageUrl(url, filesDir))
            .isEqualTo("AutoTestCrashFolder")
    }

    @Test
    fun `nested folder page url yields the full folder path`() {
        val url = "$FILE$filesDir/Parent/Child/bookmarks.html"
        assertThat(BookmarkPageFactory.folderPathForPageUrl(url, filesDir))
            .isEqualTo("Parent/Child")
    }

    @Test
    fun `folder name with spaces is preserved`() {
        val url = "$FILE$filesDir/My Folder/bookmarks.html"
        assertThat(BookmarkPageFactory.folderPathForPageUrl(url, filesDir))
            .isEqualTo("My Folder")
    }

    @Test
    fun `url outside the files dir yields the empty folder path`() {
        // A url that ends with bookmarks.html but is not under filesDir must
        // not be parsed as a folder (and must not throw).
        assertThat(BookmarkPageFactory.folderPathForPageUrl(
            "$FILE/sdcard/Download/bookmarks.html", filesDir)).isEmpty()
    }
}
