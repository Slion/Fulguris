/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.settings.fragment

import acr.browser.lightning.AccentTheme
import acr.browser.lightning.AppTheme
import acr.browser.lightning.R
import acr.browser.lightning.browser.SearchBoxDisplayChoice
import acr.browser.lightning.di.injector
import acr.browser.lightning.extensions.resizeAndShow
import acr.browser.lightning.extensions.withSingleChoiceItems
import acr.browser.lightning.settings.preferences.UserPreferences
import acr.browser.lightning.utils.Utils
import acr.browser.lightning.view.RenderingMode
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject

class DisplaySettingsFragment : AbstractSettingsFragment() {

    @Inject internal lateinit var userPreferences: UserPreferences

    /**
     * See [AbstractSettingsFragment.titleResourceId]
     */
    override fun titleResourceId(): Int {
        return R.string.settings_display
    }

    override fun providePreferencesXmlResource() = R.xml.preference_display

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        injector.inject(this)

        // Setup theme selection
        clickableDynamicPreference(
                preference = getString(R.string.pref_key_theme),
                summary = userPreferences.useTheme.toDisplayString(),
                onClick = ::showThemePicker
        )

        clickableDynamicPreference(
            preference = getString(R.string.pref_key_accent),
            summary = userPreferences.useAccent.toDisplayString(),
            onClick = ::showAccentPicker
        )

        // Setup web browser font size selector
        clickableDynamicPreference(
                preference = getString(R.string.pref_key_browser_text_size),
                summary = (userPreferences.browserTextSize + MIN_BROWSER_TEXT_SIZE).toString() + "%",
                onClick = ::showTextSizePicker
        )

        // Setup rendering mode selection
        clickableDynamicPreference(
                preference = getString(R.string.pref_key_rendering_mode),
                summary = userPreferences.renderingMode.toDisplayString(),
                onClick = this::showRenderingDialogPicker
        )

