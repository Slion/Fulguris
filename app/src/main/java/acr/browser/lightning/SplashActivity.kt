package acr.browser.lightning

import acr.browser.lightning.browser.activity.ThemableBrowserActivity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler

class SplashActivity : ThemableBrowserActivity() {

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
