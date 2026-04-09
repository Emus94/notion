package com.example.reminderalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderalarm.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onDelete: (Reminder) -> Unit,
    private val onClick: (Reminder) -> Unit
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
        val ctx = holder.itemView.context
        holder.binding.label.text = r.label.ifBlank { ctx.getString(R.string.untitled) }
        holder.binding.time.text = fmt.format(Date(r.triggerAtMillis))

        if (r.notes.isBlank()) {
            holder.binding.notes.visibility = View.GONE
        } else {
            holder.binding.notes.visibility = View.VISIBLE
            holder.binding.notes.text = r.notes
        }

        val statusParts = mutableListOf<String>()
        if (!r.enabled) statusParts += ctx.getString(R.string.done)
        if (r.vibrateOnly) statusParts += ctx.getString(R.string.vibrate_only_tag)
        if (r.recurrence != Recurrence.NONE) statusParts += "\uD83D\uDD01 ${r.recurrence.displayName}"
        holder.binding.status.text = statusParts.joinToString(" • ")
        holder.binding.status.visibility =
            if (statusParts.isEmpty()) View.GONE else View.VISIBLE

        holder.binding.btnDelete.setOnClickListener { onDelete(r) }
        holder.itemView.setOnClickListener { onClick(r) }
    }

    class VH(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root)
}
