package acr.browser.lightning.di

import acr.browser.lightning.adblock.AbpBlockerManager
import acr.browser.lightning.adblock.AbpUserRules
import acr.browser.lightning.adblock.NoOpAdBlocker
import acr.browser.lightning.browser.BrowserPresenter
import acr.browser.lightning.browser.TabsManager
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.database.downloads.DownloadsRepository
import acr.browser.lightning.database.history.HistoryRepository
import acr.browser.lightning.dialog.LightningDialogBuilder
import acr.browser.lightning.download.DownloadHandler
import acr.browser.lightning.favicon.FaviconModel
import acr.browser.lightning.html.homepage.HomePageFactory
import acr.browser.lightning.js.InvertPage
import acr.browser.lightning.js.SetMetaViewport
import acr.browser.lightning.js.TextReflow
import acr.browser.lightning.log.Logger
import acr.browser.lightning.network.NetworkConnectivityModel
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.ssl.SslWarningPreferences
import acr.browser.lightning.utils.ProxyUtils
import acr.browser.lightning.view.webrtc.WebRtcPermissionsModel
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
    @UserPrefs fun userSharedPreferences(): SharedPreferences
    val historyRepository: HistoryRepository
    @DatabaseScheduler fun databaseScheduler(): Scheduler
    @NetworkScheduler fun networkScheduler(): Scheduler
    @DiskScheduler fun diskScheduler(): Scheduler
    @MainScheduler fun mainScheduler(): Scheduler
    val searchEngineProvider: SearchEngineProvider
    val proxyUtils: ProxyUtils
    val sslWarningPreferences: SslWarningPreferences
    val logger: Logger
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
    var presenter: BrowserPresenter
    var clipboardManager: ClipboardManager

}


