package acr.browser.lightning.utils

import android.view.View
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
