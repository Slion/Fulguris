package fulguris.settings.fragment

import fulguris.Capabilities
import fulguris.R
import fulguris.browser.SuggestionNumChoice
import fulguris.constant.TEXT_ENCODINGS
import fulguris.constant.Uris
import fulguris.dialog.BrowserDialog
import fulguris.extensions.launch
import fulguris.extensions.withSingleChoiceItems
import fulguris.isSupported
import fulguris.search.SearchEngineProvider
import fulguris.search.Suggestions
import fulguris.search.engine.BaseSearchEngine
import fulguris.search.engine.CustomSearch
import fulguris.utils.ThemeUtils
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.webkit.URLUtil
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.extensions.px
import fulguris.settings.preferences.USER_AGENTS_ORDERED
import fulguris.settings.preferences.USER_AGENT_CUSTOM
import fulguris.settings.preferences.UserPreferences
import fulguris.settings.preferences.userAgent
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import fulguris.extensions.find

/**
 * The general settings of the app.
 */
@AndroidEntryPoint
class GeneralSettingsFragment : AbstractSettingsFragment() {

    @Inject lateinit var searchEngineProvider: SearchEngineProvider
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var networkEngineManager: fulguris.network.NetworkEngineManager

    private lateinit var iPrefSearchCustomImageUrl: Preference
    private var defaultBrowserPreference: Preference? = null

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

        // Network engine preference
        clickableDynamicPreference(
            preference = getString(R.string.pref_key_network_engine),
            summary = getNetworkEngineDisplayName(userPreferences.networkEngine),
            onClick = ::showNetworkEngineDialog
        )

        // OkHttp cache size preference - only enabled when OkHttp engine is selected
        findPreference<x.EditTextPreference>(getString(R.string.pref_key_network_cache_size))?.apply {
            val currentEngine = networkEngineManager.getCurrentEngine()
            isEnabled = currentEngine != null && currentEngine.supportsCache()

            // Calculate available disk space and format hint
            // Use the parent directory (cacheDir) for available space, not the cache subdirectory
            val availableBytes = requireContext().cacheDir.usableSpace
            val availableMB = availableBytes / (1024L * 1024L)
            val availableGB = availableBytes / (1024L * 1024L * 1024L)

            // Format as GB if >= 1024 MB, otherwise as MB (hardcoded units - no translation needed)
            val availableFormatted = if (availableMB >= 1024) {
                "$availableGB GB"
            } else {
                "$availableMB MB"
            }
            hint = getString(R.string.hint_available_space, availableFormatted)

            // Calculate max cache size (10% of available space, in MB)
            val maxCacheSizeMB = (availableMB * 0.1).toInt().coerceAtLeast(10)

            // Set dialog message showing the max cache size (hardcoded MB - no translation needed)
            dialogMessage = getString(R.string.pref_message_network_cache_size, "$maxCacheSizeMB MB")

            // Set up validator for cache size (0 to 10% of available space)
            validator = { input ->
                val value = input?.toIntOrNull()
                when {
                    value == null -> getString(R.string.error_invalid_number)
                    value < 0 -> getString(R.string.error_cache_negative)
                    value > maxCacheSizeMB -> getString(R.string.error_cache_too_large)
                    else -> null // Valid
                }
            }

            // Notify OkHttp engine when cache size changes
            setOnPreferenceChangeListener { _, _ ->
                // Preference is already validated by the validator above
                // Now notify the OkHttp engine to recreate its client with new cache size
                (networkEngineManager.getCurrentEngine() as? fulguris.network.NetworkEngineOkHttp)?.recreateClient()
                true // Accept the change
            }
        }

        // Clear cache preference - shows current cache size and allows clearing
        findPreference<Preference>(getString(R.string.pref_key_clear_network_cache))?.apply {
            // Update summary with current cache size
            updateCacheSizeSummary()

            // Only enabled when current engine supports caching
            val currentEngine = networkEngineManager.getCurrentEngine()
            isEnabled = currentEngine != null && currentEngine.supportsCache()

            setOnPreferenceClickListener {
                clearNetworkCache()
                true
            }
        }

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

