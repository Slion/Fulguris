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

import fulguris.R
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Still needed a splash screen activity as the SplashScreen API would not play well with our themed activity.
 * We just could not get our theme override to work then.
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity @Inject constructor(): LocaleAwareActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Splashscreen actually crashes on Android 12 when targeting Android 13
        // https://stackoverflow.com/questions/72390747/crash-activity-client-record-must-not-be-null-to-execute-transaction-item-only
        // See: https://issuetracker.google.com/issues/210886009
        //val skip = Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2
        // Just skip it always it looks the same without it anyway
        // Skipping it on Samsung Galaxy A22 with Android 13 looked ugly
        val skip = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        if (skip) {
            // Need to do that when not using splash screen otherwise we crash with:
            // java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
            setTheme(R.style.Theme_App_DayNight)
        }

        if (!skip) {
            // Setup our splash screen
            // See: https://developer.android.com/guide/topics/ui/splash-screen
            val splashScreen = installSplashScreen()
            splashScreen.setOnExitAnimationListener {
                // Callback once our splash screen is done
                // Splash screen duration is define in our style in Theme.App.SplashScreen
                // NOTE: Though it does not seem to be working, therefore we cab use the post delayed below

                // Remove our splash screen, though not needed since we are closing this activity anyway
                //it.remove()
                // Close this activity, with a defensive delay for smoother transitions on slower devices
                //finish()
            }
        }

        //setContentView(R.layout.activity_splash)

        findViewById<View>(android.R.id.content).
            // Put this here as above in the callback it did not work on Android 12
            // It would be too much to ask Google engineer to test their code across Android versions…
        postDelayed({
            Timber.d("SplashScreen skipped: $skip")
            // Just start our main activity now for fastest loading
            // TODO: check if we need onboarding
            // Launch main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            //
            finish()
        },0)
    }


    override fun onLocaleChanged() {
        //TODO("Not yet implemented")
    }
}
