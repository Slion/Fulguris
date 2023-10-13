/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package fulguris.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.*
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.max

class CodeView : AppCompatMultiAutoCompleteTextView {
    private var tabWidth = 0
    private var mUpdateDelayTime = 500
    private var modified = true
    private var hasErrors = false
    private var mRemoveErrorsWhenTextChanged = false
    private val mUpdateHandler = Handler(Looper.getMainLooper())
    private var mAutoCompleteTokenizer: Tokenizer? = null
    private val displayDensity = resources.displayMetrics.density
    private val mErrorHashSet: SortedMap<Int, Int> = TreeMap()
    private val mSyntaxPatternMap: MutableMap<Pattern, Int> = HashMap()
    private var mIndentCharacterList = mutableListOf('{', '+', '-', '*', '/', '=')

    constructor(context: Context?) : super(context!!) {
        initEditorView()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        initEditorView()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        initEditorView()
    }

    private fun initEditorView() {
        if (mAutoCompleteTokenizer == null) {
            mAutoCompleteTokenizer = KeywordTokenizer()
        }
        setTokenizer(mAutoCompleteTokenizer)
        setHorizontallyScrolling(true)
        filters = arrayOf(InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int ->
            if (modified && end - start == 1 && start < source.length && dstart < dest.length) {
                val c = source[start]
                if (c == '\n') {
                    return@InputFilter autoIndent(source, dest, dstart, dend)
                }
            }
            source
        }
        )
        addTextChangedListener(mEditorTextWatcher)
    }

    private fun autoIndent(
        source: CharSequence,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence {
        Log.d(TAG, "autoIndent: Auto Indent")
        var indent = ""
        var istart = dstart - 1
        var dataBefore = false
        var pt = 0
        while (istart > -1) {
            val c = dest[istart]
            if (c == '\n') break
            if (c != ' ' && c != '\t') {
                if (!dataBefore) {
                    if (mIndentCharacterList.contains(c)) --pt
                    dataBefore = true
                }
                if (c == '(') {
                    --pt
                } else if (c == ')') {
                    ++pt
                }
            }
            --istart
        }
        if (istart > -1) {
            val charAtCursor = dest[dstart]
            var iend: Int = ++istart
            while (iend < dend) {
                val c = dest[iend]
                if (charAtCursor != '\n' && c == '/' && iend + 1 < dend && dest[iend] == c) {
                    iend += 2
                    break
                }
                if (c != ' ' && c != '\t') {
                    break
                }
                ++iend
            }
            indent += dest.subSequence(istart, iend)
        }
        if (pt < 0) {
            indent += "\t"
        }
        return source.toString() + indent
    }

    private fun highlightSyntax(editable: Editable) {
        if (mSyntaxPatternMap.isEmpty()) return
        for (pattern in mSyntaxPatternMap.keys) {
            val color = mSyntaxPatternMap[pattern]!!
            val m = pattern.matcher(editable)
            while (m.find()) {
                createForegroundColorSpan(editable, m, color)
            }
        }
    }

    private fun highlightErrorLines(editable: Editable) {
        if (mErrorHashSet.isEmpty()) return
        val maxErrorLineValue = mErrorHashSet.lastKey()
        var lineNumber = 0
        val matcher = PATTERN_LINE.matcher(editable)
        while (matcher.find()) {
            if (mErrorHashSet.containsKey(lineNumber)) {
                val color = mErrorHashSet[lineNumber]!!
                createBackgroundColorSpan(editable, matcher, color)
            }
            lineNumber += 1
            if (lineNumber > maxErrorLineValue) break
        }
    }

    private fun createForegroundColorSpan(
        editable: Editable,
        matcher: Matcher,
        @ColorInt color: Int
    ) {
        editable.setSpan(
            ForegroundColorSpan(color),
            matcher.start(), matcher.end(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun createBackgroundColorSpan(
        editable: Editable,
        matcher: Matcher,
        @ColorInt color: Int
    ) {
        editable.setSpan(
            BackgroundColorSpan(color),
            matcher.start(), matcher.end(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun highlight(editable: Editable): Editable {
        try {
            if (editable.isEmpty()) return editable
            clearSpans(editable)
            highlightErrorLines(editable)
            highlightSyntax(editable)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Highlighter Error Message : " + e.message)
        }
        return editable
    }

    private fun highlightWithoutChange(editable: Editable) {
        modified = false
        highlight(editable)
        modified = true
    }

    private fun clearSpans(editable: Editable) {
        val length = editable.length
        val foregroundSpans = editable.getSpans(
            0, length, ForegroundColorSpan::class.java
        )
        run {
            var i = foregroundSpans.size
            while (i-- > 0) {
                editable.removeSpan(foregroundSpans[i])
            }
        }
        val backgroundSpans = editable.getSpans(
            0, length, BackgroundColorSpan::class.java
        )
        var i = backgroundSpans.size
        while (i-- > 0) {
            editable.removeSpan(backgroundSpans[i])
        }
    }

    fun cancelHighlighterRender() {
        mUpdateHandler.removeCallbacks(mUpdateRunnable)
    }

    private fun convertTabs(editable: Editable, start: Int, count: Int) {
        var starts = start
        if (tabWidth < 1) {
            return
        }
        val s = editable.toString()
        val stop = start + count
        while (s.indexOf("\t", starts).also { starts = it } > -1 && start < stop) {
            editable.setSpan(
                TabWidthSpan(),
                start,
                start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ++starts
        }
    }

    fun getSyntaxPatternsSize(): Int {
        return mSyntaxPatternMap.size
    }

    fun removeAllErrorLines() {
        mErrorHashSet.clear()
        hasErrors = false
    }

    override fun showDropDown() {
        val screenPoint = IntArray(2)
        getLocationOnScreen(screenPoint)
        val displayFrame = Rect()
        getWindowVisibleDisplayFrame(displayFrame)
        val position = selectionStart
        val layout = layout
        val line = layout.getLineForOffset(position)
        val verticalDistanceInDp = (750 + 140 * line) / displayDensity
        dropDownVerticalOffset = verticalDistanceInDp.toInt()
        val horizontalDistanceInDp = layout.getPrimaryHorizontal(position) / displayDensity
        dropDownHorizontalOffset = horizontalDistanceInDp.toInt()
        super.showDropDown()
    }

    private val mUpdateRunnable = Runnable {
        val source = text
        highlightWithoutChange(source)
    }
    private val mEditorTextWatcher: TextWatcher = object : TextWatcher {
        private var start = 0
        private var count = 0
        override fun beforeTextChanged(
            charSequence: CharSequence,
            start: Int,
            before: Int,
            count: Int
        ) {
            this.start = start
            this.count = count
        }

        override fun onTextChanged(
            charSequence: CharSequence,
            start: Int,
            before: Int,
            count: Int
        ) {
        }

        override fun afterTextChanged(editable: Editable) {
            cancelHighlighterRender()
            if (getSyntaxPatternsSize() > 0) {
                convertTabs(editable, start, count)
                if (!modified) return
                mUpdateHandler.postDelayed(mUpdateRunnable, mUpdateDelayTime.toLong())
                if (mRemoveErrorsWhenTextChanged) removeAllErrorLines()
            }
        }
    }

    private inner class TabWidthSpan : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: FontMetricsInt?
        ): Int {
            return tabWidth
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
        }
    }

    class KeywordTokenizer : Tokenizer {
        override fun findTokenStart(charSequence: CharSequence, cursor: Int): Int {
            var sequenceStr = charSequence.toString()
            sequenceStr = sequenceStr.substring(0, cursor)
            val spaceIndex = sequenceStr.lastIndexOf(" ")
            val lineIndex = sequenceStr.lastIndexOf("\n")
            val bracketIndex = sequenceStr.lastIndexOf("(")
            val index = max(0, max(spaceIndex, max(lineIndex, bracketIndex)))
            if (index == 0) return 0
            return if (index + 1 < charSequence.length) index + 1 else index
        }

        override fun findTokenEnd(charSequence: CharSequence, cursor: Int): Int {
            return charSequence.length
        }

        override fun terminateToken(charSequence: CharSequence): CharSequence {
            return charSequence
        }
    }

    companion object {
        private val PATTERN_LINE = Pattern.compile("(^.+$)+", Pattern.MULTILINE)
        private const val TAG = "CodeView"
    }
}