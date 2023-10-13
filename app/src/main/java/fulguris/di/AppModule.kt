package fulguris.di

import fulguris.BuildConfig
import fulguris.device.BuildInfo
import fulguris.device.BuildType
import fulguris.extensions.landscapeSharedPreferencesName
import fulguris.extensions.portraitSharedPreferencesName
import fulguris.html.ListPageReader
import fulguris.html.bookmark.BookmarkPageReader
import fulguris.html.homepage.HomePageReader
import fulguris.js.InvertPage
import fulguris.js.TextReflow
import fulguris.js.ThemeColor
import fulguris.js.SetMetaViewport
import fulguris.search.suggestions.RequestFactory
import fulguris.utils.FileUtils
import android.app.Application
import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.anthonycr.mezzanine.MezzanineGenerator
import fulguris.html.incognito.IncognitoPageReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import net.i2p.android.ui.I2PAndroidHelper
import okhttp3.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    fun provideBuildInfo(): BuildInfo = BuildInfo(when {
        BuildConfig.DEBUG -> BuildType.DEBUG
        else -> BuildType.RELEASE
    })

    @Provides
    @MainHandler
    fun provideMainHandler() = Handler(Looper.getMainLooper())

    @Provides
    fun provideContext(application: Application): Context = application.applicationContext


    @Provides
    @UserPrefs
    @Singleton
    // Access default shared preferences to make sure preferences framework binding is working from XML
    fun provideUserSharedPreferences(application: Application): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)

    @Provides
    @PrefsPortrait
    @Singleton
    fun providePreferencesPortrait(application: Application): SharedPreferences = application.getSharedPreferences(application.portraitSharedPreferencesName(), 0)

    @Provides
    @PrefsLandscape
    @Singleton
    fun providePreferencesLandscape(application: Application): SharedPreferences = application.getSharedPreferences(application.landscapeSharedPreferencesName(), 0)

    @Provides
    @DevPrefs
    fun provideDebugPreferences(application: Application): SharedPreferences = application.getSharedPreferences("developer_settings", 0)

    @Provides
    @AdBlockPrefs
    fun provideAdBlockPreferences(application: Application): SharedPreferences = application.getSharedPreferences("ad_block_settings", 0)

    @Provides
    fun providesAssetManager(application: Application): AssetManager = application.assets

    @Provides
    fun providesClipboardManager(application: Application) = application.getSystemService<ClipboardManager>()!!

    @Provides
    fun providesInputMethodManager(application: Application) = application.getSystemService<InputMethodManager>()!!

    @Provides
    fun providesDownloadManager(application: Application) = application.getSystemService<DownloadManager>()!!

    @Provides
    fun providesConnectivityManager(application: Application) = application.getSystemService<ConnectivityManager>()!!

    @Provides
    fun providesNotificationManager(application: Application) = application.getSystemService<NotificationManager>()!!

    @Provides
    fun providesWindowManager(application: Application) = application.getSystemService<WindowManager>()!!

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    @Provides
    fun providesShortcutManager(application: Application) = application.getSystemService<ShortcutManager>()!!

    @Provides
    @DatabaseScheduler
    @Singleton
    fun providesIoThread(): Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Provides
    @DiskScheduler
    @Singleton
    fun providesDiskThread(): Scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

    @Provides
    @NetworkScheduler
    @Singleton
    fun providesNetworkThread(): Scheduler = Schedulers.from(ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, LinkedBlockingDeque()))

    @Provides
    @MainScheduler
    @Singleton
    fun providesMainThread(): Scheduler = AndroidSchedulers.mainThread()

    @Singleton
    @Provides
    fun providesSuggestionsCacheControl(): CacheControl = CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build()

    @Singleton
    @Provides
    fun providesSuggestionsRequestFactory(cacheControl: CacheControl): RequestFactory = object :
        RequestFactory {
        override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String): Request {
            return Request.Builder().url(httpUrl)
                .addHeader("Accept-Charset", encoding)
                .cacheControl(cacheControl)
                .build()
        }
    }

    private fun createInterceptorWithMaxCacheAge(maxCacheAgeSeconds: Long) = Interceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header("cache-control", "max-age=$maxCacheAgeSeconds, max-stale=$maxCacheAgeSeconds")
            .build()
    }

    @Singleton
    @Provides
    @SuggestionsClient
    fun providesSuggestionsHttpClient(application: Application): Single<OkHttpClient> = Single.fromCallable {
        val intervalDay = TimeUnit.DAYS.toSeconds(1)
        val suggestionsCache = File(application.cacheDir, "suggestion_responses")

        return@fromCallable OkHttpClient.Builder()
            .cache(Cache(suggestionsCache, fulguris.utils.FileUtils.megabytesToBytes(1)))
            .addNetworkInterceptor(createInterceptorWithMaxCacheAge(intervalDay))
            .build()
    }.cache()

    @Provides
    @Singleton
    fun provideI2PAndroidHelper(application: Application): I2PAndroidHelper = I2PAndroidHelper(application)

    @Provides
    fun providesListPageReader(): ListPageReader = MezzanineGenerator.ListPageReader()

    @Provides
    fun providesHomePageReader(): HomePageReader = MezzanineGenerator.HomePageReader()

    @Provides
    fun providesIncognitoPageReader(): IncognitoPageReader = MezzanineGenerator.IncognitoPageReader()

    @Provides
    fun providesBookmarkPageReader(): BookmarkPageReader = MezzanineGenerator.BookmarkPageReader()

    @Provides
    fun providesTextReflow(): TextReflow = MezzanineGenerator.TextReflow()

    @Provides
    fun providesThemeColor(): ThemeColor = MezzanineGenerator.ThemeColor()

    @Provides
    fun providesInvertPage(): InvertPage = MezzanineGenerator.InvertPage()

    @Provides
    fun providesSetMetaViewport(): SetMetaViewport = MezzanineGenerator.SetMetaViewport()
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SuggestionsClient

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainHandler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UserPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PrefsPortrait

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PrefsLandscape

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AdBlockPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DevPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DiskScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NetworkScheduler

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DatabaseScheduler
