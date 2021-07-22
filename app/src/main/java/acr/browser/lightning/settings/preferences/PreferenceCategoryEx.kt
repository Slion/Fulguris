package acr.browser.lightning.settings.preferences


import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

// Used to enable multiple line summary
// See: https://stackoverflow.com/questions/6729484/android-preference-summary-how-to-set-3-lines-in-summary
class PreferenceCategoryEx : PreferenceCategory {
    constructor(ctx: Context?, attrs: AttributeSet?, defStyle: Int) : super(ctx, attrs, defStyle) {}
    constructor(ctx: Context?, attrs: AttributeSet?) : super(ctx, attrs) {}

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        // Enable multiple line support
        summary.isSingleLine = false
        summary.maxLines = 10 // Just need to be high enough I guess
    }
}