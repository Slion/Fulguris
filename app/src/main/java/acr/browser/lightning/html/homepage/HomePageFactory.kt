package acr.browser.lightning.html.homepage

import acr.browser.lightning.BrowserApp
import acr.browser.lightning.R
import acr.browser.lightning.constant.FILE
import acr.browser.lightning.constant.UTF8
import acr.browser.lightning.html.HtmlPageFactory
import acr.browser.lightning.html.jsoup.*
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.utils.ThemeUtils
import acr.browser.lightning.utils.htmlColor
import android.app.Application
import dagger.Reusable
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * A factory for the home page.
 */
@Reusable
class HomePageFactory @Inject constructor(
        private val application: Application,
        private val searchEngineProvider: SearchEngineProvider,
        private val homePageReader: HomePageReader
) : HtmlPageFactory {

    override fun buildPage(): Single<String> = Single
            .just(searchEngineProvider.provideSearchEngine())
            .map { (iconUrl, queryUrl, _) ->
                parse(homePageReader.provideHtml()
                        .replace("\${TITLE}", application.getString(R.string.home))
                        .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(BrowserApp.currentContext())))
                        .replace("\${searchBarColor}", htmlColor(ThemeUtils.getSearchBarColor(ThemeUtils.getSurfaceColor(BrowserApp.currentContext()))))
                        .replace("\${searchBarTextColor}", htmlColor(ThemeUtils.getColor(BrowserApp.currentContext(),R.attr.colorOnPrimary)))
                        .replace("\${search}", application.getString(R.string.search_action))
                ) andBuild {
                    charset { UTF8 }
                    body {
                        id("image_url") { attr("src", iconUrl) }
                        tag("script") {
                            html(
                                    html()
                                            .replace("\${BASE_URL}", queryUrl)
                                            .replace("&", "\\u0026")
                            )
                        }
                    }
                }
            }
            .map { content -> Pair(createHomePage(), content) }
            .doOnSuccess { (page, content) ->
                FileWriter(page, false).use {
                    it.write(content)
                }
            }
            .map { (page, _) -> "$FILE$page" }

    /**
     * Create the home page file.
     */
    fun createHomePage() = File(application.filesDir, FILENAME)

    companion object {

        const val FILENAME = "homepage.html"

    }

}
