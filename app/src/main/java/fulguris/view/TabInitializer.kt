package fulguris.view

import fulguris.R
import fulguris.browser.TabModel
import fulguris.constant.Uris
import fulguris.di.DiskScheduler
import fulguris.di.MainScheduler
import fulguris.extensions.resizeAndShow
import fulguris.html.HtmlPageFactory
import fulguris.html.bookmark.BookmarkPageFactory
import fulguris.html.download.DownloadPageFactory
import fulguris.html.history.HistoryPageFactory
import fulguris.html.homepage.HomePageFactory
import fulguris.html.incognito.IncognitoPageFactory
import fulguris.settings.preferences.UserPreferences
import android.app.Activity
import android.os.Bundle
import android.os.Message
import android.webkit.WebView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.Reusable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

/**
 * An initializer that is run on a [WebPageTab] after it is created.
 */
interface TabInitializer {

    /**
     * Initialize the [WebView] instance held by the [WebPageTab]. If a url is loaded, the
     * provided [headers] should be used to load the url.
     */
    fun initialize(webView: WebView, headers: Map<String, String>)

    /**
     * Tab can't be initialized without a URL.
     * That's just how browsers work: one tab, one URL.
     */
    fun url(): String
}

/**
 * An initializer that loads a [url].
 */
class UrlInitializer(private val url: String) :
    TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        webView.loadUrl(url, headers)
    }

    override fun url(): String {
        return url
    }
}

/**
 * An initializer that displays the page set as the user's homepage preference.
 */
@Reusable
class HomePageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
    private val startPageInitializer: StartPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        val homepage = userPreferences.homepage

        when (homepage) {
            Uris.AboutHome -> startPageInitializer
            Uris.AboutBookmarks -> bookmarkPageInitializer
            else -> UrlInitializer(homepage)
        }.initialize(webView, headers)
    }

    override fun url(): String {
        return Uris.FulgurisHome
    }
}

/**
 * An initializer that displays the page set as the user's incognito homepage preference.
 */
@Reusable
class IncognitoPageInitializer @Inject constructor(
    private val userPreferences: UserPreferences,
    private val startIncognitoPageInitializer: StartIncognitoPageInitializer,
    private val bookmarkPageInitializer: BookmarkPageInitializer
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        val homepage = userPreferences.homepage

        when (homepage) {
            Uris.AboutHome -> startIncognitoPageInitializer
            Uris.AboutBookmarks -> bookmarkPageInitializer
            else -> UrlInitializer(homepage)
        }.initialize(webView, headers)
    }

    override fun url(): String {
        return Uris.FulgurisIncognito
    }

}

/**
 * An initializer that displays the start page.
 */
@Reusable
class StartPageInitializer @Inject constructor(
    homePageFactory: HomePageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(homePageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.FulgurisStart
    }
}

/**
 * An initializer that displays the start incognito page.
 */
@Reusable
class StartIncognitoPageInitializer @Inject constructor(
    incognitoPageFactory: IncognitoPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(incognitoPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.FulgurisIncognito
    }
}



/**
 * An initializer that displays the bookmark page.
 */
@Reusable
class BookmarkPageInitializer @Inject constructor(
    bookmarkPageFactory: BookmarkPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(bookmarkPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.FulgurisBookmarks
    }
}

/**
 * An initializer that displays the download page.
 */
@Reusable
class DownloadPageInitializer @Inject constructor(
    downloadPageFactory: DownloadPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(downloadPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.FulgurisDownloads
    }
}

/**
 * An initializer that displays the history page.
 */
@Reusable
class HistoryPageInitializer @Inject constructor(
    historyPageFactory: HistoryPageFactory,
    @DiskScheduler diskScheduler: Scheduler,
    @MainScheduler foregroundScheduler: Scheduler
) : HtmlPageFactoryInitializer(historyPageFactory, diskScheduler, foregroundScheduler) {
    override fun url(): String {
        return Uris.FulgurisHistory
    }
}

/**
 * An initializer that loads the url built by the [HtmlPageFactory].
 */
abstract class HtmlPageFactoryInitializer(
    private val htmlPageFactory: HtmlPageFactory,
    @DiskScheduler private val diskScheduler: Scheduler,
    @MainScheduler private val foregroundScheduler: Scheduler
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        htmlPageFactory
            .buildPage()
            .subscribeOn(diskScheduler)
            .observeOn(foregroundScheduler)
            .subscribeBy(onSuccess = { webView.loadUrl(it, headers) })
    }

}

/**
 * An initializer that sets the [WebView] as the target of the [resultMessage]. Used for
 * `target="_blank"` links.
 *
 * Used when creating a new tab in response from [WebChromeClient.onCreateWindow].
 */
class ResultMessageInitializer(private val resultMessage: Message) :
    TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        resultMessage.apply {
            (obj as WebView.WebViewTransport).webView = webView
        }.sendToTarget()
    }

    override fun url(): String {
        /** We don't know our URL at this stage, it will only be loaded in the WebView by whatever is handling the message sent above.
         * That's ok though as we implemented a special case to handle this situation in [WebPageTab.initializeContent]
         */
        return ""
    }
}

/**
 * An initializer that restores the [WebView] state using the [bundle].
 */
abstract class BundleInitializer(private val bundle: Bundle?) :
    TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        bundle?.let {webView.restoreState(it)}
    }
}

/**
 * An initializer that can be delayed until the view is attached. [initialTitle] is the title that
 * should be initially set on the tab.
 */
class FreezableBundleInitializer(
    val tabModel: TabModel
) : BundleInitializer(tabModel.webView) {
    override fun url(): String {
        return tabModel.url
    }
}

/**
 * An initializer that does not load anything into the [WebView].
 */
@Reusable
class NoOpInitializer @Inject constructor() :
    TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) = Unit

    override fun url(): String {
        return Uris.FulgurisNoop
    }
}

/**
 * Ask the user's permission before loading the [url] and load the homepage instead if they deny
 * permission. Useful for scenarios where another app may attempt to open a malicious URL in the
 * browser via an intent.
 */
class PermissionInitializer(
    private val url: String,
    private val activity: Activity,
    private val homePageInitializer: HomePageInitializer
) : TabInitializer {

    override fun initialize(webView: WebView, headers: Map<String, String>) {
        MaterialAlertDialogBuilder(activity).apply {
            setTitle(R.string.title_warning)
            setMessage(R.string.message_blocked_local)
            setCancelable(false)
            setOnDismissListener {
                homePageInitializer.initialize(webView, headers)
            }
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(R.string.action_open) { _, _ ->
                UrlInitializer(url).initialize(webView, headers)
            }
        }.resizeAndShow()
    }

    override fun url(): String {
        return url
    }

}
