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
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R

/**
 * App onboarding intro slide for ad blocker configuration
 * Hosts AdBlockerPreferenceFragment and handles system bars clearance
 */
@AndroidEntryPoint
class AdBlockerSlideFragment: Fragment(), SlideBackgroundColorHolder {

    // SlideBackgroundColorHolder implementation
    @Deprecated("Use defaultBackgroundColorRes instead")
    override var defaultBackgroundColor: Int = 0

    override var defaultBackgroundColorRes: Int = R.color.md_theme_dark_onTertiary

    override fun setBackgroundColor(backgroundColor: Int) {
        view?.setBackgroundColor(backgroundColor)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.slide_intro_ad_blocker, container, false)

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

    companion object {
        fun newInstance() : AdBlockerSlideFragment {
            return AdBlockerSlideFragment()
        }
    }
}

