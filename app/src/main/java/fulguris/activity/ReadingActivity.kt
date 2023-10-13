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


import fulguris.AppTheme
import fulguris.R
import fulguris.di.MainScheduler
import fulguris.di.NetworkScheduler
import fulguris.dialog.BrowserDialog.setDialogSize
import fulguris.extensions.isDarkTheme
import fulguris.utils.Utils
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Scheduler
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import java.io.*
import java.net.URL
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ReadingActivity : ThemedActivity(), TextToSpeech.OnInitListener {
    @JvmField
    var mTitle: TextView? = null

    @JvmField
    var mBody: TextView? = null


    @JvmField
    @Inject
    @NetworkScheduler
    var mNetworkScheduler: Scheduler? = null

    @JvmField
    @Inject
    @MainScheduler
    var mMainScheduler: Scheduler? = null

    private lateinit var iTtsEngine: TextToSpeech
    private var mInvert = false
    private var mUrl: String? = null
    private var file: Boolean = false
    private var mTextSize = 0
    private var mProgressDialog: AlertDialog? = null

    /**
     * Override our theme as needed according to current theme and invert mode.
     */
    override fun provideThemeOverride(): AppTheme {
        var applyDarkTheme = isDarkTheme()
        applyDarkTheme = (applyDarkTheme && !userPreferences.invertColors) || (!applyDarkTheme && userPreferences.invertColors)
        return if (applyDarkTheme) {
            AppTheme.BLACK
        } else {
            AppTheme.LIGHT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        overridePendingTransition(R.anim.slide_in_from_right, R.anim.fade_out_scale)
        mInvert = userPreferences.invertColors
        iTtsEngine = TextToSpeech(this, this)

        setContentView(R.layout.reading_view)
        mTitle = findViewById(R.id.textViewTitle)
        mBody = findViewById(R.id.textViewBody)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        mTextSize = userPreferences!!.readingTextSize
        mBody!!.textSize = getTextSize(mTextSize)
        mTitle!!.text = getString(R.string.untitled)
        mBody!!.text = getString(R.string.loading)
        mTitle!!.visibility = View.INVISIBLE
        mBody!!.visibility = View.INVISIBLE
        val intent = intent
        try {
            if (!loadPage(intent)) {
                //setText(getString(R.string.untitled), getString(R.string.loading_failed));
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reading, menu)

        if (menu is MenuBuilder) {
            val m: MenuBuilder = menu
            m.setOptionalIconsVisible(true)
        }

        return super.onCreateOptionsMenu(menu)
    }

    private inner class loadData : AsyncTask<Void?, Void?, Void?>() {
        var extractedContentHtml: String? = null
        var extractedContentHtmlWithUtf8Encoding: String? = null
        var extractedContentPlainText: String? = null
        var title: String? = null
        var byline: String? = null
        var excerpt: String? = null


        override fun onPostExecute(aVoid: Void?) {
            val html: String? = extractedContentHtmlWithUtf8Encoding?.replace("image copyright".toRegex(), resources.getString(R.string.reading_mode_image_copyright) + " ")?.replace("image caption".toRegex(), resources.getString(R.string.reading_mode_image_caption) + " ")?.replace("￼".toRegex(), "")
            try {
                val doc = Jsoup.parse(html)
                for (element in doc.select("img")) {
                    element.remove()
                }
                setText(title, doc.outerHtml())
                dismissProgressDialog()
            }
            catch (e: Exception){
                mTitle!!.alpha = 1.0f
                mTitle!!.visibility = View.VISIBLE
                mTitle?.text = resources.getString(R.string.title_error)
                dismissProgressDialog()
            }
        }

        override fun doInBackground(vararg params: Void?): Void? {
            try {
                val google = URL(mUrl)
                val line = BufferedReader(InputStreamReader(google.openStream()))
                var input: String?
                val stringBuffer = StringBuffer()
                while (line.readLine().also { input = it } != null) {
                    stringBuffer.append(input)
                }
                line.close()
                val htmlData = stringBuffer.toString()
                val readability4J = Readability4J(mUrl!!, htmlData) // url is just needed to resolve relative urls
                val article = readability4J.parse()
                extractedContentHtml = article.content
                extractedContentHtmlWithUtf8Encoding = article.contentWithUtf8Encoding
                extractedContentPlainText = article.textContent
                title = article.title
                byline = article.byline
                excerpt = article.excerpt
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }

    protected fun makeLinkClickable(strBuilder: SpannableStringBuilder, span: URLSpan?) {
        val start: Int = strBuilder.getSpanStart(span)
        val end: Int = strBuilder.getSpanEnd(span)
        val flags: Int = strBuilder.getSpanFlags(span)
        val clickable: ClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                mTitle!!.text = getString(R.string.untitled)
                mBody!!.text = getString(R.string.loading)
                mUrl = span?.url
                loadData().execute()
                // TODO: somehow TTS is broken after following a link
                // Restarting the activity does not help for some reason
                // We ought to check TTS error codes and debug that at some point
                //launch(this@ReadingActivity, mUrl!!, file)
                //finish()

            }
        }
        strBuilder.setSpan(clickable, start, end, flags)
        strBuilder.removeSpan(span)
    }

    protected fun setTextViewHTML(text: TextView, html: String?) {
        val sequence: CharSequence = Html.fromHtml(html)
        val strBuilder = SpannableStringBuilder(sequence)
        val urls: Array<URLSpan> = strBuilder.getSpans(0, sequence.length, URLSpan::class.java)
        for (span in urls) {
            makeLinkClickable(strBuilder, span)
        }
        text.setText(strBuilder)
        text.movementMethod = LinkMovementMethod.getInstance()
    }

    @Throws(IOException::class)
    private fun loadPage(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        mUrl = intent.getStringExtra(LOAD_READING_URL)
        file = intent.getBooleanExtra(LOAD_FILE, false)
        if (mUrl == null) {
            return false
        }
        else if (file){
            setText(mUrl, loadFile(this, mUrl))
            return false
        }
        if (supportActionBar != null) {
            supportActionBar!!.title = fulguris.utils.Utils.getDisplayDomainName(mUrl)
        }

        // Build progress dialog
        val progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val builder = MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
        mProgressDialog = builder.create()
        val tv = progressView.findViewById<TextView>(R.id.text_progress_bar)
        tv.setText(R.string.loading)
        mProgressDialog!!.show()


        loadData().execute()
        return true
    }

    private fun dismissProgressDialog() {
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
            mProgressDialog = null
        }
    }

    private fun setText(title: String?, body: String?) {
        if (mTitle == null || mBody == null) return
        if (mTitle!!.visibility == View.INVISIBLE) {
            mTitle!!.alpha = 1.0f
            mTitle!!.visibility = View.VISIBLE
            setTextViewHTML(mTitle!!, title)
            //mTitle!!.text = title
        } else {
            mTitle!!.text = title
            setTextViewHTML(mTitle!!, title)
        }
        if (mBody!!.visibility == View.INVISIBLE) {
            mBody!!.alpha = 1.0f
            mBody!!.visibility = View.VISIBLE
            setTextViewHTML(mBody!!, body)
        } else {
            setTextViewHTML(mBody!!, body)
        }
    }

    override fun onDestroy() {
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
            mProgressDialog = null
        }
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            overridePendingTransition(R.anim.fade_in_scale, R.anim.slide_out_to_right)
        }
    }

    override fun onStop() {
        super.onStop()
        // Otherwise TTS goes on if we go background which is not always what we want
        // TODO: Could have a setting to support that?
        iTtsEngine.stop()
    }

    /**
     *
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.invert_item -> {
                userPreferences!!.invertColors = !mInvert
                if (mUrl != null) {
                    launch(this, mUrl!!, file)
                    finish()
                }
            }
            R.id.text_size_item -> {
                val view = LayoutInflater.from(this).inflate(R.layout.dialog_seek_bar, null)
                val bar = view.findViewById<SeekBar>(R.id.text_size_seekbar)
                bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(view: SeekBar, size: Int, user: Boolean) {
                        mBody!!.textSize = getTextSize(size)
                    }

                    override fun onStartTrackingTouch(arg0: SeekBar) {}
                    override fun onStopTrackingTouch(arg0: SeekBar) {}
                })
                bar.max = 5
                bar.progress = mTextSize
                val builder = MaterialAlertDialogBuilder(this)
                        .setView(view)
                        .setTitle(R.string.size)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, arg1: Int ->
                            mTextSize = bar.progress
                            mBody!!.textSize = getTextSize(mTextSize)
                            userPreferences!!.readingTextSize = bar.progress
                        }
                val dialog: Dialog = builder.show()
                setDialogSize(this, dialog)
            }
            R.id.tts -> {
                // Toggle TTS
                if (!iTtsEngine.isSpeaking) {
                    val text: String = mBody?.text.toString()
                    //TODO: check error codes
                    iTtsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
                else {
                    iTtsEngine.stop()
                }
                invalidateOptionsMenu()
            }
            else -> finish()
        }
        return super.onOptionsItemSelected(item)
    }



    private fun loadFile(context: Context, name: String?): String? {
        return try {
            val fis: FileInputStream = context.openFileInput(name + ".txt")

            fis.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var result: Int = iTtsEngine.setLanguage(Locale.getDefault())
            //iTtsEngine.stop()

            // Try falling back to US english then
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = iTtsEngine.setLanguage(Locale.US)
            }

            // Check if that was working
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported")
            }

            iTtsEngine.setOnUtteranceCompletedListener(TextToSpeech.OnUtteranceCompletedListener {
                runOnUiThread {
                    invalidateOptionsMenu()
                }
            })

        } else {
            Log.e("TTS", "Initilization Failed")
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.tts)
        if (iTtsEngine.isSpeaking) {
            item.title = resources.getString(R.string.stop_tts)
        } else {
            item.title = resources.getString(R.string.tts)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    companion object {
        private const val LOAD_READING_URL = "ReadingUrl"
        private const val LOAD_FILE = "FileUrl"

        /**
         * Launches this activity with the necessary URL argument.
         *
         * @param context The context needed to launch the activity.
         * @param url     The URL that will be loaded into reading mode.
         */
        fun launch(context: Context, url: String, file: Boolean) {
            val intent = Intent(context, ReadingActivity::class.java)
            intent.putExtra(LOAD_READING_URL, url)
            intent.putExtra(LOAD_FILE, file)
            context.startActivity(intent)
        }

        private const val XXLARGE = 30.0f
        private const val XLARGE = 26.0f
        private const val LARGE = 22.0f
        private const val MEDIUM = 18.0f
        private const val SMALL = 14.0f
        private const val XSMALL = 10.0f
        private fun getTextSize(size: Int): Float {
            return when (size) {
                0 -> XSMALL
                1 -> SMALL
                2 -> MEDIUM
                3 -> LARGE
                4 -> XLARGE
                5 -> XXLARGE
                else -> MEDIUM
            }
        }
    }
}