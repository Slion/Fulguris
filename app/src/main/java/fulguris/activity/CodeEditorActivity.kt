/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.activity

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.databinding.ActivityCodeEditorBinding
import fulguris.userscript.UserScriptManager
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import timber.log.Timber
import javax.inject.Inject

/**
 * Activity for viewing userscript source code using Sora Editor.
 * Displays the JavaScript code in a read-only code editor with line numbers and syntax highlighting.
 */
@AndroidEntryPoint
class CodeEditorActivity : ThemedActivity() {

    @Inject lateinit var userScriptManager: UserScriptManager

    private lateinit var binding: ActivityCodeEditorBinding
    private lateinit var editor: CodeEditor
    private var scriptId: String? = null
    private var scriptName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Get script ID from intent
        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID)
        if (scriptId == null) {
            Timber.e("No script ID provided")
            finish()
            return
        }

        // Load the script
        val script = userScriptManager.getScript(scriptId!!)
        if (script == null) {
            Timber.e("Script not found: $scriptId")
            finish()
            return
        }

        scriptName = script.name
        title = scriptName

        // Initialize editor
        editor = binding.editor
        editor.isEditable = false // Read-only

        // Configure editor appearance
        editor.setLineNumberEnabled(true)
        editor.isWordwrap = false

        // Set up syntax highlighting with TextMate
        setupTextMate()

        // Set the text
        editor.setText(script.code)
    }

    private fun setupTextMate() {
        try {
            // Ensure TextMate registries are initialized
            ensureTextMateInitialized()

            // Apply theme BEFORE setting language to ensure colors are ready
            applyTextMateTheme()

            // Load JavaScript language
            val language: Language = TextMateLanguage.create(
                "source.js",
                true // Enable tab completion
            )
            editor.setEditorLanguage(language)

            Timber.d("TextMate syntax highlighting successfully configured")

        } catch (e: Exception) {
            Timber.e(e, "Failed to set up TextMate syntax highlighting, falling back to basic colors")
            // Fallback to basic color scheme
            applyBasicColorScheme()
        }
    }

    private fun ensureTextMateInitialized() {
        try {
            val fileProvider = FileProviderRegistry.getInstance()
            val grammarRegistry = GrammarRegistry.getInstance()
            val themeRegistry = ThemeRegistry.getInstance()

            // Register assets file provider
            try {
                fileProvider.addFileProvider(AssetsFileResolver(assets))
                Timber.d("Assets file provider registered")
            } catch (e: Exception) {
                // Already registered
                Timber.d("Assets file provider already registered")
            }

            // Load JavaScript grammar
            try {
                grammarRegistry.loadGrammars("textmate/language-configuration.json")
                Timber.d("Grammar loaded successfully")
            } catch (e: Exception) {
                Timber.w(e, "Grammar may already be loaded")
            }

            // Load theme based on dark/light mode
            val isDark = isDarkMode()
            val themeName = if (isDark) "darcula" else "quietlight"
            val themePath = "textmate/$themeName.json"

            Timber.d("Loading theme: $themeName (isDark=$isDark)")

            try {
                val themeSource = IThemeSource.fromInputStream(
                    assets.open(themePath),
                    themePath,
                    null
                )
                themeRegistry.loadTheme(ThemeModel(themeSource, themeName))
                themeRegistry.setTheme(themeName)
                Timber.d("Theme loaded and set successfully: $themeName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load theme: $themePath")
                throw e
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TextMate")
            throw e
        }
    }

    private fun applyTextMateTheme() {
        try {
            val themeRegistry = ThemeRegistry.getInstance()
            val colorScheme = TextMateColorScheme.create(themeRegistry)

            Timber.d("Applying TextMate color scheme")
            editor.colorScheme = colorScheme
            Timber.d("TextMate color scheme applied successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply TextMate theme")
            throw e
        }
    }

    private fun applyBasicColorScheme() {
        try {
            val colorScheme = if (isDarkMode()) {
                // Dark theme colors
                EditorColorScheme().apply {
                    setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF2B2B2B.toInt())
                    setColor(EditorColorScheme.TEXT_NORMAL, 0xFFA9B7C6.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF313335.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER, 0xFF606366.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_PANEL, 0xFF313335.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, 0xFF606366.toInt())
                    setColor(EditorColorScheme.CURRENT_LINE, 0xFF323232.toInt())
                    setColor(EditorColorScheme.SELECTION_INSERT, 0xFF214283.toInt())
                    setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF4A88C7.toInt())
                    setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, 0xFF214283.toInt())

                    // Set additional token colors to ensure visibility
                    setColor(EditorColorScheme.IDENTIFIER_NAME, 0xFFA9B7C6.toInt())
                    setColor(EditorColorScheme.IDENTIFIER_VAR, 0xFFA9B7C6.toInt())
                    setColor(EditorColorScheme.LITERAL, 0xFF6A8759.toInt())
                    setColor(EditorColorScheme.OPERATOR, 0xFFA9B7C6.toInt())
                    setColor(EditorColorScheme.COMMENT, 0xFF808080.toInt())
                    setColor(EditorColorScheme.KEYWORD, 0xFFCC7832.toInt())
                }
            } else {
                // Light theme colors
                EditorColorScheme().apply {
                    setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFFF5F5F5.toInt())
                    setColor(EditorColorScheme.TEXT_NORMAL, 0xFF333333.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFFEEEEEE.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER, 0xFF999999.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_PANEL, 0xFFEEEEEE.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, 0xFF999999.toInt())
                    setColor(EditorColorScheme.CURRENT_LINE, 0xFFE8F2FF.toInt())
                    setColor(EditorColorScheme.SELECTION_INSERT, 0xFF3399FF.toInt())
                    setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF3399FF.toInt())
                    setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, 0xFFC0D8F0.toInt())

                    // Set additional token colors
                    setColor(EditorColorScheme.IDENTIFIER_NAME, 0xFF333333.toInt())
                    setColor(EditorColorScheme.IDENTIFIER_VAR, 0xFF333333.toInt())
                    setColor(EditorColorScheme.LITERAL, 0xFF448C27.toInt())
                    setColor(EditorColorScheme.OPERATOR, 0xFF333333.toInt())
                    setColor(EditorColorScheme.COMMENT, 0xFFAAAAAA.toInt())
                    setColor(EditorColorScheme.KEYWORD, 0xFF794938.toInt())
                }
            }
            editor.colorScheme = colorScheme
        } catch (e: Exception) {
            Timber.e(e, "Failed to apply basic color scheme")
        }
    }

    private fun isDarkMode(): Boolean {
        // Check app theme preference first
        return when (userPreferences.useTheme) {
            fulguris.AppTheme.DARK, fulguris.AppTheme.BLACK -> true
            fulguris.AppTheme.LIGHT, fulguris.AppTheme.WHITE -> false
            fulguris.AppTheme.DEFAULT -> {
                // For default theme, check system setting
                val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.code_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_copy -> {
                copyToClipboard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText(scriptName, editor.text.toString())
        clipboard.setPrimaryClip(clip)

        // Show toast
        android.widget.Toast.makeText(this, R.string.message_code_copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_SCRIPT_ID = "script_id"

        /**
         * Create an intent to open the code editor for a specific script.
         */
        fun createIntent(context: Context, scriptId: String): Intent {
            return Intent(context, CodeEditorActivity::class.java).apply {
                putExtra(EXTRA_SCRIPT_ID, scriptId)
            }
        }
    }
}

