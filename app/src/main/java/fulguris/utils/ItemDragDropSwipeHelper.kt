package fulguris.utils
import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * An implementation of [ItemTouchHelper.Callback] that enables basic drag & drop and
 * swipe-to-dismiss. Drag events are automatically started by an item long-press.<br></br>
 *
 * Expects the `RecyclerView.Adapter` to listen for [ ] callbacks and the `RecyclerView.ViewHolder` to implement
 * [ItemDragDropSwipeViewHolder].
 *
 * See: https://github.com/iPaulPro/Android-ItemTouchHelper-Demo
 */
class ItemDragDropSwipeHelper(adapter: ItemDragDropSwipeAdapter,
                              aLongPressDragEnabled: Boolean = true,
                              aSwipeEnabled: Boolean = true,
                                // Define which movements trigger drag
                              aDragFlags: Int = ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                                // Define which movements trigger swipe
                              aSwipeFlags: Int = ItemTouchHelper.END) : ItemTouchHelper.Callback() {
    private val mAdapter: ItemDragDropSwipeAdapter = adapter
    private val iLongPressDragEnabled = aLongPressDragEnabled
    private val iSwipeEnabled = aSwipeEnabled
    private val iDragFlags = aDragFlags
    private val iSwipeFlags = aSwipeFlags


    override fun isLongPressDragEnabled(): Boolean {
        return iLongPressDragEnabled
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return iSwipeEnabled
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Define which movement trigger swipe or drag
        return makeMovementFlags(iDragFlags, iSwipeFlags)
    }

    override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        if (source.itemViewType != target.itemViewType) {
            return false
        }

        // Notify the adapter of the move
        mAdapter.onItemMove(source.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
        // Notify the adapter of the dismissal
        mAdapter.onItemDismiss(viewHolder.adapterPosition)
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // Fade out the view as it is swiped out of the parent's bounds
            val alpha = ALPHA_FULL - Math.abs(dX) / viewHolder.itemView.width.toFloat()
            viewHolder.itemView.alpha = alpha
            viewHolder.itemView.translationX = dX
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        // We only want the active item to change
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder is ItemDragDropSwipeViewHolder) {
                // Let the view holder know that this item is being moved or dragged
                val itemViewHolder: ItemDragDropSwipeViewHolder? = viewHolder as ItemDragDropSwipeViewHolder?
                itemViewHolder?.onItemOperationStart()
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = ALPHA_FULL
        if (viewHolder is ItemDragDropSwipeViewHolder) {
            // Tell the view holder it's time to restore the idle state
            val itemViewHolder: ItemDragDropSwipeViewHolder = viewHolder as ItemDragDropSwipeViewHolder
            itemViewHolder.onItemOperationStop()
        }
    }

    companion object {
        const val ALPHA_FULL = 1.0f
    }

}