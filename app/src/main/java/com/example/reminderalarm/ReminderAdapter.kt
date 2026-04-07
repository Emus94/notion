package com.example.reminderalarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderalarm.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onDelete: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    private val items = mutableListOf<Reminder>()
    private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    fun submit(list: List<Reminder>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.binding.label.text = r.label.ifBlank { holder.itemView.context.getString(R.string.untitled) }
        holder.binding.time.text = fmt.format(Date(r.triggerAtMillis))
        holder.binding.status.text = if (r.enabled) "" else holder.itemView.context.getString(R.string.done)
        holder.binding.btnDelete.setOnClickListener { onDelete(r) }
    }

    class VH(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root)
}
