package acr.browser.lightning.view

import acr.browser.lightning.R
import acr.browser.lightning.extensions.pad
import acr.browser.lightning.extensions.toBitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.res.ResourcesCompat
//import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.color.MaterialColors

/**
 * [LightningViewTitle] acts as a container class
 * for the favicon and page title, the information used
 * by the tab adapters to show the tabs to the user.
 * TODO: Add HTML meta theme color?
 */
class LightningViewTitle(context: Context) {

    init {
        // TODO: find a way to update default favicon when the theme changed
        if (defaultFavicon==null) {
            //val background = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK)
            defaultFavicon =  ResourcesCompat.getDrawable(context.resources, R.drawable.ic_web, context.theme)!!.toBitmap(256,256)
        }
    }

    private var favicon: Bitmap = defaultFavicon!!
    private var title = context.getString(R.string.action_new_tab)

    /**
     * Set the current favicon to a new Bitmap.
     * May be null, if null, the default will be used.
     *
     * @param favicon the potentially null favicon to set.
     */
    fun setFavicon(favicon: Bitmap) {
        this.favicon = favicon.pad()
    }

    /**
     *
     */
    fun resetFavicon() {
        favicon = defaultFavicon!!
    }

    /**
     * Gets the current title, which is not null. Can be an empty string.
     *
     * @return the non-null title.
     */
    fun getTitle(): String = title

    /**
     * Set the current title to a new title. If the title is null, an empty title will be used.
     *
     * @param title the title to set.
     */
    fun setTitle(title: String) {
        this.title = title
    }

    /**
     * Gets the favicon of the page, which is not null.
     * Either the favicon, or a default icon.
     *
     * @return the favicon or a default if that is null.
     */
    fun getFavicon(): Bitmap = favicon

    companion object {
        private var defaultFavicon: Bitmap? = null
    }

}
