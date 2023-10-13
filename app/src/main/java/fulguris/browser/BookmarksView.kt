package fulguris.browser

import fulguris.database.Bookmark

interface BookmarksView {

    fun navigateBack()

    fun handleUpdatedUrl(url: String)

    fun handleBookmarkDeleted(bookmark: Bookmark)

}
