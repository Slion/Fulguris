package fulguris.html.homepage

import fulguris.App
import fulguris.R
import fulguris.constant.FILE
import fulguris.constant.UTF8
import fulguris.html.HtmlPageFactory
import fulguris.search.SearchEngineProvider
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.ThemeUtils
import fulguris.utils.htmlColor
import android.app.Application
import dagger.Reusable
import fulguris.html.jsoup.andBuild
import fulguris.html.jsoup.body
import fulguris.html.jsoup.charset
import fulguris.html.jsoup.id
import fulguris.html.jsoup.parse
import fulguris.html.jsoup.tag
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
    private val homePageReader: HomePageReader,
    private var userPreferences: UserPreferences
) : HtmlPageFactory {

    override fun buildPage(): Single<String> = Single
        .just(searchEngineProvider.provideSearchEngine())
        .map { (iconUrl, queryUrl, _) ->
	    App.setLocale() // Make sure locale is set as user specified
            parse(homePageReader.provideHtml()
                .replace("\${TITLE}", application.getString(R.string.home))
                .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(App.currentContext())))
                .replace("\${searchBarColor}", htmlColor(ThemeUtils.getSearchBarColor(ThemeUtils.getSurfaceColor(App.currentContext()))))
                .replace("\${searchBarTextColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSurface)))
                .replace("\${borderColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSecondary)))
                .replace("\${accent}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorSecondary)))
                .replace("\${search}", application.getString(R.string.search_homepage))
            ) andBuild {
                charset { UTF8 }
                body {
                    when (userPreferences.searchChoice) {
                        0 -> id("image_url") { attr("src", userPreferences.imageUrlString) }
                        else -> id("image_url") { attr("src", iconUrl) }
                    }
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
