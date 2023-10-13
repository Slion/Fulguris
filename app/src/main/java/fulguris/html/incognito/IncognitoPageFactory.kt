/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.html.incognito

import android.app.Application
import fulguris.App
import fulguris.R
import fulguris.constant.FILE
import fulguris.constant.UTF8
import fulguris.html.HtmlPageFactory
import fulguris.search.SearchEngineProvider
import fulguris.utils.ThemeUtils
import fulguris.utils.htmlColor
import dagger.Reusable
import fulguris.html.jsoup.andBuild
import fulguris.html.jsoup.body
import fulguris.html.jsoup.charset
import fulguris.html.jsoup.parse
import fulguris.html.jsoup.tag
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * A factory for the incognito page.
 */
@Reusable
class IncognitoPageFactory @Inject constructor(
    private val application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val incognitoPageReader: IncognitoPageReader
) : HtmlPageFactory {

    override fun buildPage(): Single<String> = Single
            .just(searchEngineProvider.provideSearchEngine())
            .map { (_, queryUrl, _) ->
                parse(incognitoPageReader.provideHtml()
                        .replace("\${TITLE}", application.getString(R.string.incognito))
                        .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(App.currentContext())))
                        .replace("\${searchBarColor}", htmlColor(ThemeUtils.getSearchBarColor(ThemeUtils.getSurfaceColor(App.currentContext()))))
                        .replace("\${searchBarTextColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSurface)))
                        .replace("\${borderColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSecondary)))
                        .replace("\${accent}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorSecondary)))
                        .replace("\${search}", application.getString(R.string.search_incognito))
                ) andBuild {
                    charset { UTF8 }
                    body {
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
            .map { content -> Pair(createIncognitoPage(), content) }
            .doOnSuccess { (page, content) ->
                FileWriter(page, false).use {
                    it.write(content)
                }
            }
    .map { (page, _) -> "$FILE$page" }

    /**
     * Create the incognito page file.
     */
    fun createIncognitoPage() = File(application.filesDir, FILENAME)

    companion object {

        const val FILENAME = "private.html"

    }

}
