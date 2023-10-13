package fulguris.list

import fulguris.R
import fulguris.extensions.inflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

/**
 * A simple [RecyclerView.Adapter] that displays a [List] of [String].
 */
class RecyclerViewStringAdapter<T>(
    private val listItems: List<T>,
    private val getTitle: T.() -> String,
    private val getText: T.() -> String?
) : RecyclerView.Adapter<SimpleStringViewHolder>() {

    var onItemClickListener: ((T) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleStringViewHolder =
        SimpleStringViewHolder(
            parent.context.inflater.inflate(R.layout.simple_list_item, parent, false)
        )

    override fun getItemCount(): Int = listItems.size

    override fun onBindViewHolder(holder: SimpleStringViewHolder, position: Int) {
        val item = listItems[position]
        holder.title.text = item.getTitle()
        // Check secondary text
        val text = item.getText()
        if (text.isNullOrEmpty()) {
            // Hide secondary text if none provided
            holder.secondary.isVisible = false
        } else {
            holder.secondary.text = text
            // Make sure marquee is working
            holder.secondary.isSelected = true;
        }

        holder.itemView.setOnClickListener { onItemClickListener?.invoke(item) }
    }
}

/**
 * A simple [RecyclerView.ViewHolder] that displays a single text item.
 */
class SimpleStringViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    /**
     * The text to display.
     */
    val title: TextView = view.findViewById(R.id.title_text)

    /**
     * The text to display.
     */
    val secondary: TextView = view.findViewById(R.id.secondary_text)

}
