package fulguris.activity

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro2
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.fragment.AcceptTermsSlideFragment
import fulguris.fragment.AdBlockerSlideFragment
import fulguris.fragment.TelemetrySlideFragment
import timber.log.Timber

/**
 * App introduction activity that showcases Fulguris browser features
 * Uses AppIntro library to provide a smooth onboarding experience
 */
@AndroidEntryPoint
class IntroActivity : AppIntro2() {

    companion object {
        private const val KEY_CURRENT_SLIDE_POSITION = "current_slide_position"
    }

    private var lightningAnimator: ObjectAnimator? = null
    private var scaleXAnimator: ObjectAnimator? = null
    private var scaleYAnimator: ObjectAnimator? = null
    // Use this to identify our welcome fragment for animation restarts
    private var welcomeFragment: Fragment? = null
    // Track the current slide position for configuration changes
    private var currentSlidePosition = 0

    /**
     * Public method to allow fragments to navigate to the next slide
     */
    fun nextSlide() {
        goToNextSlide()
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // Force dark theme for intro regardless of system setting or user preferences
        // Specified in manifest
        //setTheme(R.style.Theme_App_Dark)

        super.onCreate(savedInstanceState)

        // Restore slide position if activity was recreated
        if (savedInstanceState != null) {
            currentSlidePosition = savedInstanceState.getInt(KEY_CURRENT_SLIDE_POSITION, 0)
            Timber.d("onCreate: Restored slide position=$currentSlidePosition from savedInstanceState")
        } else {
            Timber.d("onCreate: Fresh start, no saved state")
        }

        // Show the status bar (AppIntro hides it by default)
        showStatusBar(true)

        // Configure AppIntro appearance
        setTransformer(AppIntroPageTransformerType.Parallax())
        isColorTransitionsEnabled = true
        setIndicatorColor(
            selectedIndicatorColor = ContextCompat.getColor(this, R.color.md_theme_dark_primary),
            unselectedIndicatorColor = ContextCompat.getColor(this, R.color.md_theme_dark_outline)
        )

        // Add introduction slides
        addSlide(createWelcomeSlide())
        addSlide(AcceptTermsSlideFragment.newInstance())
        // Don't add telemetry slide for F-Droid variant
        if (!fulguris.Variant.isFdroid()) {
            addSlide(TelemetrySlideFragment.newInstance())
        }
        // Add ad blocker slide
        addSlide(AdBlockerSlideFragment.newInstance())
        // TODO: Could add a variant specific slide here

        // Request notification permission on the permissions slide (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Only add permissions slide if permission is not already granted
            val notificationPermission = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (notificationPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Add the permissions slide first
                addSlide(createPermissionsSlide())
                // Then ask for permissions on that slide using the actual slide count
                askForPermissions(
                    permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    // The permissions slide (1-indexed: Welcome=1, Terms=2, Telemetry=3, AdBlocker=4, Permissions=5)
                    slideNumber = totalSlidesNumber,
                    required = false // Make it optional, not required to proceed
                )
            }
        }

