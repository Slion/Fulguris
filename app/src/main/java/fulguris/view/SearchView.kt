package fulguris.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import java.util.concurrent.TimeUnit

class SearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    interface PreFocusListener {
        fun onPreFocus()
    }

    /**
     * Lets the host update the field content when the edition starts and ends: show the URL while
     * editing and the label the rest of the time.
     */
    interface EditListener {
        fun onEditStart()
        fun onEditEnd()
    }

    var onPreFocusListener: PreFocusListener? = null
    var editListener: EditListener? = null

    /**
     * When true, the InputConnection wrapper silently drops empty IME commits. Set by the
     * host activity during the brief window after entering edit mode so the leanback TV IME
     * cannot wipe the freshly selected URL text on its first connection.
     */
    var isEditGuarded: Boolean = false

    private var isBeingClicked: Boolean = false
    private var timePressedNs: Long = 0

    /**
     * True while the field is actively being edited (cursor shown, suggestions allowed).
     *
     * A pointer or touch interaction goes straight to edit mode. Directional navigation
     * (D-pad / keyboard / game pad) only focuses the field for navigation; the user then presses
     * the center / enter / A button to start editing. This makes address bar navigation behave the
     * same on touch screens, TVs, keyboards and game pads.
     */
    var isEditing: Boolean = false
        private set

    /**
     * Whether we believe the soft keyboard is currently shown for this field.
     */
    private var isKeyboardShown: Boolean = false

    /**
     * Set when a confirm key press starts the edition so the matching key up doesn't get
     * interpreted as a validation right away.
     */
    private var swallowNextConfirmUp: Boolean = false

    private val inputMethodManager: InputMethodManager?
        get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    /**
     * Consume, once, the confirm key up that belongs to the press which just started the edition.
     */
    fun shouldSwallowConfirmUp(): Boolean {
        if (swallowNextConfirmUp) {
            swallowNextConfirmUp = false
            return true
        }
        return false
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // Block the TV leanback IME's empty initialisation commit that wipes the URL.
                if (isEditGuarded && text != null && text.isEmpty() && this@SearchView.length() > 0) {
                    return true
                }
                return super.commitText(text, newCursorPosition)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                timePressedNs = System.nanoTime()
                isBeingClicked = true
                // A pointer or touch interaction goes straight to edit mode. The framework shows
                // the keyboard on its own when a touchable field gains focus from a tap.
                if (!isEditing) {
                    startEditing(fromPointer = true)
                }
            }
            MotionEvent.ACTION_CANCEL -> isBeingClicked = false
            MotionEvent.ACTION_UP -> if (isBeingClicked && !isLongPress(timePressedNs)) {
                onPreFocusListener?.onPreFocus()
            }
        }

        return super.onTouchEvent(event)
    }

    private fun isLongPress(actionDownTime: Long): Boolean =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - actionDownTime) >= ViewConfiguration.getLongPressTimeout()

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            if (!isEditing) {
                // Focused for directional navigation, not editing yet.
                isCursorVisible = false
                dismissDropDown()
            }
        } else {
            resetEditingState()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEditing) {
            // While focused for navigation, the center / enter / A key starts editing.
            if (isConfirmKey(keyCode)) {
                startEditing(fromPointer = false)
                swallowNextConfirmUp = true
                return true
            }
            // Don't consume the directional keys to move the cursor while only navigating; let
            // the framework move focus out of the field instead.
            if (isDirectionalKey(keyCode)) {
                return false
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        // ESC and BACK follow the same two-stage exit: keyboard first, then cancel editing.
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) && isEditing) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (isKeyboardShown) {
                    // First press hides the keyboard, keeping the suggestion popup.
                    hideKeyboard()
                } else {
                    // Second press cancels the edition.
                    cancelEditing()
                }
            }
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    private fun isConfirmKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A

    private fun isDirectionalKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    override fun showDropDown() {
        // Suppress during the guard window to avoid the popup flickering open-close-open
        // while the URL is being set and the TV IME's stale commits are being absorbed.
        if (!isEditing || isEditGuarded) {
            return
        }
        super.showDropDown()
    }

    /**
     * Enter edit mode: show the cursor and keyboard. The host swaps the label for the URL and
     * selects all of its text.
     */
    private fun startEditing(fromPointer: Boolean) {
        isEditing = true
        isCursorVisible = true
        editListener?.onEditStart()
        if (!fromPointer) {
            // The framework shows the keyboard on its own for pointer / touch focus.
            inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
        isKeyboardShown = true
    }

    /**
     * Hide the keyboard while staying in edit mode so the suggestion popup remains usable.
     */
    private fun hideKeyboard() {
        isKeyboardShown = false
        inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
    }

    /**
     * Cancel the current edition: go back to the navigation label, dismiss the popup, hide the
     * keyboard and keep the field focused for navigation.
     */
    private fun cancelEditing() {
        resetEditingState()
        editListener?.onEditEnd()
        dismissDropDown()
        inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun resetEditingState() {
        isEditing = false
        isKeyboardShown = false
        isCursorVisible = false
    }

}

