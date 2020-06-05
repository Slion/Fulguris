package acr.browser.lightning.browser

import acr.browser.lightning.R
import acr.browser.lightning.utils.Utils
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build.VERSION.SDK_INT
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow

class BrowserPopupMenu : PopupWindow {

    constructor(layoutInflater: LayoutInflater, view: View = BrowserPopupMenu.inflate(layoutInflater))
            : super(view, WRAP_CONTENT, WRAP_CONTENT, true) {

        animationStyle = R.style.AnimationMenu
        //animationStyle = android.R.style.Animation_Dialog
    }

    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(rootView: View, anchorView: View) {
        // Assuming top right for now
        //val anchorLocation = IntArray(2)
        //anchorView.getLocationOnScreen(anchorLocation)
        val x = Utils.dpToPx(5f) //anchorLocation[0] margin
        val y =  Utils.dpToPx(5f) //anchorLocation[1] //+ margin
        showAtLocation(rootView, Gravity.TOP or Gravity.RIGHT, x, y)
    }

    companion object {

        private const val margin = 15

        fun inflate(layoutInflater: LayoutInflater): View {
            return layoutInflater.inflate(R.layout.popup_menu_browser, null)
        }

    }
}

