package fulguris.search.suggestions

import fulguris.settings.preferences.UserPreferences
import fulguris.unimplemented
import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import fulguris.search.suggestions.RequestFactory
import io.reactivex.Single
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.*

/**
 * Unit tests for [BaiduSuggestionsModel].
 */
class BaiduSuggestionsModelTest {

    private val httpClient = Single.just(OkHttpClient.Builder().build())
    private val requestFactory = object :
        RequestFactory {
        override fun createSuggestionsRequest(httpUrl: HttpUrl, encoding: String) = unimplemented()
    }
    private val mockConfiguration = mock<Configuration> {
        on { locales } doReturn LocaleList(Locale.US)
    }.apply {
        locale = Locale.US
    }

    private val mockResources = mock<Resources> {
        on { configuration } doReturn mockConfiguration
    }
    private val mockUserPrefs = mock<UserPreferences> { // not working
        on { suggestionChoice.value } doReturn 2
    }
    private val application = mock<Application> {
        on { getString(any()) } doReturn "test"
        on { resources } doReturn mockResources
    }
/*
    @Test
    fun `verify query url`() {
        val suggestionsModel = BaiduSuggestionsModel(httpClient, requestFactory, application, NoOpLogger(), mockUserPrefs)

        (0..100).forEach {
            val result = "http://suggestion.baidu.com/s?wd=$it&action=opensearch"

            assertThat(suggestionsModel.createQueryUrl(it.toString(), "null")).isEqualTo(result.toHttpUrlOrNull())
        }
    }
 */
}