        // Configure navigation
        isSkipButtonEnabled = true
        isWizardMode = true
        //setImmersiveMode()
    }

    override fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        Timber.d("onPageSelected: position=$position")
        currentSlidePosition = position
        handlePageAnimation(position)
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume: currentSlidePosition=$currentSlidePosition")

        // If we're on the welcome slide after activity recreation, restart the animation
        // Use a post delay to ensure all views are fully set up
        if (currentSlidePosition == 0) {
            findViewById<View>(android.R.id.content).postDelayed({
                Timber.d("onResume: Attempting to restart animation for welcome slide")
                restartAnimationAfterRecreation()
            }, 300)
        }
    }

    // Handle back button navigation to restart animation when returning to welcome slide
    override fun onSlideChanged(oldFragment: Fragment?, newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        Timber.d("onSlideChanged: oldFragment=${oldFragment?.javaClass?.simpleName}, newFragment=${newFragment?.javaClass?.simpleName}")

        // Check if we're on the welcome slide and restart animation
        // Use a post delay to ensure the slide change is complete
        findViewById<View>(android.R.id.content).postDelayed({
            // Check if the new fragment is the welcome slide (position 0)
            // Instead of comparing fragment references, check if we're on slide 0
            if (currentSlidePosition == 0 && newFragment is AppIntroFragment) {
                Timber.d("onSlideChanged: Starting animation for welcome slide (position 0)")
                handlePageAnimation(0)
            } else {
                Timber.d("onSlideChanged: Not on welcome slide (position=$currentSlidePosition), stopping animation")
                handlePageAnimation(-1) // Stop animation for other slides
            }
        }, 50)
    }

    private fun handlePageAnimation(position: Int) {
        Timber.d("handlePageAnimation: position=$position")
        // Stop any existing animation first to avoid duplicates
        stopLightningAnimation()

        // Apply animation to the first slide (welcome slide with lightning bolt)
        if (position == 0) {
            Timber.d("handlePageAnimation: Starting lightning bolt animation for welcome slide")
            // Start animation with a small delay to ensure the view is ready
            animateLightningBolt()
        } else {
            Timber.d("handlePageAnimation: Not on welcome slide, no animation")
        }
    }

    private fun animateLightningBolt() {
        Timber.d("animateLightningBolt: Starting animation search")
        // Find the ImageView in the current fragment
        val currentFragment = supportFragmentManager.fragments
            .find { it is AppIntroFragment && it.isVisible }

        Timber.d("animateLightningBolt: Found fragment=${currentFragment?.javaClass?.simpleName}, isVisible=${currentFragment?.isVisible}")

        currentFragment?.view?.let { fragmentView ->
            Timber.d("animateLightningBolt: Fragment has view")
            val imageView = fragmentView.findViewById<ImageView>(com.github.appintro.R.id.image)
            Timber.d("animateLightningBolt: ImageView found=${imageView != null}")
            imageView?.let { iv ->
                Timber.d("animateLightningBolt: Starting animations on ImageView")

                iv.scaleX = 0f
                iv.scaleY = 0f// Ensure the ImageView is visible and reset its properties
                //val count = ObjectAnimator.INFINITE
                val count = 0

                val time: Long = 1600
                // Create complex back-and-forth spinning animation
                lightningAnimator = ObjectAnimator.ofFloat(
                    iv,
                    "rotation",
                    0f, 720f, 725f, 724f, 723f, 722f, 721f, 720f // Fast spin, overshoot, reverse, overshoot, repeat
                    //0f, 720f, 725f, 724f, 723f, 722f, 721f, 720f, 719f, 718f, 717f, 716f, 715f, 0f  // Fast spin, overshoot, reverse, overshoot, repeat
                ).apply {
                    duration = time  // 4 seconds for full cycle
                    repeatCount = count

                    // Custom interpolator for acceleration/deceleration effect
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    start()
                }

                // Add pulsing scale animation that syncs with rotation changes
                scaleXAnimator = ObjectAnimator.ofFloat(iv, "scaleX", 0f, 1f, 1f, 1f, 1f, 1f, 1f).apply {
                    duration = time  // Match rotation duration
                    repeatCount = count
                    start()
                }

                scaleYAnimator = ObjectAnimator.ofFloat(iv, "scaleY", 0f, 1f, 1f, 1f, 1f, 1f, 1f).apply {
                    duration = time  // Match rotation duration
                    repeatCount = count
                    start()
                }

                Timber.d("animateLightningBolt: All animations started successfully")
            } ?: run {
                Timber.w("animateLightningBolt: ImageView not found in fragment view")
            }
        } ?: run {
            Timber.w("animateLightningBolt: Fragment view is null")
        }
    }

    private fun restartAnimationAfterRecreation() {
        Timber.d("restartAnimationAfterRecreation: Starting animation restart process")

        // Use the retry mechanism to find and animate the ImageView after activity recreation
        animateLightningBoltWithRetry()
    }

    // Enhanced version for configuration changes with retry mechanism
    private fun animateLightningBoltWithRetry(retryCount: Int = 0) {
        Timber.d("animateLightningBoltWithRetry: Attempt $retryCount")

        // Try to find the ImageView in multiple ways after configuration change
        var imageView: ImageView? = null

        // Method 1: Find through visible fragment
        val visibleFragment = supportFragmentManager.fragments
            .find { it is AppIntroFragment && it.isVisible }

        Timber.d("animateLightningBoltWithRetry: Visible fragment=${visibleFragment?.javaClass?.simpleName}")

        imageView = visibleFragment?.view?.findViewById(com.github.appintro.R.id.image)
        Timber.d("animateLightningBoltWithRetry: Method 1 - ImageView found=${imageView != null}")

        // Method 2: If not found, try through welcomeFragment directly
        if (imageView == null && welcomeFragment?.isAdded == true) {
            Timber.d("animateLightningBoltWithRetry: Trying method 2 - welcomeFragment direct access")
            imageView = welcomeFragment?.view?.findViewById(com.github.appintro.R.id.image)
            Timber.d("animateLightningBoltWithRetry: Method 2 - ImageView found=${imageView != null}")
        }

        // Method 3: If still not found and we haven't retried too many times, retry
        if (imageView == null && retryCount < 3) {
            Timber.w("animateLightningBoltWithRetry: ImageView not found, retrying in 100ms (attempt ${retryCount + 1}/3)")
            findViewById<View>(android.R.id.content).postDelayed({
                animateLightningBoltWithRetry(retryCount + 1)
            }, 100)
            return
        }

        if (imageView == null) {
            Timber.e("animateLightningBoltWithRetry: Failed to find ImageView after $retryCount retries")

            // Log fragment manager state for debugging
            Timber.d("animateLightningBoltWithRetry: Fragment manager fragments count=${supportFragmentManager.fragments.size}")
            supportFragmentManager.fragments.forEachIndexed { index, fragment ->
                Timber.d("animateLightningBoltWithRetry: Fragment[$index]: ${fragment.javaClass.simpleName}, isAdded=${fragment.isAdded}, isVisible=${fragment.isVisible}, hasView=${fragment.view != null}")
            }
            return
        }

        // If we found the ImageView, start the animation
        imageView?.let { iv ->
            Timber.d("animateLightningBoltWithRetry: Starting animation on found ImageView")
            // Reset the ImageView properties
            iv.scaleX = 0f
            iv.scaleY = 0f
            iv.rotation = 0f

            val count = 0
            val time: Long = 1600

            // Create the animations
            lightningAnimator = ObjectAnimator.ofFloat(
                iv,
                "rotation",
                0f, 720f, 725f, 724f, 723f, 722f, 721f, 720f
            ).apply {
                duration = time
                repeatCount = count
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }

            scaleXAnimator = ObjectAnimator.ofFloat(iv, "scaleX", 0f, 1f, 1f, 1f, 1f, 1f, 1f).apply {
                duration = time
                repeatCount = count
                start()
            }

            scaleYAnimator = ObjectAnimator.ofFloat(iv, "scaleY", 0f, 1f, 1f, 1f, 1f, 1f, 1f).apply {
                duration = time
                repeatCount = count
                start()
            }

            Timber.d("animateLightningBoltWithRetry: Animation started successfully after $retryCount retries")
        }
    }

    private fun stopLightningAnimation() {
        Timber.d("stopLightningAnimation: Stopping all animations")
        lightningAnimator?.cancel()
        scaleXAnimator?.cancel()
        scaleYAnimator?.cancel()
        (scaleXAnimator?.target as? ImageView)?.scaleX = 0f
        (scaleYAnimator?.target as? ImageView)?.scaleY = 0f
        lightningAnimator = null
        scaleXAnimator = null
        scaleYAnimator = null
        Timber.d("stopLightningAnimation: All animations stopped and cleared")
    }

    private fun createWelcomeSlide(): Fragment {
        welcomeFragment = AppIntroFragment.createInstance(
            title = getString(R.string.intro_welcome_title),
            description = getString(R.string.intro_welcome_description),
            imageDrawable = R.drawable.ic_lightning_large,
            backgroundColorRes = R.color.md_theme_dark_onTertiary
        )
        return welcomeFragment!!
    }

    private fun createPermissionsSlide(): Fragment {
        return AppIntroFragment.createInstance(
            title = getString(R.string.intro_notification_permission_title),
            description = getString(R.string.intro_notification_permission_description),
            imageDrawable = R.drawable.ic_chat_info_outline,
            backgroundColorRes = R.color.intro_accept_terms_background
        )
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        finishIntro()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        finishIntro()
    }

    private fun finishIntro() {

        // Start main activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_SLIDE_POSITION, currentSlidePosition)
        Timber.d("onSaveInstanceState: Saving slide position=$currentSlidePosition")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentSlidePosition = savedInstanceState.getInt(KEY_CURRENT_SLIDE_POSITION, 0)
        Timber.d("onRestoreInstanceState: Restored slide position=$currentSlidePosition")
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //Timber.d("onRequestPermissionsResult: requestCode=$requestCode, permissions=${permissions.joinToString()}, grantResults=${grantResults.joinToString()}")

        // For now only our last slide requests permissions
        // and we don't want user to have to push done again after the permission request
        // we just finish our intro here no matter what
        finishIntro()
    }

}
