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

import android.app.Activity
import android.os.Bundle
import fulguris.utils.BrowserChooser
import timber.log.Timber

/**
 * A transparent activity that shows a browser chooser dialog and forwards the intent to other browsers.
 * This activity excludes the current app from the browser list, showing only external browsers.
 * It is not exported and can only be launched from within the app.
 * In theory we should be able to support that in SelectBrowserActivity using extra parameters in intent but we could not get it working for some reason.
 */
class SelectOtherBrowserActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the URL from the intent
        val url = intent?.data?.toString()

        Timber.d("SelectOtherBrowserActivity: URL = $url")

        if (url.isNullOrEmpty()) {
            Timber.w("SelectOtherBrowserActivity: No URL provided in intent")
            finish()
            return
        }

        // Extract extras from the intent to pass to the target browser
        val extras = intent?.extras

        // Show the browser chooser dialog
        // excludeThisApp is true, so MainActivity and IncognitoActivity will be excluded
        // This shows only external browsers
        BrowserChooser.open(this, url, excludeThisApp = true, extras = extras) {
            // Finish this activity when the dialog is dismissed
            finish()
        }

        // Don't finish here - the dialog will finish this activity when dismissed
    }
}

