package fulguris.di

import fulguris.browser.cleanup.DelegatingExitCleanup
import fulguris.browser.cleanup.ExitCleanup
import fulguris.database.adblock.UserRulesDatabase
import fulguris.database.adblock.UserRulesRepository
import fulguris.database.bookmark.BookmarkDatabase
import fulguris.database.bookmark.BookmarkRepository
import fulguris.database.downloads.DownloadsDatabase
import fulguris.database.downloads.DownloadsRepository
import fulguris.database.history.HistoryDatabase
import fulguris.database.history.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dependency injection module used to bind implementations to interfaces.
 * SL: Looks like those are still actually needed.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AppBindsModule {

    @Binds
    fun bindsExitCleanup(delegatingExitCleanup: DelegatingExitCleanup): ExitCleanup

    @Binds
    fun bindsBookmarkModel(bookmarkDatabase: BookmarkDatabase): BookmarkRepository

    @Binds
    fun bindsDownloadsModel(downloadsDatabase: DownloadsDatabase): DownloadsRepository

    @Binds
    fun bindsHistoryModel(historyDatabase: HistoryDatabase): HistoryRepository

    @Binds
    fun bindsAbpRulesRepository(apbRulesDatabase: UserRulesDatabase): UserRulesRepository

}
