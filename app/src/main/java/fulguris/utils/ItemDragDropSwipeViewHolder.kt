package fulguris.utils

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Intended to be implemented by your [RecyclerView.ViewHolder] implementation.
 * Provide start and stop notifications whenever this item is moved or swiped.
 * Typically used to change this item view background.
 *
 * See: https://github.com/iPaulPro/Android-ItemTouchHelper-Demo
 */
interface ItemDragDropSwipeViewHolder {
    /**
     * Called when the [ItemTouchHelper] first registers an item as being moved or swiped.
     * Implementations should update the item view to indicate it's active state.
     */
    fun onItemOperationStart()

    /**
     * Called when the [ItemTouchHelper] has completed the move or swipe, and the active item
     * state should be cleared.
     */
    fun onItemOperationStop()
}