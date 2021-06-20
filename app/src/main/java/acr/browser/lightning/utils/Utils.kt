package acr.browser.lightning.utils

import acr.browser.lightning.di.configPrefs
import acr.browser.lightning.extensions.canScrollVertically
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.lang.reflect.Method

/**
 * To fix issues with wrong edge-to-edge bottom sheet top padding and status bar icon color…
 * …we need to call [BottomSheetDialog.EdgeToEdgeCallback.setPaddingForPosition] which is a private function from a private class.
 * See: https://github.com/material-components/material-components-android/issues/2165
 */
fun adjustBottomSheet(aDialog : BottomSheetDialog) {
    // Get our private class
    val classEdgeToEdgeCallback = Class.forName("com.google.android.material.bottomsheet.BottomSheetDialog\$EdgeToEdgeCallback")
    // Get our private method
    val methodSetPaddingForPosition: Method = classEdgeToEdgeCallback.getDeclaredMethod("setPaddingForPosition", View::class.java)
    methodSetPaddingForPosition.isAccessible = true
    // Get private field containing our EdgeToEdgeCallback instance
    val fieldEdgeToEdgeCallback = BottomSheetDialog::class.java.getDeclaredField("edgeToEdgeCallback")
    fieldEdgeToEdgeCallback.isAccessible = true
    // Get our bottom sheet view field
    val fieldBottomField = BottomSheetDialog::class.java.getDeclaredField("bottomSheet")
    fieldBottomField.isAccessible = true
    // Eventually call setPaddingForPosition from EdgeToEdgeCallback instance passing bottom sheet view as parameter
    methodSetPaddingForPosition.invoke(fieldEdgeToEdgeCallback.get(aDialog),fieldBottomField.get(aDialog))
}


fun portraitSharedPreferencesName(context: Context): String {
    return context.packageName + "_preferences_portrait"
}

fun landscapeSharedPreferencesName(context: Context): String {
    return context.packageName + "_preferences_landscape"
}

/**
 * Workaround reversed layout bug: https://github.com/Slion/Fulguris/issues/212
 */
fun fixScrollBug(aList : RecyclerView): Boolean {
    val lm = (aList.layoutManager as LinearLayoutManager)
    // Can't change stackFromEnd when computing layout or scrolling otherwise it throws an exception
    if (!aList.isComputingLayout) {
        if (aList.context.configPrefs.toolbarsBottom) {
            // Workaround reversed layout bug: https://github.com/Slion/Fulguris/issues/212
            if (lm.stackFromEnd != aList.canScrollVertically()) {
                lm.stackFromEnd = !lm.stackFromEnd
                return true
            }
        } else {
            // Make sure this is set properly when not using bottom toolbars
            // No need to check if the value is already set properly as this is already done internally
            lm.stackFromEnd = false
        }
    }

    return false
}