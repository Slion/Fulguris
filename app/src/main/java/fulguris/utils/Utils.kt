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

package fulguris.utils

import fulguris.di.configPrefs
import fulguris.extensions.canScrollVertically
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