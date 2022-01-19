package acr.browser.lightning

import acr.browser.lightning.database.bookmark.BookmarkExporter
import acr.browser.lightning.database.bookmark.BookmarkRepository
import acr.browser.lightning.di.DatabaseScheduler
import acr.browser.lightning.locale.LocaleUtils
import acr.browser.lightning.log.Logger
import acr.browser.lightning.settings.preferences.DeveloperPreferences
import acr.browser.lightning.settings.preferences.LandscapePreferences
import acr.browser.lightning.settings.preferences.PortraitPreferences
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.FileUtils
import acr.browser.lightning.utils.MemoryLeakUtils
import acr.browser.lightning.utils.installMultiDex
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.plugins.RxJavaPlugins
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class BrowserApp : Application() {

    @Inject internal lateinit var developerPreferences: DeveloperPreferences
    @Inject internal lateinit var userPreferences: UserPreferences
    @Inject internal lateinit var portraitPreferences: PortraitPreferences
    @Inject internal lateinit var landscapePreferences: LandscapePreferences
    @Inject internal lateinit var bookmarkModel: BookmarkRepository
    @Inject @DatabaseScheduler internal lateinit var databaseScheduler: Scheduler
    @Inject internal lateinit var logger: Logger
    //@Inject internal lateinit var buildInfo: BuildInfo

    // Used to be able to tell when our application was just started
    var justStarted: Boolean = true;

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // We only need to install that multi DEX library when not doing minify code optimization, typically in debug, and on devices below API level 21.
        // In fact from API level 21 and above Android Runtime (ART) is used rather than deprecated Dalvik.
        // Since ART has multi DEX support built-in we don't need to install that DEX library from API level 21 and above.
        // See: https://github.com/Slion/Fulguris/issues/116
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT < 21) {
            installMultiDex(context = base)
        }
    }


    override fun onCreate() {
        instance = this
        // SL: Use this to debug when launched from another app for instance
        //Debug.waitForDebugger()
        super.onCreate()

        AndroidThreeTen.init(this);

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build())
        }

        if (Build.VERSION.SDK_INT >= 28) {
            if (getProcessName() == "$packageName:incognito") {
                WebView.setDataDirectorySuffix("incognito")
            }
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            if (userPreferences.crashLogs) {
                FileUtils.writeCrashToStorage(ex)
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex)
            } else {
                exitProcess(2)
            }
        }

        RxJavaPlugins.setErrorHandler { throwable: Throwable? ->
            if (userPreferences.crashLogs && throwable != null) {
                FileUtils.writeCrashToStorage(throwable)
                throw throwable
            }
        }

        // Apply locale
        val requestLocale = LocaleUtils.requestedLocale(userPreferences.locale)
        LocaleUtils.updateLocale(this, requestLocale)

        Single.fromCallable(bookmarkModel::count)
            .filter { it == 0L }
            .flatMapCompletable {
                val assetsBookmarks = BookmarkExporter.importBookmarksFromAssets(this@BrowserApp)
                bookmarkModel.addBookmarkList(assetsBookmarks)
            }
            .subscribeOn(databaseScheduler)
            .subscribe()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }



        registerActivityLifecycleCallbacks(object : MemoryLeakUtils.LifecycleAdapter() {
            override fun onActivityDestroyed(activity: Activity) {
                logger.log(TAG, "Cleaning up after the Android framework")
                MemoryLeakUtils.clearNextServedView(activity, this@BrowserApp)
            }

            // Track current activity
            override fun onActivityResumed(activity: Activity) {
                resumedActivity = activity
            }

            // Track current activity
            override fun onActivityPaused(activity: Activity) {
                resumedActivity = null
            }
        })
    }


    companion object {
        private const val TAG = "BrowserApp"
        lateinit var instance: BrowserApp
        // Used to track current activity
        var resumedActivity: Activity? = null

        /**
         * Used to get current activity context in order to access current theme.
         */
        fun currentContext() : Context {
            val act = resumedActivity
            if (act!=null)
            {
                return act
            }
            else
            {
                return instance.applicationContext
            }
        }

        /**
         * Was needed to patch issue with Homepage displaying system language when user selected another language
         */
        fun setLocale() {
            val requestLocale = LocaleUtils.requestedLocale(instance.userPreferences.locale)
            LocaleUtils.updateLocale(instance, requestLocale)
        }

        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT)
        }
    }

}
