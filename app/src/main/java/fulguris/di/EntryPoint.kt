package fulguris.di

import fulguris.adblock.AbpBlockerManager
import fulguris.adblock.AbpUserRules
import fulguris.adblock.NoOpAdBlocker
import fulguris.browser.TabsManager
import fulguris.database.bookmark.BookmarkRepository
import fulguris.database.downloads.DownloadsRepository
import fulguris.database.history.HistoryRepository
import fulguris.dialog.LightningDialogBuilder
import fulguris.download.DownloadHandler
import fulguris.favicon.FaviconModel
import fulguris.html.homepage.HomePageFactory
import fulguris.js.InvertPage
import fulguris.js.SetMetaViewport
import fulguris.js.TextReflow
import fulguris.network.NetworkConnectivityModel
import fulguris.search.SearchEngineProvider
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.ProxyUtils
import fulguris.view.webrtc.WebRtcPermissionsModel
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.Scheduler

/**
 * Provide access to all our injectable classes.
 * Virtual fields can't resolve qualifiers for some reason.
 * Therefore we use functions where qualifiers are needed.
 *
 * Just add your class here if you need it.
 *
 * TODO: See if and how we can use the 'by' syntax to initialize usage of those.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltEntryPoint {

    val bookmarkRepository: BookmarkRepository
    val userPreferences: UserPreferences
    @UserPrefs
    fun userSharedPreferences(): SharedPreferences
    val historyRepository: HistoryRepository
    @DatabaseScheduler
    fun databaseScheduler(): Scheduler
    @NetworkScheduler
    fun networkScheduler(): Scheduler
    @DiskScheduler
    fun diskScheduler(): Scheduler
    @MainScheduler
    fun mainScheduler(): Scheduler
    val searchEngineProvider: SearchEngineProvider
    val proxyUtils: ProxyUtils
    val textReflowJs: TextReflow
    val invertPageJs: InvertPage
    val setMetaViewport: SetMetaViewport
    val homePageFactory: HomePageFactory
    val abpBlockerManager: AbpBlockerManager
    val noopBlocker: NoOpAdBlocker
    val dialogBuilder: LightningDialogBuilder
    val networkConnectivityModel: NetworkConnectivityModel
    val faviconModel: FaviconModel
    val webRtcPermissionsModel: WebRtcPermissionsModel
    val abpUserRules: AbpUserRules
    val downloadHandler: DownloadHandler
    val downloadManager: DownloadManager
    val downloadsRepository: DownloadsRepository
    var tabsManager: TabsManager
    var clipboardManager: ClipboardManager

}


