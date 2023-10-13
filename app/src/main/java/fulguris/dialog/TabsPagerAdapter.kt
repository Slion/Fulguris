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

package fulguris.dialog

import fulguris.R
import fulguris.extensions.inflater
import fulguris.list.RecyclerViewStringAdapter
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter

/**
 * Pager adapter instantiate pager items.
 *
 */
class TabsPagerAdapter(
    private val context: Context,
    private val dialog: AlertDialog,
    private val tabs: List<DialogTab>
) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        // Inflate our view from our layout definition
        val view: View = context.inflater.inflate(R.layout.dialog_tab_list, container, false)
        // Populate our list with our items
        val recyclerView = view.findViewById<RecyclerView>(R.id.dialog_list)
        val itemList = tabs[position].iItems.filter(DialogItem::show)
        val adapter = RecyclerViewStringAdapter(itemList, getTitle = { context.getString(this.title) }, getText = {this.text})
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            this.adapter = adapter
            setHasFixedSize(true)
        }

        adapter.onItemClickListener = { item ->
            item.onClick()
            dialog.dismiss()
        }

        container.addView(view)

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View);
    }

    /**
     * See: https://stackoverflow.com/questions/30995446/what-is-the-role-of-isviewfromobject-view-view-object-object-in-fragmentst
     */
    override fun isViewFromObject(aView: View, aObject: Any): Boolean {
        return aView === aObject
    }

    override fun getCount(): Int {
        return tabs.count()
    }

    override fun getPageTitle(position: Int): CharSequence {
        if (tabs[position].title == 0) {
            return ""
        }
        return context.getString(tabs[position].title)
    }

    /**
     * Convert zero-based numbering of tabs into readable numbering of tabs starting at 1.
     *
     * @param position - Zero-based tab position
     * @return Readable tab position
     */
    private fun getReadableTabPosition(position: Int): Int {
        return position + 1
    }
}