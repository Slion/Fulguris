package fulguris.html.history

import fulguris.App
import fulguris.R
import fulguris.constant.FILE
import fulguris.database.history.HistoryRepository
import fulguris.html.HtmlPageFactory
import fulguris.html.ListPageReader
import fulguris.utils.ThemeUtils
import fulguris.utils.htmlColor
import android.app.Application
import dagger.Reusable
import fulguris.html.jsoup.andBuild
import fulguris.html.jsoup.body
import fulguris.html.jsoup.clone
import fulguris.html.jsoup.id
import fulguris.html.jsoup.parse
import fulguris.html.jsoup.removeElement
import fulguris.html.jsoup.tag
import fulguris.html.jsoup.title
import io.reactivex.Completable
import io.reactivex.Single
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * Factory for the history page.
 */
@Reusable
class HistoryPageFactory @Inject constructor(
    private val listPageReader: ListPageReader,
    private val application: Application,
    private val historyRepository: HistoryRepository
) : HtmlPageFactory {

    private val title = application.getString(R.string.action_history)

    override fun buildPage(): Single<String> = historyRepository
        .lastHundredVisitedHistoryEntries()
        .map { list ->
            parse(listPageReader.provideHtml()
                    // Show localized page title
                    .replace("\${pageTitle}", application.getString(R.string.action_history))
                    // Theme our page first
                    .replace("\${backgroundColor}", htmlColor(ThemeUtils.getSurfaceColor(App.currentContext())))
                    .replace("\${textColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOnSurface)))
                    .replace("\${secondaryTextColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorSecondary)))
                    .replace("\${dividerColor}", htmlColor(ThemeUtils.getColor(App.currentContext(),R.attr.colorOutline)))
            ) andBuild {
                title { title }
                body {
                    val repeatedElement = id("repeated").removeElement()
                    id("content") {
                        list.forEach {
                            appendChild(repeatedElement.clone {
                                tag("a") { attr("href", it.url) }
                                id("title") { text(it.title) }
                                id("url") { text(it.url) }
                            })
                        }
                    }
                }
            }
        }
        .map { content -> Pair(createHistoryPage(), content) }
        .doOnSuccess { (page, content) ->
            FileWriter(page, false).use { it.write(content) }
        }
        .map { (page, _) -> "$FILE$page" }

    /**
     * Use this observable to immediately delete the history page. This will clear the cached
     * history page that was stored on file.
     *
     * @return a completable that deletes the history page when subscribed to.
     */
    fun deleteHistoryPage(): Completable = Completable.fromAction {
        with(createHistoryPage()) {
            if (exists()) {
                delete()
            }
        }
    }

    private fun createHistoryPage() = File(application.filesDir, FILENAME)

    companion object {
        const val FILENAME = "history.html"
    }

}
