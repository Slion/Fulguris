package acr.browser.lightning.settings.fragment

import acr.browser.lightning.Capabilities
import acr.browser.lightning.R
import acr.browser.lightning.browser.JavaScriptChoice
import acr.browser.lightning.browser.ProxyChoice
import acr.browser.lightning.browser.SuggestionNumChoice
import acr.browser.lightning.constant.TEXT_ENCODINGS
import acr.browser.lightning.constant.Uris
import acr.browser.lightning.di.*
import acr.browser.lightning.dialog.BrowserDialog
import acr.browser.lightning.extensions.resizeAndShow
import acr.browser.lightning.extensions.withSingleChoiceItems
import acr.browser.lightning.isSupported
import acr.browser.lightning.locale.LocaleUtils
import acr.browser.lightning.search.SearchEngineProvider
import acr.browser.lightning.search.Suggestions
import acr.browser.lightning.search.engine.BaseSearchEngine
import acr.browser.lightning.search.engine.CustomSearch
import acr.browser.lightning.settings.preferences.*
import acr.browser.lightning.utils.FileUtils
import acr.browser.lightning.utils.ProxyUtils
import acr.browser.lightning.utils.ThemeUtils
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The general settings of the app.
 */
@AndroidEntryPoint
class GeneralSettingsFragment : AbstractSettingsFragment() {

    @Inject lateinit var searchEngineProvider: SearchEngineProvider
    @Inject lateinit var userPreferences: UserPreferences


    private lateinit var proxyChoices: Array<String>

