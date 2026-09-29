package id.tipime.tipistream.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import id.tipime.tipistream.R
import id.tipime.tipistream.model.Category

class CategoryNavAdapter(
    private val categories: ArrayList<Category>?,
    private val onSelected: (Int) -> Unit
) : RecyclerView.Adapter<CategoryNavAdapter.ViewHolder>() {
    lateinit var context: Context
    var selectedPosition = 0

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_category_nav, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories?.get(position)
        holder.textView.text = category?.name
        holder.textView.isActivated = position == selectedPosition
        holder.textView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            setSelected(pos)
            onSelected(pos)
        }
    }

    override fun getItemCount(): Int = categories?.size ?: 0

    fun setSelected(position: Int) {
        if (position < 0 || position >= itemCount) return
        val old = selectedPosition
        selectedPosition = position
        if (old != position) notifyItemChanged(old)
        notifyItemChanged(position)
    }

    fun clear() {
        val size = itemCount
        categories?.clear()
        notifyItemRangeRemoved(0, size)
    }
}
