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
 
package acr.browser.lightning

import acr.browser.lightning.browser.activity.ThemedBrowserActivity
import acr.browser.lightning.locale.LocaleAwareActivity
import acr.browser.lightning.settings.preferences.UserPreferences
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Still needed a splash screen activity as the SplashScreen API would not play well with our themed activity.
 * We just could not get our theme override to work then.
 */
@AndroidEntryPoint
class SplashActivity @Inject constructor(): LocaleAwareActivity() {

    val mHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        // Put this here as above in the callback it did not work on Android 12
        // It would be too much to ask Google engineer to test their code across Android versions…
        mHandler.postDelayed({
            // Just start our main activity now for fastest loading
            // TODO: check if we need onboarding
            // Launch main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            //
            finish()
        },0)

        setContentView(R.layout.activity_splash)
    }

    override fun onLocaleChanged() {
        //TODO("Not yet implemented")
    }
}
