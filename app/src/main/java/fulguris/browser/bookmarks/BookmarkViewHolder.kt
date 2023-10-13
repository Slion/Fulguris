/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.browser.bookmarks

import fulguris.R
import fulguris.database.Bookmark
import fulguris.utils.ItemDragDropSwipeViewHolder
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class BookmarkViewHolder(
    itemView: View,
    private val adapter: BookmarksAdapter,
    private val iShowBookmarkMenu: (Bookmark) -> Boolean,
    private val iOpenBookmark: (Bookmark) -> Unit
) : RecyclerView.ViewHolder(itemView),
    ItemDragDropSwipeViewHolder {

    val txtTitle: TextView = itemView.findViewById(R.id.textBookmark)
    val favicon: ImageView = itemView.findViewById(R.id.faviconBookmark)
    private val iButtonEdit: ImageButton = itemView.findViewById(R.id.button_edit)
    private val iCardView: MaterialCardView = itemView.findViewById(R.id.card_view)

    init {
        itemView.setOnClickListener{
            val index = adapterPosition
            if (index.toLong() != RecyclerView.NO_ID) {
                iOpenBookmark(adapter.itemAt(index).bookmark)
            }
        }

        iButtonEdit.setOnClickListener {
            val index = adapterPosition
            if (index.toLong() != RecyclerView.NO_ID) {
                iShowBookmarkMenu(adapter.itemAt(index).bookmark)
            }
        }
    }

    /**
     * Implements [ItemDragDropSwipeViewHolder.onItemOperationStart]
     * Start dragging
     */
    override fun onItemOperationStart() {
        iCardView.isDragged = true
    }

    /**
     * Implements [ItemDragDropSwipeViewHolder.onItemOperationStop]
     * Stop dragging
     */
    override fun onItemOperationStop() {
        iCardView.isDragged = false
    }

}