        // Setup tool bar text selection
        clickableDynamicPreference(
                preference = getString(R.string.pref_key_tool_bar_text_display),
                summary = userPreferences.urlBoxContentChoice.toDisplayString(),
                onClick = this::showUrlBoxDialogPicker
        )

    }


    /**
     * Shows the dialog which allows the user to choose the browser's URL box display options.
     *
     * @param summaryUpdater the command which allows the summary to be updated.
     */
    private fun showUrlBoxDialogPicker(summaryUpdater: SummaryUpdater) {
        activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
            setTitle(resources.getString(R.string.url_contents))

            val items = SearchBoxDisplayChoice.values().map { Pair(it, it.toDisplayString()) }

            withSingleChoiceItems(items, userPreferences.urlBoxContentChoice) {
                userPreferences.urlBoxContentChoice = it
                summaryUpdater.updateSummary(it.toDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }?.resizeAndShow()
    }


    private fun SearchBoxDisplayChoice.toDisplayString(): String {
        val stringArray = resources.getStringArray(R.array.url_content_array)
        return when (this) {
            SearchBoxDisplayChoice.DOMAIN -> stringArray[0]
            SearchBoxDisplayChoice.URL -> stringArray[1]
            SearchBoxDisplayChoice.TITLE -> stringArray[2]
            SearchBoxDisplayChoice.TRIMMED -> stringArray[3]
        }
    }


    /**
     * Shows the dialog which allows the user to choose the browser's rendering method.
     *
     * @param summaryUpdater the command which allows the summary to be updated.
     */
    private fun showRenderingDialogPicker(summaryUpdater: SummaryUpdater) {
        activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
            setTitle(resources.getString(R.string.rendering_mode))

            val values = RenderingMode.values().map { Pair(it, it.toDisplayString()) }
            withSingleChoiceItems(values, userPreferences.renderingMode) {
                userPreferences.renderingMode = it
                summaryUpdater.updateSummary(it.toDisplayString())

            }
            setPositiveButton(resources.getString(R.string.action_ok), null)
        }?.resizeAndShow()
    }

    private fun RenderingMode.toDisplayString(): String = getString(when (this) {
        RenderingMode.NORMAL -> R.string.name_normal
        RenderingMode.INVERTED -> R.string.name_inverted
        RenderingMode.GRAYSCALE -> R.string.name_grayscale
        RenderingMode.INVERTED_GRAYSCALE -> R.string.name_inverted_grayscale
        RenderingMode.INCREASE_CONTRAST -> R.string.name_increase_contrast
    })

    private fun showTextSizePicker(summaryUpdater: SummaryUpdater) {
        MaterialAlertDialogBuilder(activity as Activity).apply {
            val layoutInflater = (activity as Activity).layoutInflater
            val customView = (layoutInflater.inflate(R.layout.dialog_seek_bar, null) as LinearLayout).apply {
                val text = TextView(activity).apply {
                    text = getTextDemo(context, userPreferences.browserTextSize)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER
                    height = Utils.dpToPx(100f)
                }
                addView(text, 0)
                findViewById<SeekBar>(R.id.text_size_seekbar).apply {
                    setOnSeekBarChangeListener(TextSeekBarListener(text))
                    max = MAX_BROWSER_TEXT_SIZE - MIN_BROWSER_TEXT_SIZE
                    progress = userPreferences.browserTextSize

                }
            }
            setView(customView)
            setTitle(R.string.title_text_size)
            setPositiveButton(android.R.string.ok) { _, _ ->
                val seekBar = customView.findViewById<SeekBar>(R.id.text_size_seekbar)
                userPreferences.browserTextSize = seekBar.progress
                summaryUpdater.updateSummary((seekBar.progress + MIN_BROWSER_TEXT_SIZE).toString() + "%")
            }
        }.resizeAndShow()
    }

    private fun showThemePicker(summaryUpdater: SummaryUpdater) {
        val currentTheme = userPreferences.useTheme
        MaterialAlertDialogBuilder(activity as Activity).apply {
            setTitle(resources.getString(R.string.theme))
            val values = AppTheme.values().map { Pair(it, it.toDisplayString()) }
            withSingleChoiceItems(values, userPreferences.useTheme) {
                userPreferences.useTheme = it
                summaryUpdater.updateSummary(it.toDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok)) { _, _ ->
                if (currentTheme != userPreferences.useTheme) {
                    // Restart our activity so that new theme is applied
                    requireActivity().recreate()
                }
            }
            setOnCancelListener {
                if (currentTheme != userPreferences.useTheme) {
                    (activity as Activity).onBackPressed()
                }
            }
        }.resizeAndShow()
    }

    private fun AppTheme.toDisplayString(): String = getString(when (this) {
        AppTheme.LIGHT -> R.string.light_theme
        AppTheme.DARK -> R.string.dark_theme
        AppTheme.BLACK -> R.string.black_theme
        AppTheme.DEFAULT -> R.string.default_theme
    })

    private fun showAccentPicker(summaryUpdater: SummaryUpdater) {
        val currentAccent = userPreferences.useAccent
        MaterialAlertDialogBuilder(activity as AppCompatActivity).apply {
            setTitle(resources.getString(R.string.accent_color))
            val values = AccentTheme.values().map { Pair(it, it.toDisplayString()) }
            withSingleChoiceItems(values, userPreferences.useAccent) {
                userPreferences.useAccent = it
                summaryUpdater.updateSummary(it.toDisplayString())
            }
            setPositiveButton(resources.getString(R.string.action_ok)) { _, _ ->
                if (currentAccent != userPreferences.useAccent) {
                    requireActivity().recreate()
                }
            }
            setOnCancelListener {
                if (currentAccent != userPreferences.useAccent) {
                    (activity as AppCompatActivity).onBackPressed()
                }
            }
        }.resizeAndShow()
    }

    private fun AccentTheme.toDisplayString(): String = getString(when (this) {
        AccentTheme.DEFAULT_ACCENT -> R.string.accent_default
        AccentTheme.PINK -> R.string.accent_pink
        AccentTheme.PURPLE -> R.string.accent_purple
        AccentTheme.DEEP_PURPLE -> R.string.accent_deep_purple
        AccentTheme.INDIGO -> R.string.accent_indigo
        AccentTheme.BLUE -> R.string.accent_blue
        AccentTheme.LIGHT_BLUE -> R.string.accent_light_blue
        AccentTheme.CYAN -> R.string.accent_cyan
        AccentTheme.TEAL -> R.string.accent_teal
        AccentTheme.GREEN -> R.string.accent_green
        AccentTheme.LIGHT_GREEN -> R.string.accent_light_green
        AccentTheme.LIME -> R.string.accent_lime
        AccentTheme.YELLOW -> R.string.accent_yellow
        AccentTheme.AMBER -> R.string.accent_amber
        AccentTheme.ORANGE -> R.string.accent_orange
        AccentTheme.DEEP_ORANGE -> R.string.accent_deep_orange
        AccentTheme.BROWN -> R.string.accent_brown
    })

    private class TextSeekBarListener(
            private val sampleText: TextView
    ) : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(view: SeekBar, size: Int, user: Boolean) {
            this.sampleText.textSize = getTextSize(size)
            this.sampleText.text = getTextDemo(view.context, size)
        }

        override fun onStartTrackingTouch(arg0: SeekBar) {}

        override fun onStopTrackingTouch(arg0: SeekBar) {}

    }

    companion object {

        private const val XX_LARGE = 30.0f
        private const val X_SMALL = 10.0f

        // I guess those are percent
        const val MAX_BROWSER_TEXT_SIZE = 200
        const val DEFAULT_BROWSER_TEXT_SIZE = 100
        const val MIN_BROWSER_TEXT_SIZE = 50

        private fun getTextSize(size: Int): Float {
            var ratio : Float = (XX_LARGE - X_SMALL) / (MAX_BROWSER_TEXT_SIZE - MIN_BROWSER_TEXT_SIZE)
            return X_SMALL + size * ratio
        }

        private fun getTextDemo(context: Context, size: Int): String {
            return context.getText(R.string.untitled).toString() + ": " + (size + MIN_BROWSER_TEXT_SIZE) + "%"
        }


    }
}
