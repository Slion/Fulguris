package fulguris.extensions

import android.app.Dialog
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
    onClick: (T) -> Unit
) {
    val checkedIndex = items.map(Pair<T, String>::first).indexOf(checkedItem)
    val titles = items.map(Pair<T, String>::second).toTypedArray()
    setSingleChoiceItems(titles, checkedIndex) { _, which ->
        onClick(items[which].first)
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
