package fulguris.enums


import android.view.View
import fulguris.settings.preferences.IntEnum
import android.view.WindowManager

/**
 * View layer type as an enum so that we can conveniently use it as a preference
 */
enum class LayerType(override val value: Int) : IntEnum {
    /**
     * The view is rendered normally and is not backed by an off-screen buffer. This is the default behavior.
     * Though in my experience this uses hardware acceleration too.
     */
    None(View.LAYER_TYPE_NONE),
    /**
     * The view is rendered in software into a bitmap.
     */
    Software(View.LAYER_TYPE_SOFTWARE),
    /**
     * The view is rendered in hardware into a hardware texture if the application is hardware accelerated.
     */
    Hardware(View.LAYER_TYPE_HARDWARE)
}

