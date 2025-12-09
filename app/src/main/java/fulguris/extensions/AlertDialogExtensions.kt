package fulguris.extensions

import android.app.Dialog
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Show single choice items.
 *
 * @param items A list of items and their user readable string description.
 * @param checkedItem The item that will be checked when the dialog is displayed.
 * @param onClick Called when an item is clicked. The item clicked is provided.
 */
fun <T> MaterialAlertDialogBuilder.withSingleChoiceItems(
    items: List<Pair<T, String>>,
    checkedItem: T,
    onClick: (dialog: DialogInterface, T) -> Unit
) {
    val checkedIndex = items.map(Pair<T, String>::first).indexOf(checkedItem)
    val titles = items.map(Pair<T, String>::second).toTypedArray()
    setSingleChoiceItems(titles, checkedIndex) { dialog, which ->
        onClick(dialog, items[which].first)
    }
}

/**
 * Apply styles patch before launching our dialog
 */
fun MaterialAlertDialogBuilder.launch(): Dialog {
    // Create our dialog
    val dialog = create()
    // Create our views
    dialog.create()
    // Patch our gap issue, see: https://github.com/material-components/material-components-android/issues/4981
    val contentPanel = dialog.findViewById<android.widget.FrameLayout>(androidx.appcompat.R.id.contentPanel)
    contentPanel?.minimumHeight = 0
    // Show our dialog
    dialog.show()
    //
    return dialog
}

/**
 * Execute the given [action] when the dialog is shown.
 * This is a convenience extension that wraps setOnShowListener with a cleaner API.
 * The listener is automatically removed after being called once.
 */
inline fun Dialog.doOnShow(crossinline action: (DialogInterface) -> Unit) {
    setOnShowListener { dialog ->
        // Deregister the listener after first execution
        setOnShowListener(null)
        // Execute
        action(dialog)
    }
}

