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
import android.content.Intent
import android.os.Bundle
import fulguris.utils.BrowserChooser
import timber.log.Timber

/**
 * A transparent activity that shows a browser chooser dialog and forwards the intent to the selected browser.
 * This activity has no content and uses a transparent theme.
 * It handles VIEW intents and allows the user to select which browser to use.
 */
class SelectBrowserActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the URL from the intent
        val url = intent?.data?.toString()

        Timber.d("SelectBrowserActivity: URL = $url")

        if (url.isNullOrEmpty()) {
            Timber.w("SelectBrowserActivity: No URL provided in intent")
            finish()
            return
        }

        // Check if the intent comes from our own app
        val callingPackage = callingActivity?.packageName ?: intent?.getStringExtra("source_package")
        val isFromOurApp = callingPackage == packageName

        Timber.d("SelectBrowserActivity: Calling package = $callingPackage, isFromOurApp = $isFromOurApp")

        if (isFromOurApp) {
            // Intent comes from Fulguris itself, forward directly to MainActivity
            Timber.d("SelectBrowserActivity: Forwarding to MainActivity")
            val forwardIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = intent.data
                // Copy any extras from the original intent
                intent.extras?.let { putExtras(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(forwardIntent)
            finish()
            return
        }

        // Show the browser chooser dialog for external intents
        // Force include incognito activity to give users the option to open in incognito mode
        BrowserChooser.open(this, url, excludeThisApp = false, forceIncludeIncognito = true) {
            // Finish this activity when the dialog is dismissed
            finish()
        }

        // Don't finish here - the dialog will finish this activity when dismissed
    }
}
