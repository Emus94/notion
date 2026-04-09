package com.example.reminderalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderalarm.databinding.ItemEntityBinding

/**
 * Simple list adapter for anything with (id, name, color, count) — reused by
 * [ProjectsActivity] and [TagsActivity]. The count is shown as a subtitle so
 * the user knows how many reminders are assigned to each project/tag.
 */
class ColoredEntityAdapter(
    private val onClick: (Long) -> Unit,
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<ColoredEntityAdapter.VH>() {

    data class Entry(val id: Long, val name: String, val color: Int, val count: Int = 0)

    private val items = mutableListOf<Entry>()

    fun submit(list: List<Entry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemEntityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val ctx = holder.itemView.context
        holder.binding.name.text = e.name
        holder.binding.colorDot.setBackgroundColor(e.color)
        holder.binding.count.text = ctx.resources.getQuantityString(
            R.plurals.tasks_count, e.count, e.count
        )
        holder.binding.count.visibility = View.VISIBLE
        holder.binding.btnDelete.setOnClickListener { onDelete(e.id) }
        holder.itemView.setOnClickListener { onClick(e.id) }
    }

    class VH(val binding: ItemEntityBinding) : RecyclerView.ViewHolder(binding.root)
}
