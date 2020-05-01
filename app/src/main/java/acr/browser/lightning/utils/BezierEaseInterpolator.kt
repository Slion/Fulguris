package acr.browser.lightning.utils

import android.view.animation.Interpolator
import androidx.core.view.animation.PathInterpolatorCompat


internal class BezierEaseInterpolator : Interpolator {
    override fun getInterpolation(input: Float): Float {
        return BezierEaseInterpolator.Companion.sBezierInterpolator.getInterpolation(input)
    }

    companion object {
        private val sBezierInterpolator: Interpolator = PathInterpolatorCompat.create(0.25f, 0.1f, 0.25f, 1f)
    }
}