    private lateinit var iPrefSearchCustomImageUrl: Preference

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_general
    }

    override fun providePreferencesXmlResource() = R.xml.preference_general

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        //injector.inject(this)

        proxyChoices = resources.getStringArray(R.array.proxy_choices_array)

        clickableDynamicPreference(
            preference = SETTINGS_PROXY,
            summary = userPreferences.proxyChoice.toSummary(),
            onClick = ::showProxyPicker
        )

        clickableDynamicPreference(
            preference = SETTINGS_USER_AGENT,
            summary = userAgentSummary(),
            onClick = ::showUserAgentChooserDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_DOWNLOAD,
            summary = userPreferences.downloadDirectory,
            onClick = ::showDownloadLocationDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_HOME,
            summary = homePageUrlToDisplayTitle(userPreferences.homepage),
            onClick = ::showHomePageDialog
        )

        switchPreference(
            preference = SETTINGS_SHOW_SSL,
            isChecked = userPreferences.ssl,
            onCheckChange = { userPreferences.ssl = it }
        )

        clickableDynamicPreference(
            preference = SETTINGS_SEARCH_ENGINE,
            summary = getSearchEngineSummary(searchEngineProvider.provideSearchEngine()),
            onClick = ::showSearchProviderDialog
        )

        clickableDynamicPreference(
            preference = SETTINGS_SUGGESTIONS,
            summary = searchSuggestionChoiceToTitle(Suggestions.from(userPreferences.searchSuggestionChoice)),
            onClick = ::showSearchSuggestionsDialog
        )

        val stringArray = resources.getStringArray(R.array.suggestion_name_array)

        clickableDynamicPreference(
            preference = SETTINGS_SUGGESTIONS_NUM,
            summary = stringArray[userPreferences.suggestionChoice.value],
            onClick = ::showSuggestionNumPicker
        )

        clickableDynamicPreference(
            preference = getString(R.string.pref_key_default_text_encoding),
            summary = userPreferences.textEncoding,
            onClick = this::showTextEncodingDialogPicker
        )

        val incognitoCheckboxPreference = switchPreference(
            preference = getString(R.string.pref_key_cookies_incognito),
            isEnabled = !Capabilities.FULL_INCOGNITO.isSupported,
            isVisible = !Capabilities.FULL_INCOGNITO.isSupported,
            isChecked = if (Capabilities.FULL_INCOGNITO.isSupported) {
                userPreferences.cookiesEnabled
            } else {
                userPreferences.incognitoCookiesEnabled
            },
            summary = if (Capabilities.FULL_INCOGNITO.isSupported) {
                getString(R.string.incognito_cookies_pie)
            } else {
                null
            },
            onCheckChange = { userPreferences.incognitoCookiesEnabled = it }
        )

        switchPreference(
            preference = getString(R.string.pref_key_cookies),
            isChecked = userPreferences.cookiesEnabled,
            onCheckChange = {
                userPreferences.cookiesEnabled = it
                if (Capabilities.FULL_INCOGNITO.isSupported) {
                    incognitoCheckboxPreference.isChecked = it
                }
            }
        )

        // Handle locale language selection
        findPreference<ListPreference>(getString(R.string.pref_key_locale))?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, aNewLocale: Any ->
                // User selected a new locale
                val newLocaleId = aNewLocale as String
                val newLocale = LocaleUtils.requestedLocale(newLocaleId)
                // Update app configuration with selected locale
                LocaleUtils.updateLocale(activity, newLocale)
                // Reload our activity
                requireActivity().recreate()
                true
            }
	    }

        clickableDynamicPreference(
            preference = SETTINGS_BLOCK_JAVASCRIPT,
            summary = userPreferences.javaScriptChoice.toSummary(),
            onClick = ::showJavaScriptPicker
        )

        switchPreference(
            preference = SETTINGS_FORCE_ZOOM,
            isChecked = userPreferences.forceZoom,
            onCheckChange = { userPreferences.forceZoom = it }
        )


        iPrefSearchCustomImageUrl = clickablePreference(
            preference = getString(R.string.pref_key_search_custom_image_url),
            onClick = ::showImageUrlPicker
        )

        // Only visible when using custom URL instead of predefined search engines
        iPrefSearchCustomImageUrl.isVisible = (userPreferences.searchChoice == 0)
    }

    /**
     * Shows the dialog which allows the user to choose the browser's text encoding.
     *
     * @param summaryUpdater the command which allows the summary to be updated.
     */
    private fun showTextEncodingDialogPicker(summaryUpdater: SummaryUpdater) {
        activity?.let {
            MaterialAlertDialogBuilder(it).apply {
                setTitle(resources.getString(R.string.text_encoding))

                val currentChoice = TEXT_ENCODINGS.indexOf(userPreferences.textEncoding)

                setSingleChoiceItems(TEXT_ENCODINGS, currentChoice) { _, which ->
                    userPreferences.textEncoding = TEXT_ENCODINGS[which]
                    summaryUpdater.updateSummary(TEXT_ENCODINGS[which])
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }.resizeAndShow()
        }
    }


    private fun ProxyChoice.toSummary(): String {
        val stringArray = resources.getStringArray(R.array.proxy_choices_array)
        return when (this) {
            ProxyChoice.NONE -> stringArray[0]
            ProxyChoice.ORBOT -> stringArray[1]
            ProxyChoice.I2P -> stringArray[2]
            ProxyChoice.MANUAL -> "${userPreferences.proxyHost}:${userPreferences.proxyPort}"
        }
    }

    private fun showProxyPicker(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity as Activity) {
            setTitle(R.string.http_proxy)
            val stringArray = resources.getStringArray(R.array.proxy_choices_array)
            val values = ProxyChoice.values().map {
                Pair(it, when (it) {
                    ProxyChoice.NONE -> stringArray[0]
                    ProxyChoice.ORBOT -> stringArray[1]
                    ProxyChoice.I2P -> stringArray[2]
                    ProxyChoice.MANUAL -> stringArray[3]
                })
            }
            withSingleChoiceItems(values, userPreferences.proxyChoice) {
                updateProxyChoice(it, activity as Activity, summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun updateProxyChoice(choice: ProxyChoice, activity: Activity, summaryUpdater: SummaryUpdater) {
        val sanitizedChoice = ProxyUtils.sanitizeProxyChoice(choice, activity)
        if (sanitizedChoice == ProxyChoice.MANUAL) {
            showManualProxyPicker(activity, summaryUpdater)
        }

        userPreferences.proxyChoice = sanitizedChoice
        summaryUpdater.updateSummary(sanitizedChoice.toSummary())
    }
    private fun showSuggestionNumPicker(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
            setTitle(R.string.suggest)
            val stringArray = resources.getStringArray(R.array.suggestion_name_array)
            val values = SuggestionNumChoice.values().map {
                Pair(it, when (it) {
                    SuggestionNumChoice.TWO -> stringArray[0]
                    SuggestionNumChoice.THREE -> stringArray[1]
                    SuggestionNumChoice.FOUR -> stringArray[2]
                    SuggestionNumChoice.FIVE -> stringArray[3]
                    SuggestionNumChoice.SIX -> stringArray[4]
                    SuggestionNumChoice.SEVEN -> stringArray[5]
                    SuggestionNumChoice.EIGHT -> stringArray[6]
                    SuggestionNumChoice.NINE -> stringArray[7]
                    SuggestionNumChoice.TEN -> stringArray[8]
                })
            }
            withSingleChoiceItems(values, userPreferences.suggestionChoice) {
                updateSearchNum(it, summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun updateSearchNum(choice: SuggestionNumChoice, summaryUpdater: SummaryUpdater) {
        val stringArray = resources.getStringArray(R.array.suggestion_name_array)

        userPreferences.suggestionChoice = choice
        summaryUpdater.updateSummary(stringArray[choice.value])
    }

    private fun showManualProxyPicker(activity: Activity, summaryUpdater: SummaryUpdater) {
        val v = activity.layoutInflater.inflate(R.layout.dialog_manual_proxy, null)
        val eProxyHost = v.findViewById<TextView>(R.id.proxyHost)
        val eProxyPort = v.findViewById<TextView>(R.id.proxyPort)

        // Limit the number of characters since the port needs to be of type int
        // Use input filters to limit the EditText length and determine the max
        // length by using length of integer MAX_VALUE
        val maxCharacters = Integer.MAX_VALUE.toString().length
        eProxyPort.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxCharacters - 1))

        eProxyHost.text = userPreferences.proxyHost
        eProxyPort.text = userPreferences.proxyPort.toString()

        BrowserDialog.showCustomDialog(activity) {
            setTitle(R.string.manual_proxy)
            setView(v)
            setPositiveButton(R.string.action_ok) { _, _ ->
                val proxyHost = eProxyHost.text.toString()
                val proxyPort = try {
                    // Try/Catch in case the user types an empty string or a number
                    // larger than max integer
                    Integer.parseInt(eProxyPort.text.toString())
                } catch (ignored: NumberFormatException) {
                    userPreferences.proxyPort
                }

                userPreferences.proxyHost = proxyHost
                userPreferences.proxyPort = proxyPort
                summaryUpdater.updateSummary("$proxyHost:$proxyPort")
            }
        }
    }

    private fun showImageUrlPicker() {
        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.pref_title_search_custom_image_url,
                R.string.hint_url,
                userPreferences.imageUrlString,
                R.string.action_ok) { s ->
                userPreferences.imageUrlString = s
            }
        }
    }

    private fun userAgentSummary() =
        resources.getString(USER_AGENTS_ORDERED[userPreferences.userAgentChoice] ?: R.string.agent_default) +
                activity?.application?.let { ":\n" + userPreferences.userAgent(it) }

    private fun showUserAgentChooserDialog(summaryUpdater: SummaryUpdater) {
        activity?.let { activity ->
            val userAgentChoices = USER_AGENTS_ORDERED.keys.toTypedArray()
            val currentAgentIndex = userAgentChoices.indexOf(userPreferences.userAgentChoice).
                let {if (it == -1) 0 else it}
            BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
                setTitle(resources.getString(R.string.title_user_agent))
                setSingleChoiceItems(
                    USER_AGENTS_ORDERED.values.map { resources.getString(it) }.toTypedArray(),
                    currentAgentIndex)
                { _, which ->
                    userPreferences.userAgentChoice = userAgentChoices[which]
                    if (userAgentChoices[which] == USER_AGENT_CUSTOM)
                        showCustomUserAgentPicker()

                    summaryUpdater.updateSummary(userAgentSummary())
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }
    }

    private fun showCustomUserAgentPicker() {
        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.title_user_agent,
                R.string.title_user_agent,
                userPreferences.userAgentString,
                R.string.action_ok) { s ->
                userPreferences.userAgentString = s
            }
        }
    }

    private fun showDownloadLocationDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it) {
            setTitle(resources.getString(R.string.title_download_location))
            val n: Int = if (userPreferences.downloadDirectory.contains(Environment.DIRECTORY_DOWNLOADS)) {
                0
            } else {
                1
            }

            setSingleChoiceItems(R.array.download_folder, n) { _, which ->
                when (which) {
                    0 -> {
                        userPreferences.downloadDirectory = FileUtils.DEFAULT_DOWNLOAD_PATH
                        summaryUpdater.updateSummary(FileUtils.DEFAULT_DOWNLOAD_PATH)
                    }
                    1 -> {
                        showCustomDownloadLocationPicker(summaryUpdater)
                    }
                }
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }
        }
    }


    private fun showCustomDownloadLocationPicker(summaryUpdater: SummaryUpdater) {
        activity?.let { activity ->
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_text, null)
            val getDownload = dialogView.findViewById<EditText>(R.id.dialog_edit_text)

            val errorColor = ContextCompat.getColor(activity
                , R.color.error_red)
            val regularColor = ThemeUtils.getTextColor(activity)
            getDownload.setTextColor(regularColor)
            getDownload.addTextChangedListener(DownloadLocationTextWatcher(getDownload, errorColor, regularColor))
            getDownload.setText(userPreferences.downloadDirectory)

            BrowserDialog.showCustomDialog(activity) {
                setTitle(R.string.title_download_location)
                setView(dialogView)
                setPositiveButton(R.string.action_ok) { _, _ ->
                    var text = getDownload.text.toString()
                    text = FileUtils.addNecessarySlashes(text)
                    userPreferences.downloadDirectory = text
                    summaryUpdater.updateSummary(text)
                }
            }
        }
    }

    private class DownloadLocationTextWatcher(
        private val getDownload: EditText,
        private val errorColor: Int,
        private val regularColor: Int
    ) : TextWatcher {

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable) {
            if (!FileUtils.isWriteAccessAvailable(s.toString())) {
                this.getDownload.setTextColor(this.errorColor)
            } else {
                this.getDownload.setTextColor(this.regularColor)
            }
        }
    }

    private fun homePageUrlToDisplayTitle(url: String): String = when (url) {
        Uris.AboutHome -> resources.getString(R.string.search_action)
        Uris.AboutBlank -> resources.getString(R.string.action_blank)
        Uris.AboutBookmarks -> resources.getString(R.string.action_bookmarks)
        else -> url
    }

    private fun showHomePageDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(R.string.home)
                val n = when (userPreferences.homepage) {
                    Uris.AboutHome -> 0
                    Uris.AboutBlank -> 1
                    Uris.AboutBookmarks -> 2
                    else -> 3
                }

                setSingleChoiceItems(R.array.homepage, n) { _, which ->
                    when (which) {
                        0 -> {
                            userPreferences.homepage = Uris.AboutHome
                            summaryUpdater.updateSummary(resources.getString(R.string.search_action))
                        }
                        1 -> {
                            userPreferences.homepage = Uris.AboutBlank
                            summaryUpdater.updateSummary(resources.getString(R.string.action_blank))
                        }
                        2 -> {
                            userPreferences.homepage = Uris.AboutBookmarks
                            summaryUpdater.updateSummary(resources.getString(R.string.action_bookmarks))
                        }
                        3 -> {
                            showCustomHomePagePicker(summaryUpdater)
                        }
                    }
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }
    }

    private fun showCustomHomePagePicker(summaryUpdater: SummaryUpdater) {
        val currentHomepage: String = if (!URLUtil.isAboutUrl(userPreferences.homepage)) {
            userPreferences.homepage
        } else {
            "https://www.google.com"
        }

        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.title_custom_homepage,
                R.string.title_custom_homepage,
                currentHomepage,
                R.string.action_ok) { url ->
                userPreferences.homepage = url
                summaryUpdater.updateSummary(url)
            }
        }
    }

    private fun getSearchEngineSummary(baseSearchEngine: BaseSearchEngine): String {
        return if (baseSearchEngine is CustomSearch) {
            baseSearchEngine.queryUrl
        } else {
            getString(baseSearchEngine.titleRes)
        }
    }

    private fun convertSearchEngineToString(searchEngines: List<BaseSearchEngine>): Array<CharSequence> =
        searchEngines.map { getString(it.titleRes) }.toTypedArray()

    private fun showSearchProviderDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(resources.getString(R.string.title_search_engine))

                val searchEngineList = searchEngineProvider.provideAllSearchEngines()

                val chars = convertSearchEngineToString(searchEngineList)

                val n = userPreferences.searchChoice

                setSingleChoiceItems(chars, n) { _, which ->
                    val searchEngine = searchEngineList[which]

                    // Store the search engine preference
                    val preferencesIndex = searchEngineProvider.mapSearchEngineToPreferenceIndex(searchEngine)
                    userPreferences.searchChoice = preferencesIndex
                    // Update that guy visibility
                    iPrefSearchCustomImageUrl.isVisible = (userPreferences.searchChoice == 0)

                    if (searchEngine is CustomSearch) {
                        // Show the URL picker
                        showCustomSearchDialog(searchEngine, summaryUpdater)
                    } else {
                        // Set the new search engine summary
                        summaryUpdater.updateSummary(getSearchEngineSummary(searchEngine))
                    }
                }
                setPositiveButton(R.string.action_ok, null)
            }
        }
    }

    private fun showCustomSearchDialog(customSearch: CustomSearch, summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showEditText(
                it as AppCompatActivity,
                R.string.search_engine_custom,
                R.string.search_engine_custom,
                userPreferences.searchUrl,
                R.string.action_ok
            ) { searchUrl ->
                userPreferences.searchUrl = searchUrl
                summaryUpdater.updateSummary(getSearchEngineSummary(customSearch))
            }
        }
    }

    private fun JavaScriptChoice.toSummary(): String {
        val stringArray = resources.getStringArray(R.array.block_javascript)
        return when (this) {
            JavaScriptChoice.NONE -> stringArray[0]
            JavaScriptChoice.WHITELIST -> userPreferences.siteBlockNames
            JavaScriptChoice.BLACKLIST -> userPreferences.siteBlockNames
        }
    }

    private fun showJavaScriptPicker(summaryUpdater: SummaryUpdater) {
        BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
            setTitle(R.string.block_javascript)
            val stringArray = resources.getStringArray(R.array.block_javascript)
            val values = JavaScriptChoice.values().map {
                Pair(it, when (it) {
                    JavaScriptChoice.NONE -> stringArray[0]
                    JavaScriptChoice.WHITELIST -> stringArray[1]
                    JavaScriptChoice.BLACKLIST -> stringArray[2]
                })
            }
            withSingleChoiceItems(values, userPreferences.javaScriptChoice) {
                updateJavaScriptChoice(it, activity as Activity, summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }
    }

    private fun updateJavaScriptChoice(choice: JavaScriptChoice, activity: Activity, summaryUpdater: SummaryUpdater) {
        if (choice == JavaScriptChoice.WHITELIST || choice == JavaScriptChoice.BLACKLIST) {
            showManualJavaScriptPicker(activity, summaryUpdater, choice)
        }

        userPreferences.javaScriptChoice = choice
        summaryUpdater.updateSummary(choice.toSummary())
    }

    @SuppressLint("InflateParams")
    private fun showManualJavaScriptPicker(activity: Activity, summaryUpdater: SummaryUpdater, choice: JavaScriptChoice) {
        val v = activity.layoutInflater.inflate(R.layout.site_block, null)
        val blockedSites = v.findViewById<TextView>(R.id.siteBlock)
        // Limit the number of characters since the port needs to be of type int
        // Use input filters to limit the EditText length and determine the max
        // length by using length of integer MAX_VALUE

        blockedSites.text = userPreferences.javaScriptBlocked

        BrowserDialog.showCustomDialog(activity as AppCompatActivity) {
            setTitle(R.string.block_sites_title)
            setView(v)
            setPositiveButton(R.string.action_ok) { _, _ ->
                val js = blockedSites.text.toString()
                userPreferences.javaScriptBlocked = js
                if(choice.toString() == "BLACKLIST"){
                    summaryUpdater.updateSummary(getText(R.string.listed_javascript).toString())
                }
                else{
                    summaryUpdater.updateSummary(getText(R.string.unlisted_javascript).toString())
                }

            }
        }
    }

    private fun searchSuggestionChoiceToTitle(choice: Suggestions): String =
        when (choice) {
            Suggestions.NONE -> getString(R.string.search_suggestions_off)
            Suggestions.GOOGLE -> getString(R.string.powered_by_google)
            Suggestions.DUCK -> getString(R.string.powered_by_duck)
            Suggestions.NAVER -> getString(R.string.powered_by_naver)
            Suggestions.BAIDU -> getString(R.string.powered_by_baidu)
        }

    private fun showSearchSuggestionsDialog(summaryUpdater: SummaryUpdater) {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(resources.getString(R.string.search_suggestions))

                val currentChoice = when (Suggestions.from(userPreferences.searchSuggestionChoice)) {
                    Suggestions.GOOGLE -> 0
                    Suggestions.DUCK -> 1
                    Suggestions.NAVER -> 2
                    Suggestions.BAIDU -> 3
                    Suggestions.NONE -> 4
                }

                setSingleChoiceItems(R.array.suggestions, currentChoice) { _, which ->
                    val suggestionsProvider = when (which) {
                        0 -> Suggestions.GOOGLE
                        1 -> Suggestions.DUCK
                        2 -> Suggestions.NAVER
                        3 -> Suggestions.BAIDU
                        4 -> Suggestions.NONE
                        else -> Suggestions.GOOGLE
                    }
                    userPreferences.searchSuggestionChoice = suggestionsProvider.index
                    summaryUpdater.updateSummary(searchSuggestionChoiceToTitle(suggestionsProvider))
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }
    }

    companion object {
        private const val SETTINGS_PROXY = "proxy"
        private const val SETTINGS_SUGGESTIONS_NUM = "suggestions_number"
        private const val SETTINGS_USER_AGENT = "agent"
        private const val SETTINGS_DOWNLOAD = "download"
        private const val SETTINGS_HOME = "home"
        private const val SETTINGS_SEARCH_ENGINE = "search"
        private const val SETTINGS_SUGGESTIONS = "suggestions_choice"
        private const val SETTINGS_BLOCK_JAVASCRIPT = "block_javascript"
        private const val SETTINGS_FORCE_ZOOM = "force_zoom"
        private const val SETTINGS_SHOW_SSL = "show_ssl"
    }
}