        // Default browser preference
        defaultBrowserPreference = find<Preference>(R.string.pref_key_default_browser)?.apply {

            // Looks like this is done from onResume() anyway
            //updateDefaultBrowserInfo()

            // Handle click to open default apps settings
            setOnPreferenceClickListener {
                try {
                    val settingsIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_SETTINGS)
                    }
                    startActivity(settingsIntent)
                } catch (_: Exception) {
                    // Fallback for devices that don't support this intent
                    try {
                        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                        startActivity(settingsIntent)
                    } catch (_: Exception) {
                        // Last resort - do nothing
                    }
                }
                true
            }
        }
    }

    /**
     * Update default browser preference with current system default browser info
     */
    private fun Preference.updateDefaultBrowserInfo() {
        val intent = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri())
        val pm = requireContext().packageManager
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        if (resolveInfo != null) {
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val browserName = pm.getApplicationLabel(appInfo).toString()
            val browserIcon = pm.getApplicationIcon(appInfo)
            val packageName = appInfo.packageName

            // Set summary and icon
            summary = "$browserName\n$packageName"

            // Resize icon
            val iconSize = 32.px
            val bitmap = createBitmap(iconSize, iconSize)
            val canvas = android.graphics.Canvas(bitmap)
            browserIcon.setBounds(0, 0, iconSize, iconSize)
            browserIcon.draw(canvas)
            icon = bitmap.toDrawable(resources)
        } else {
            summary = getString(R.string.unknown)
        }
    }

    /**
     * Shows the dialog which allows the user to choose the browser's text encoding.
     *
     * @param summaryUpdater the command which allows the summary to be updated.
     */
    private fun showTextEncodingDialogPicker(summaryUpdater: SummaryUpdater) : Boolean {
        activity?.let {
            MaterialAlertDialogBuilder(it).apply {
                setTitle(resources.getString(R.string.text_encoding))

                val currentChoice = TEXT_ENCODINGS.indexOf(userPreferences.textEncoding)

                setSingleChoiceItems(TEXT_ENCODINGS, currentChoice) { _, which ->
                    userPreferences.textEncoding = TEXT_ENCODINGS[which]
                    summaryUpdater.updateSummary(TEXT_ENCODINGS[which])
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }.launch()
        }

        return true
    }

    private fun showSuggestionNumPicker(summaryUpdater: SummaryUpdater) : Boolean {
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
            withSingleChoiceItems(values, userPreferences.suggestionChoice) { _, item ->
                updateSearchNum(item, summaryUpdater)
            }
            setPositiveButton(R.string.action_ok, null)
        }

        return true
    }

    private fun updateSearchNum(choice: SuggestionNumChoice, summaryUpdater: SummaryUpdater) {
        val stringArray = resources.getStringArray(R.array.suggestion_name_array)

        userPreferences.suggestionChoice = choice
        summaryUpdater.updateSummary(stringArray[choice.value])
    }

    private fun showImageUrlPicker() : Boolean {
        activity?.let {
            BrowserDialog.showEditText(it as AppCompatActivity,
                R.string.pref_title_search_custom_image_url,
                R.string.hint_url,
                userPreferences.imageUrlString,
                R.string.action_ok) { s ->
                userPreferences.imageUrlString = s
            }
        }

        return true
    }

    private fun userAgentSummary() =
        resources.getString(USER_AGENTS_ORDERED[userPreferences.userAgentChoice] ?: R.string.agent_default) +
                activity?.application?.let { ":\n" + userPreferences.userAgent(it) }

    private fun showUserAgentChooserDialog(summaryUpdater: SummaryUpdater) : Boolean {
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

        return true
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

    private fun showDownloadLocationDialog(summaryUpdater: SummaryUpdater) : Boolean {
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
                        userPreferences.downloadDirectory = fulguris.utils.FileUtils.DEFAULT_DOWNLOAD_PATH
                        summaryUpdater.updateSummary(fulguris.utils.FileUtils.DEFAULT_DOWNLOAD_PATH)
                    }
                    1 -> {
                        showCustomDownloadLocationPicker(summaryUpdater)
                    }
                }
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }
        }

        return true
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
                    text = fulguris.utils.FileUtils.addNecessarySlashes(text)
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
            if (!fulguris.utils.FileUtils.isWriteAccessAvailable(s.toString())) {
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

    private fun showHomePageDialog(summaryUpdater: SummaryUpdater) : Boolean {
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

        return true
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

    private fun showSearchProviderDialog(summaryUpdater: SummaryUpdater) : Boolean {
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

        return true
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

    private fun searchSuggestionChoiceToTitle(choice: Suggestions): String =
        when (choice) {
            Suggestions.NONE -> getString(R.string.search_suggestions_off)
            Suggestions.GOOGLE -> getString(R.string.powered_by_google)
            Suggestions.DUCK -> getString(R.string.powered_by_duck)
            Suggestions.NAVER -> getString(R.string.powered_by_naver)
            Suggestions.BAIDU -> getString(R.string.powered_by_baidu)
        }

    private fun showSearchSuggestionsDialog(summaryUpdater: SummaryUpdater) : Boolean {
        activity?.let {
            BrowserDialog.showCustomDialog(it as AppCompatActivity) {
                setTitle(resources.getString(R.string.search_suggestions))

                val currentChoice = when (Suggestions.from(userPreferences.searchSuggestionChoice)) {
                    Suggestions.GOOGLE -> 0
                    Suggestions.DUCK -> 1
                    Suggestions.BAIDU -> 2
                    Suggestions.NAVER -> 3
                    Suggestions.NONE -> 4
                }

                setSingleChoiceItems(R.array.suggestions, currentChoice) { _, which ->
                    val suggestionsProvider = when (which) {
                        0 -> Suggestions.GOOGLE
                        1 -> Suggestions.DUCK
                        2 -> Suggestions.BAIDU
                        3 -> Suggestions.NAVER
                        4 -> Suggestions.NONE
                        else -> Suggestions.GOOGLE
                    }
                    userPreferences.searchSuggestionChoice = suggestionsProvider.index
                    summaryUpdater.updateSummary(searchSuggestionChoiceToTitle(suggestionsProvider))
                }
                setPositiveButton(resources.getString(R.string.action_ok), null)
            }
        }

        return true
    }

    /**
     * Get the display name for a network engine ID.
     */
    private fun getNetworkEngineDisplayName(engineId: String): String {
        return networkEngineManager.getEngine(engineId)?.displayName
            ?: engineId
    }

    /**
     * Show dialog to select network engine implementation.
     */
    private fun showNetworkEngineDialog(summaryUpdater: SummaryUpdater): Boolean {
        activity?.let {
            val availableEngines = networkEngineManager.getAvailableEngines()
            val names = availableEngines.map { it.second }.toTypedArray()
            val ids = availableEngines.map { it.first }
            val currentIndex = ids.indexOf(userPreferences.networkEngine).coerceAtLeast(0)

            MaterialAlertDialogBuilder(it).apply {
                setTitle(R.string.pref_title_network_engine)
                setSingleChoiceItems(names, currentIndex) { dialog, which ->
                    val selectedId = ids[which]
                    userPreferences.networkEngine = selectedId
                    networkEngineManager.selectEngine(selectedId)
                    summaryUpdater.updateSummary(names[which])

                    // Enable/disable cache size preference based on selected engine's cache support
                    findPreference<androidx.preference.EditTextPreference>(getString(R.string.pref_key_network_cache_size))?.apply {
                        val selectedEngine = networkEngineManager.getCurrentEngine()
                        isEnabled = selectedEngine != null && selectedEngine.supportsCache()
                    }

                    // Enable/disable clear cache preference based on selected engine's cache support
                    findPreference<Preference>(getString(R.string.pref_key_clear_network_cache))?.apply {
                        val selectedEngine = networkEngineManager.getCurrentEngine()
                        isEnabled = selectedEngine != null && selectedEngine.supportsCache()
                        updateCacheSizeSummary()
                    }

                    dialog.dismiss()
                }
                setNegativeButton(R.string.action_cancel, null)
            }.show()
        }
        return true
    }

    /**
     * Format cache size in bytes to human-readable string (MB or GB)
     */
    private fun formatCacheSize(sizeBytes: Long): String {
        val sizeMB = sizeBytes / (1024L * 1024L)
        val sizeGB = sizeBytes / (1024L * 1024L * 1024L)

        return if (sizeMB >= 1024) {
            "$sizeGB GB"
        } else {
            "$sizeMB MB"
        }
    }

    /**
     * Update the clear cache preference summary with current cache size from the engine
     */
    private fun Preference.updateCacheSizeSummary() {
        val engine = networkEngineManager.getCurrentEngine()
        val cacheSize = if (engine != null && engine.supportsCache()) {
            formatCacheSize(engine.cacheUsedSize())
        } else {
            "0 MB"
        }
        summary = getString(R.string.pref_summary_clear_cache, cacheSize)
    }

    /**
     * Clear the cache using the engine's cache management
     */
    private fun clearNetworkCache() {
        val engine = networkEngineManager.getCurrentEngine()
        if (engine != null && engine.supportsCache()) {
            val success = engine.clearCache()

            if (success) {
                // Show success message
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.message_cache_cleared,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

            // Update the summary to show current size (should be 0 MB after clearing)
            findPreference<Preference>(getString(R.string.pref_key_clear_network_cache))?.apply {
                updateCacheSizeSummary()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update default browser preference summary when returning to settings screen
        defaultBrowserPreference?.updateDefaultBrowserInfo()

        // Update cache size summary
        findPreference<Preference>(getString(R.string.pref_key_clear_network_cache))?.apply {
            updateCacheSizeSummary()
        }
    }

    companion object {
        private const val SETTINGS_SUGGESTIONS_NUM = "suggestions_number"
        private const val SETTINGS_USER_AGENT = "agent"
        private const val SETTINGS_DOWNLOAD = "download"
        private const val SETTINGS_HOME = "home"
        private const val SETTINGS_SEARCH_ENGINE = "search"
        private const val SETTINGS_SUGGESTIONS = "suggestions_choice"
        private const val SETTINGS_FORCE_ZOOM = "force_zoom"
    }
}
