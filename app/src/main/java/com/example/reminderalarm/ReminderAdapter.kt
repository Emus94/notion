package com.example.reminderalarm

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderalarm.databinding.ItemDayHeaderBinding
import com.example.reminderalarm.databinding.ItemReminderBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mixed-list adapter: day-bucket headers ("Dziś", "Jutro", "Ten
 * tydzień", "Później", "Zaległe") interleaved with the reminder
 * cards that belong to each bucket. Grouping happens in
 * [MainActivity] which feeds the adapter already-ordered items.
 */
class ReminderAdapter(
    private val onClick: (Reminder) -> Unit,
    private val onLongPress: (Reminder) -> Unit = {},
    private val onSelectionChanged: (Set<Long>) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Header(val title: String, val count: Int) : Row()
        data class Item(val reminder: Reminder) : Row()
    }

    private val rows = mutableListOf<Row>()
    private var projectsById: Map<Long, Project> = emptyMap()
    private var tagsById: Map<Long, Tag> = emptyMap()
    private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

    // --- selection state ---
    var selectionMode: Boolean = false
        private set
    private val selectedIds = mutableSetOf<Long>()

    fun selectedReminders(): List<Reminder> =
        rows.asSequence()
            .filterIsInstance<Row.Item>()
            .map { it.reminder }
            .filter { it.id in selectedIds }
            .toList()

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
        val idx = rows.indexOfFirst { it is Row.Item && it.reminder.id == id }
        if (idx >= 0) notifyItemChanged(idx)
        onSelectionChanged(selectedIds.toSet())
    }

    fun submit(
        newRows: List<Row>,
        projects: Map<Long, Project> = emptyMap(),
        tags: Map<Long, Tag> = emptyMap()
    ) {
        projectsById = projects
        tagsById = tags
        rows.clear()
        rows.addAll(newRows)
        // Headers aren't DiffUtil-friendly (they carry counts that shift
        // as items move between buckets), so we just re-render the whole
        // list on every submit. The adapter list is always small (hundreds
        // at most) and the animations are nicer than flicker from a bad
        // diff of mixed types.
        notifyDataSetChanged()
    }

    /** Reminder at [position] if it's a reminder row, else null (header). */
    fun getAt(position: Int): Reminder? =
        (rows.getOrNull(position) as? Row.Item)?.reminder

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Item -> TYPE_ITEM
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemDayHeaderBinding.inflate(inflater, parent, false))
        } else {
            VH(ItemReminderBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> bindHeader(holder as HeaderVH, row)
            is Row.Item -> bindItem(holder as VH, row.reminder)
        }
    }

    private fun bindHeader(holder: HeaderVH, row: Row.Header) {
        holder.binding.headerTitle.text = row.title
        holder.binding.headerCount.text = row.count.toString()
    }

    private fun bindItem(holder: VH, r: Reminder) {
        val ctx = holder.itemView.context
        val defaultLabelColor = holder.defaultLabelColor
        holder.binding.label.text = r.label.ifBlank { ctx.getString(R.string.untitled) }
        holder.binding.time.text = fmt.format(Date(r.triggerAtMillis))

        // Thumbnail — async so scrolling never blocks on big JPEGs.
        if (r.imageUri.isNullOrBlank()) {
            holder.binding.thumbnail.tag = null
            holder.binding.thumbnail.setImageBitmap(null)
            holder.binding.thumbnail.visibility = View.GONE
            holder.binding.thumbnail.setOnClickListener(null)
            holder.binding.thumbnail.isClickable = false
        } else {
            holder.binding.thumbnail.visibility = View.VISIBLE
            ImageLoader.loadAsync(ctx, r.imageUri, 200, holder.binding.thumbnail)
            holder.binding.thumbnail.isClickable = true
            holder.binding.thumbnail.setOnClickListener {
                val intent = android.content.Intent(ctx, ImageViewerActivity::class.java)
                    .putExtra(ImageViewerActivity.EXTRA_PATH, r.imageUri)
                ctx.startActivity(intent)
            }
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
        // vibrate / recurrence / location markers.
        val statusParts = mutableListOf<String>()
        if (r.priority != Priority.NORMAL) {
            statusParts += "${r.priority.marker}\u00A0${r.priority.displayName}"
        }
        if (!r.enabled) statusParts += ctx.getString(R.string.done)
        if (r.vibrateOnly) statusParts += ctx.getString(R.string.vibrate_only_tag)
        if (r.isRepeating()) {
            statusParts += "\uD83D\uDD01 ${r.recurrenceDisplayName(ctx)}"
        }
        if (r.isLocationBased()) {
            val name = r.locationName?.takeIf { it.isNotBlank() }
                ?: ctx.getString(R.string.location_tag_generic)
            statusParts += "\uD83D\uDCCD $name"
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

    class HeaderVH(val binding: ItemDayHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
