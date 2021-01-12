package acr.browser.lightning.browser.sessions

import acr.browser.lightning.R
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.utils.ItemTouchHelperAdapter
import acr.browser.lightning.view.BackgroundDrawable
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import java.util.*

/**
 * Sessions [RecyclerView.Adapter].
 *
 * TODO: consider using [ListAdapter] instead of [RecyclerView.Adapter]
 */
class SessionsAdapter(
        private val uiController: UIController
) : RecyclerView.Adapter<SessionViewHolder>(), ItemTouchHelperAdapter {

    // Current sessions shown in our dialog
    private var iSessions: ArrayList<Session> = arrayListOf<Session>()
    // Collection of disposable subscriptions observing edit mode changes
    private var iEditModeSubscriptions: CompositeDisposable = CompositeDisposable()
    // Here comes some most certainly overkill way to notify our view holders that our edit mode has changed
    // See: https://medium.com/@MiguelSesma/update-recycler-view-content-without-refreshing-the-data-bb79d768bde8
    // See: https://stackoverflow.com/a/49433976/3969362
    var iEditModeEnabledObservable = BehaviorSubject.createDefault(false)

    /**
     * Display the given list of session in our recycler view.
     * Possibly updating an existing list.
     */
    fun showSessions(aSessions: List<Session>) {
        DiffUtil.calculateDiff(SessionsDiffCallback(iSessions, aSessions)).dispatchUpdatesTo(this)
        iSessions.clear()
        // Do a deep copy for our diff to work
        // TODO: Surely there must be a way to manage a recycler view without doing a copy of our data set
        aSessions.forEach { s -> iSessions.add(Session(s.name,s.tabCount,s.isCurrent)) }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // Taking some wild guess here
        // Do we need to explicitly call that when adapter is being destroyed?
        // I'm guessing not
        iEditModeSubscriptions.dispose()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SessionViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.session_list_item, viewGroup, false)
        //view.background = BackgroundDrawable(view.context)
        return SessionViewHolder(view, uiController).apply {
            // Ask our newly created view holder to observe our edit mode status
            // Thus buttons on our items will be shown or hidden
            iEditModeSubscriptions.add(observeEditMode(iEditModeEnabledObservable))
        }
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = iSessions[position]
        holder.textName.tag = position
        holder.textName.text = holder.sessionLabel()

        // Set item font style according to current session
        if (session.isCurrent) {
            TextViewCompat.setTextAppearance(holder.textName, R.style.boldText)
        } else {
            TextViewCompat.setTextAppearance(holder.textName, R.style.normalText)
        }

        //updateViewHolderAppearance(holder, web.favicon, web.themeColor, web.isForegroundTab)
        //updateViewHolderFavicon(holder, web.favicon, web.isForegroundTab)
        //updateViewHolderBackground(holder, web.isForegroundTab)
    }


    private fun updateViewHolderFavicon(viewHolder: SessionViewHolder, favicon: Bitmap?, isForeground: Boolean) {
        // Apply filter to favicon if needed
        /*
        favicon?.let {
            val ba = uiController as BrowserActivity
            viewHolder.favicon.setImageForTheme(it,ba.isDarkTheme)
        } ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)

         */
    }

    private fun updateViewHolderBackground(viewHolder: SessionViewHolder, isForeground: Boolean) {
        val verticalBackground = viewHolder.layout.background as BackgroundDrawable
        verticalBackground.isCrossFadeEnabled = false
        if (isForeground) {
            verticalBackground.startTransition(200)
        } else {
            verticalBackground.reverseTransition(200)
        }
    }

    private fun updateViewHolderAppearance(viewHolder: SessionViewHolder, favicon: Bitmap?, color: Int, isForeground: Boolean) {
        /*
        if (isForeground) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
            uiController.changeToolbarBackground(favicon, color, null)
        } else {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.normalText)
        }

         */
    }

    override fun getItemCount() = iSessions.size

    // From ItemTouchHelperAdapter
    // An item was was moved through drag & drop
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    {
        // Note: recent tab list is not affected
        // Swap local list position
        Collections.swap(iSessions, fromPosition, toPosition)
        // Swap model list position
        Collections.swap(uiController.getTabModel().allTabs, fromPosition, toPosition)
        // Tell base class an item was moved
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    // From ItemTouchHelperAdapter
    override fun onItemDismiss(position: Int)
    {
        uiController.tabCloseClicked(position)
    }

}


/**
 * Diffing callback used to determine whether changes have been made to the list.
 *
 * @param oldList The old list that is being replaced by the [newList].
 * @param newList The new list replacing the [oldList], which may or may not be different.
 */
class SessionsDiffCallback(
        private val oldList: List<Session>,
        private val newList: List<Session>
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].name == newList[newItemPosition].name

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].tabCount == newList[newItemPosition].tabCount &&
            oldList[oldItemPosition].isCurrent == newList[newItemPosition].isCurrent
}
