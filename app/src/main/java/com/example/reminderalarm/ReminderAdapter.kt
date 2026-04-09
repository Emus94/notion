package com.example.reminderalarm

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
    private var projectsById: Map<Long, Project> = emptyMap()
    private var tagsById: Map<Long, Tag> = emptyMap()
    private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    fun submit(
        list: List<Reminder>,
        projects: Map<Long, Project> = emptyMap(),
        tags: Map<Long, Tag> = emptyMap()
    ) {
        items.clear()
        items.addAll(list)
        projectsById = projects
        tagsById = tags
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

        val bitmap = ImageLoader.loadSampled(ctx, r.imageUri, 200)
        if (bitmap != null) {
            holder.binding.thumbnail.setImageBitmap(bitmap)
            holder.binding.thumbnail.visibility = View.VISIBLE
        } else {
            holder.binding.thumbnail.setImageBitmap(null)
            holder.binding.thumbnail.visibility = View.GONE
        }

        if (r.notes.isBlank()) {
            holder.binding.notes.visibility = View.GONE
        } else {
            holder.binding.notes.visibility = View.VISIBLE
            holder.binding.notes.text = r.notes
        }

        val project = r.projectId?.let { projectsById[it] }
        if (project != null) {
            holder.binding.projectLabel.visibility = View.VISIBLE
            holder.binding.projectLabel.text = "● ${project.name}"
            holder.binding.projectLabel.setTextColor(project.color)
        } else {
            holder.binding.projectLabel.visibility = View.GONE
        }

        val tags = r.tagIds.mapNotNull { tagsById[it] }
        if (tags.isNotEmpty()) {
            val ssb = SpannableStringBuilder()
            tags.forEachIndexed { i, tag ->
                val start = ssb.length
                ssb.append("#").append(tag.name)
                val end = ssb.length
                ssb.setSpan(
                    ForegroundColorSpan(tag.color),
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (i < tags.size - 1) ssb.append("  ")
            }
            holder.binding.tagsLabel.visibility = View.VISIBLE
            holder.binding.tagsLabel.text = ssb
        } else {
            holder.binding.tagsLabel.visibility = View.GONE
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
