package fulguris.browser.bookmark

import fulguris.browser.bookmarks.BookmarkUiModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Unit tests for [BookmarkUiModel].
 */
class BookmarkUiModelTest {

    @Test
    fun `isCurrentFolderRoot returns true for null folder`() {
        val model = BookmarkUiModel().apply { currentFolder = null }
        assertThat(model.isCurrentFolderRoot()).isTrue()
    }

    @Test
    fun `isCurrentFolderRoot returns false for non null folder`() {
        val model = BookmarkUiModel().apply { currentFolder = "test" }
        assertThat(model.isCurrentFolderRoot()).isFalse()
    }
}
