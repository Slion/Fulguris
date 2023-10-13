package fulguris.settings.fragment


import fulguris.R
import fulguris.utils.Observers
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnKeyListener
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import timber.log.Timber


/**
 * Manage our bottom sheet dialog as a fragment thus enabling loading preference screens
 * This is basically a fragment that will contain our settings fragments.
 */
class BottomSheetDialogFragment : BottomSheetDialogFragment {

    /**
     * Allow registered observers to be notified when this dialog is closed by the user
     */
    fun interface Observer {
        fun onCancel(aSheet: BottomSheetDialogFragment)
    }

    @LayoutRes var iResId: Int = R.layout.fragment_settings_domain
    private var iFragmentManager: FragmentManager? = null
    /// Our list of observers
    val observers = Observers<Observer>()

    constructor(aFragmentManager: FragmentManager, @LayoutRes aResId: Int = R.layout.fragment_settings_domain) {
        iFragmentManager = aFragmentManager
        iResId = aResId
    }

    /**
     * We need a default constructor otherwise we crash when recreating the activity when changing keyboard layout for instance.
     */
    constructor() {
        Timber.d("constructor")
    }

    /**
     * Needed to handle back action and pop our back stack or close our dialog accordingly.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dlg = super.onCreateDialog(savedInstanceState)
        dlg.setOnKeyListener(object: OnKeyListener {
            override fun onKey(dialog: DialogInterface?, keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    // User wants to go back
                    Timber.d("Back stack entry count: ${childFragmentManager.backStackEntryCount}")
                    if (childFragmentManager.backStackEntryCount==0) {
                        // Will close the dialog if we are at the root of our stack
                        return false
                    }

                    // Move to previous fragment
                    childFragmentManager.popBackStack()
                    return true
                }

                // We don't handle that key
                return false
            }
        })
        return dlg
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.d("onCreateView")

        // Inflate our layout
        return inflater.inflate(iResId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // We want our bottom sheet to be extended
        // Also make sure it is extended too when opening a new fragment
        (dialog as? BottomSheetDialog)?.behavior?.state = STATE_EXPANDED
    }

    /**
     *
     */
    fun setLayout(@LayoutRes aResId: Int): fulguris.settings.fragment.BottomSheetDialogFragment {
        iResId = aResId
        return this
    }

    /**
     *
     */
    fun show() {
        // In case we were restored we need to fetch our fragment manager ourselves
        if (iFragmentManager==null) {
            iFragmentManager = activity?.supportFragmentManager
        }

        // Just just our dialog then
        iFragmentManager?.let {
            show(it,"bottom-sheet-tag")
        }
    }


    /**
     * Had to implement that to be able to handle cases where user closes the dialog
     * See: https://stackoverflow.com/a/40628141/3969362
     */
    override fun onCancel(dialog: DialogInterface) {
        //Timber.d("onCancel")
        super.onCancel(dialog)
        // Notify our observers this dialog was cancelled
        // Notably when the user hits back button or taps outside dialog
        observers.broadcast { it.onCancel(this) }
    }
}