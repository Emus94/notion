package com.example.reminderalarm

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderalarm.databinding.ItemReminderBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter(
    private val onClick: (Reminder) -> Unit,
    private val onLongPress: (Reminder) -> Unit = {},
    private val onSelectionChanged: (Set<Long>) -> Unit = {}
) : RecyclerView.Adapter<ReminderAdapter.VH>() {

    private val items = mutableListOf<Reminder>()
    private var projectsById: Map<Long, Project> = emptyMap()
    private var tagsById: Map<Long, Tag> = emptyMap()
    private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    // --- selection state ---
    var selectionMode: Boolean = false
        private set
    private val selectedIds = mutableSetOf<Long>()

    fun selectedReminders(): List<Reminder> =
        items.filter { it.id in selectedIds }

    fun enterSelectionMode(initialId: Long) {
        selectionMode = true
        selectedIds.clear()
        selectedIds.add(initialId)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.toSet())
    }

    fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(emptySet())
    }

    fun toggleSelection(id: Long) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx)
        onSelectionChanged(selectedIds.toSet())
    }

    fun submit(
        list: List<Reminder>,
        projects: Map<Long, Project> = emptyMap(),
        tags: Map<Long, Tag> = emptyMap()
    ) {
        val old = items.toList()
        val projectsChanged = projects != projectsById
        val tagsChanged = tags != tagsById

        projectsById = projects
        tagsById = tags
        items.clear()
        items.addAll(list)

        // DiffUtil gives us smooth per-item updates — no flicker on the
        // full list when a single reminder ticks or the sort changes.
        // If the project or tag maps changed we fall back to a full
        // rebind because item content depends on those maps.
        if (projectsChanged || tagsChanged) {
            notifyDataSetChanged()
            return
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = list.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                old[oldItemPosition].id == list[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                old[oldItemPosition] == list[newItemPosition]
        })
        diff.dispatchUpdatesTo(this)
    }

    fun getAt(position: Int): Reminder? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        val ctx = holder.itemView.context
        val defaultLabelColor = holder.defaultLabelColor
        holder.binding.label.text = r.label.ifBlank { ctx.getString(R.string.untitled) }
        holder.binding.time.text = fmt.format(Date(r.triggerAtMillis))

        // Thumbnail
        val bitmap = ImageLoader.loadSampled(ctx, r.imageUri, 200)
        if (bitmap != null) {
            holder.binding.thumbnail.setImageBitmap(bitmap)
            holder.binding.thumbnail.visibility = View.VISIBLE
        } else {
            holder.binding.thumbnail.setImageBitmap(null)
            holder.binding.thumbnail.visibility = View.GONE
        }

        // Notes
        if (r.notes.isBlank()) {
            holder.binding.notes.visibility = View.GONE
        } else {
            holder.binding.notes.visibility = View.VISIBLE
            holder.binding.notes.text = r.notes
        }

        // Project — both the colored left strip AND the text label.
        val project = r.projectId?.let { projectsById[it] }
        if (project != null) {
            holder.binding.colorStrip.setBackgroundColor(project.color)
            holder.binding.projectLabel.visibility = View.VISIBLE
            holder.binding.projectLabel.text = project.name
            holder.binding.projectLabel.setTextColor(project.color)
        } else {
            holder.binding.colorStrip.setBackgroundColor(Color.TRANSPARENT)
            holder.binding.projectLabel.visibility = View.GONE
        }

        // Tags as Material chips (one chip per tag with its own color).
        holder.binding.tagsGroup.removeAllViews()
        val tags = r.tagIds.mapNotNull { tagsById[it] }
        if (tags.isNotEmpty()) {
            holder.binding.tagsGroup.visibility = View.VISIBLE
            tags.forEach { tag ->
                val chip = Chip(ctx).apply {
                    text = tag.name
                    isClickable = false
                    isCheckable = false
                    chipBackgroundColor = ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(tag.color, 0x33)
                    )
                    chipStrokeColor = ColorStateList.valueOf(tag.color)
                    chipStrokeWidth = dp(ctx, 1f)
                    setTextColor(tag.color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    chipMinHeight = dp(ctx, 24f)
                    textStartPadding = dp(ctx, 6f)
                    textEndPadding = dp(ctx, 6f)
                    chipStartPadding = 0f
                    chipEndPadding = 0f
                }
                holder.binding.tagsGroup.addView(chip)
            }
        } else {
            holder.binding.tagsGroup.visibility = View.GONE
        }

        // Status row: priority badge (non-normal only) + completed /
        // vibrate / recurrence markers.
        val statusParts = mutableListOf<String>()
        if (r.priority != Priority.NORMAL) {
            statusParts += "${r.priority.marker}\u00A0${r.priority.displayName}"
        }
        if (!r.enabled) statusParts += ctx.getString(R.string.done)
        if (r.vibrateOnly) statusParts += ctx.getString(R.string.vibrate_only_tag)
        if (r.recurrence != Recurrence.NONE) {
            statusParts += "\uD83D\uDD01 ${r.recurrence.displayName}"
        }
        holder.binding.status.text = statusParts.joinToString("  •  ")
        holder.binding.status.visibility =
            if (statusParts.isEmpty()) View.GONE else View.VISIBLE

        // For HIGH/URGENT, tint the title with the priority color so it
        // jumps out of the list. NORMAL/LOW keep the default text color —
        // the ViewHolder is reusable, so always reset to the label's
        // current theme color for non-priority items.
        if (r.priority == Priority.URGENT || r.priority == Priority.HIGH) {
            holder.binding.label.setTextColor(r.priority.color)
        } else {
            holder.binding.label.setTextColor(defaultLabelColor)
        }

        // Visual feedback for selection: a translucent primary-tint
        // overlay on the card. Using cardBackgroundColor since the root
        // view is a MaterialCardView.
        val card = holder.itemView as? com.google.android.material.card.MaterialCardView
        if (selectionMode && r.id in selectedIds) {
            card?.setCardBackgroundColor(
                ColorUtils.setAlphaComponent(ThemeManager.accentColor(ctx), 0x55)
            )
        } else {
            // Reset to the default cardview surface color
            card?.setCardBackgroundColor(holder.defaultCardColor)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(r.id)
            } else {
                onClick(r)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) onLongPress(r)
            true
        }
    }

    private fun dp(ctx: android.content.Context, v: Float): Float =
        v * ctx.resources.displayMetrics.density

    class VH(val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        // Captured at ViewHolder construction so we can restore them after
        // rebinds that override the label color or card background.
        val defaultLabelColor: Int = binding.label.currentTextColor
        val defaultCardColor: Int =
            (binding.root as? com.google.android.material.card.MaterialCardView)
                ?.cardBackgroundColor?.defaultColor ?: 0
    }
}
