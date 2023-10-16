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

//package fulguris.browser.sessions
// We kept this package otherwise it fails to load persisted bundles
// See: https://stackoverflow.com/questions/77292533/load-bundle-from-parcelable-class-after-refactoring
// Could not find an easy solution for it, custom ClassLoader did not work
package acr.browser.lightning.browser.sessions

import android.os.Parcel
import android.os.Parcelable

/**
 * You can easily regenerate that parcelable implementation.
 * See: https://stackoverflow.com/a/49426012/3969362
 * We could also use @Parcelize: https://stackoverflow.com/a/69027267/3969362
 *
 * TODO: Don't use Parcelable as it saves the class name in the Bundle and you can't refactor.
 * Instead do it like we did with [fulguris.browser.TabModel].
 */
data class Session (
    var name: String = "",
    var tabCount: Int = -1,
    var isCurrent: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this() {
        val n = parcel.readString();
        if (n == null) {
            name = ""
        }
        else {
            name = n
        }
        tabCount = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(tabCount)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Session> {
        override fun createFromParcel(parcel: Parcel): Session {
            return Session(parcel)
        }

        override fun newArray(size: Int): Array<Session?> {
            return arrayOfNulls(size)
        }
    }
}