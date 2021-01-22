package acr.browser.lightning.browser.sessions

import acr.browser.lightning.R
import acr.browser.lightning.controller.UIController
import acr.browser.lightning.extensions.inflater
import acr.browser.lightning.utils.ItemDragDropSwipeAdapter
import android.view.View
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
) : RecyclerView.Adapter<SessionViewHolder>(), ItemDragDropSwipeAdapter {

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
        holder.textName.tag = session.name
        holder.textName.text = session.name
        holder.textTabCount.text = holder.tabCountLabel()

        if (iEditModeEnabledObservable.value == true) {
                holder.buttonEdit.visibility = View.VISIBLE
                holder.buttonDelete.visibility = View.VISIBLE
            } else {
                holder.buttonEdit.visibility = View.GONE
                holder.buttonDelete.visibility = View.GONE
            }

        // Set item font style according to current session
        if (session.isCurrent) {
            TextViewCompat.setTextAppearance(holder.textName, R.style.boldText)
        } else {
            TextViewCompat.setTextAppearance(holder.textName, R.style.normalText)
        }
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
        Collections.swap(uiController.getTabModel().iSessions, fromPosition, toPosition)
        // Tell base class an item was moved
        notifyItemMoved(fromPosition, toPosition)
        // Persist our changes
        uiController.getTabModel().saveSessions()
        return true
    }

    // From ItemTouchHelperAdapter
    override fun onItemDismiss(position: Int)
    {

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
