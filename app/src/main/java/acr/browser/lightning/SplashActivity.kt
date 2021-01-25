package acr.browser.lightning

import acr.browser.lightning.browser.activity.ThemedBrowserActivity
import android.content.Intent
import android.os.Bundle
import android.os.Handler

class SplashActivity : ThemedBrowserActivity() {

    val mHandler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // TODO: check if we need onboarding

        // Launch main activity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent);

        // Close this activity
        mHandler.postDelayed({   finish()},3000)
    }
}
