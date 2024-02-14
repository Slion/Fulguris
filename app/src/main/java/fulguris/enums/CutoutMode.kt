package fulguris.enums

import android.util.Log
import fulguris.settings.preferences.IntEnum
import android.view.WindowManager

enum class CutoutMode(override val value: Int) : IntEnum {
    /**
     * See: [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT]
     */
    Default(0),
    /**
     * See: [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES]
     */
    ShortEdges(1),
    /**
     * See: [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER]
     */
    Never(2),
    /**
     * See: [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS]
     */
    Always(3)
}

/*
enum class CutoutMode {
    Default = 0,
}
*/
