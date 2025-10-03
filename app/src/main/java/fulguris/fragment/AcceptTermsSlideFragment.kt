package fulguris.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.appintro.SlideBackgroundColorHolder
import com.github.appintro.SlidePolicy
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.extensions.flash
import fulguris.settings.preferences.UserPreferences
import javax.inject.Inject

/**
 * App onboarding intro slide for user to accept terms and conditions
 * Just hosts AcceptTermsSlideFragment, defines background color for transition animations and takes care of system bars clearance
 */
@AndroidEntryPoint
class AcceptTermsSlideFragment: Fragment(), SlidePolicy, SlideBackgroundColorHolder {

    @Inject
    lateinit var userPreferences: UserPreferences

    // SlideBackgroundColorHolder implementation
    @Deprecated("Use defaultBackgroundColorRes instead")
    override var defaultBackgroundColor: Int = 0

    override var defaultBackgroundColorRes: Int = R.color.intro_accept_terms_background

    override fun setBackgroundColor(backgroundColor: Int) {
        view?.setBackgroundColor(backgroundColor)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.slide_intro_accept_terms, container, false)

    // Handle window insets to avoid overlap with status bar
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val content = view.findViewById<View>(R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply margin to our inner page content so that we clear the system bars while still applying background color to them
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
                bottomMargin = systemBars.bottom
                leftMargin = systemBars.left
                rightMargin = systemBars.right
            }
            insets
        }

        super.onViewCreated(view, savedInstanceState)
    }

    // Prevent going forward if terms not accepted
    override val isPolicyRespected: Boolean
        get() = userPreferences.acceptTerms

    // Display error message if user tries to go forward without accepting terms
    override fun onUserIllegallyRequestedNextPage() {
        // Trigger ripple effect on the accept terms switch to draw attention
        val preferenceFragment = childFragmentManager.findFragmentById(R.id.fragmentContainerView)
            as? AcceptTermsPreferenceFragment

        preferenceFragment?.flash(R.string.pref_key_accept_terms)
    }

    companion object {
        fun newInstance() : AcceptTermsSlideFragment {
            return AcceptTermsSlideFragment()
        }
    }
}
