package fulguris.browser

import fulguris.R
import fulguris.activity.WebBrowserActivity
import fulguris.database.bookmark.BookmarkRepository
import fulguris.databinding.MenuMainBinding
import fulguris.di.HiltEntryPoint
import fulguris.di.configPrefs
import fulguris.settings.preferences.UserPreferences
import fulguris.utils.Utils
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import androidx.core.view.isVisible
import dagger.hilt.android.EntryPointAccessors

/**
 * That's our browser main navigation menu.
 */
class MenuMain : PopupWindow {

    var iBinding: MenuMainBinding
    var iIsIncognito = false

    constructor(layoutInflater: LayoutInflater, aBinding: MenuMainBinding = inflate(layoutInflater))
            : super(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

        //aBinding.root.context.injector.inject(this)

        iBinding = aBinding


        // Elevation just need to be high enough not to cut the effect defined in our layout
        elevation = 100F
        //
        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog

        // Needed on Android 5 to make sure our pop-up can be dismissed by tapping outside and back button
        // See: https://stackoverflow.com/questions/46872634/close-popupwindow-upon-tapping-outside-or-back-button
        setBackgroundDrawable(ColorDrawable())

        // Incognito status will be used to manage menu items visibility
        iIsIncognito = (aBinding.root.context as WebBrowserActivity).isIncognito()

        //val radius: Float = getResources().getDimension(R.dimen.default_corner_radius) //32dp

        //iBinding.layoutMenuItems.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        //iBinding.layoutMenuItems.layoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING)
        //iBinding.layoutMenuItems.layoutTransition.disableTransitionType(LayoutTransition.CHANGING)


        //iBinding.layoutMenuItems.layoutTransition.setAnimator(LayoutTransition.CHANGE_APPEARING, animator)
        //iBinding.layoutMenuItems.layoutTransition.setDuration(LayoutTransition.CHANGE_APPEARING, animator.duration)
        //iBinding.layoutMenuItems.layoutTransition.setAnimator(LayoutTransition.CHANGING, animator)
        //iBinding.layoutMenuItems.layoutTransition.setDuration(LayoutTransition.CHANGING, animator.duration)


        /*
        // TODO: That fixes the corner but leaves a square shadow behind
        val toolbar: AppBarLayout = view.findViewById(R.id.header)
        val materialShapeDrawable = toolbar.background as MaterialShapeDrawable
        materialShapeDrawable.shapeAppearanceModel = materialShapeDrawable.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, Utils.dpToPx(16F).toFloat())
                .build()
         */

        val hiltEntryPoint = EntryPointAccessors.fromApplication(iBinding.root.context.applicationContext, HiltEntryPoint::class.java)
        bookmarkModel = hiltEntryPoint.bookmarkRepository
        iUserPreferences = hiltEntryPoint.userPreferences
    }

    val bookmarkModel: BookmarkRepository
    val iUserPreferences: UserPreferences



    /**
     * Scroll to the start of our menu.
     * Could be the bottom or the top depending if we are using bottom toolbars.
     * Default delay matches items animation.
     */
    private fun scrollToStart(aDelay: Long = 300) {
        iBinding.scrollViewItems.postDelayed(
            {
                if (contentView.context.configPrefs.toolbarsBottom) {
                    iBinding.scrollViewItems.smoothScrollTo(0, iBinding.scrollViewItems.height);
                } else {
                    iBinding.scrollViewItems.smoothScrollTo(0, 0);
                }
            }, aDelay
        )
    }

    /**
     * Register click observer with the given menu item.
     * This gave us the opportunity to dismiss the dialog…
     * …but since we don't do that for all menu items anymore it is kinda useless.
     */
    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
        }
    }


    /**
     * Show menu items corresponding to our main menu.
     */
    private fun applyMainMenuItemVisibility() {
        // Reset items visibility
        iBinding.layoutMenuItemsContainer.isVisible=true;
        iBinding.menuItemWebPage.isVisible = true
        // Basic items
        iBinding.menuItemSessions.isVisible = !iIsIncognito
        //iBinding.menuItemBookmarks.isVisible = true
        iBinding.menuItemHistory.isVisible = true
        iBinding.menuItemDownloads.isVisible = true
        iBinding.menuItemNewTab.isVisible = true
        iBinding.menuItemIncognito.isVisible = !iIsIncognito
        iBinding.menuItemOptions.isVisible = true
        iBinding.menuItemSettings.isVisible = !iIsIncognito


        iBinding.menuItemExit.isVisible = iUserPreferences.menuShowExit || iIsIncognito
        iBinding.menuItemNewTab.isVisible = iUserPreferences.menuShowNewTab
    }

    /**
     * Open up this popup menu
     */
    fun show(aAnchor: View) {

        applyMainMenuItemVisibility()

        // Get our anchor location
        val anchorLoc = IntArray(2)
        aAnchor.getLocationInWindow(anchorLoc)
        // Show our popup menu from the right side of the screen below our anchor
        val gravity = if (contentView.context.configPrefs.toolbarsBottom) Gravity.BOTTOM or Gravity.RIGHT else Gravity.TOP or Gravity.RIGHT
        val yOffset = if (contentView.context.configPrefs.toolbarsBottom) (contentView.context as WebBrowserActivity).iBinding.root.height - anchorLoc[1] - aAnchor.height else anchorLoc[1]
        showAtLocation(aAnchor, gravity,
                // Offset from the right screen edge
                fulguris.utils.Utils.dpToPx(10F),
                // Above our anchor
                yOffset)

        scrollToStart(0)
    }

    companion object {

        fun inflate(layoutInflater: LayoutInflater): MenuMainBinding {
            return MenuMainBinding.inflate(layoutInflater)
        }

    }
}

