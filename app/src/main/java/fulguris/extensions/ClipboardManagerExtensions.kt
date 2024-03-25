package fulguris.extensions

import android.content.ClipData
import android.content.ClipboardManager

/**
 * Copies the [text] to the clipboard with the label `URL`.
 */
fun ClipboardManager.copyToClipboard(text: String, label: String = "URL") {
    setPrimaryClip(ClipData.newPlainText(label, text))
}
