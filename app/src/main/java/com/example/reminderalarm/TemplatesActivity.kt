package com.example.reminderalarm

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderalarm.databinding.ActivityEntitiesBinding

/**
 * Lists saved reminder templates and lets the user pick one to create a
 * new reminder prefilled with its fields. Adding a template from scratch
 * here creates a minimal skeleton (name + label + notes); the full
 * detail is typically captured by "Zapisz jako szablon" in the reminder
 * editor, which already has recurrence / priority / project / tags set.
 */
class TemplatesActivity : BaseActivity() {

    private lateinit var binding: ActivityEntitiesBinding
    private lateinit var adapter: ColoredEntityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.templates_title)
        binding.empty.text = getString(R.string.templates_empty)
        binding.emptyIcon.setImageResource(R.drawable.ic_empty_template)

        applyPaletteColors()

        adapter = ColoredEntityAdapter(
            onClick = { id ->
                // Tapping a template opens the reminder editor prefilled
                // with that template's fields. The user then picks time
                // and saves as usual.
                val intent = Intent(this, AddReminderActivity::class.java).apply {
                    putExtra(AddReminderActivity.EXTRA_TEMPLATE_ID, id)
                }
                startActivity(intent)
                finish()
            },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete)
                    .setMessage(R.string.confirm_delete_template)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        TemplateStore.delete(this, id)
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.fabAdd.setOnClickListener { showEditDialog(null) }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val templates = TemplateStore.all(this)
        adapter.submit(
            templates.map { t ->
                ColoredEntityAdapter.Entry(
                    id = t.id,
                    name = t.name,
                    color = templateDotColor(t),
                    subtitle = buildSubtitle(t)
                )
            }
        )
        binding.emptyContainer.visibility = if (templates.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * A subtitle line summarising what the template prefills — label
     * preview plus the non-default fields (recurrence, priority).
     */
    private fun buildSubtitle(t: Template): String {
        val parts = mutableListOf<String>()
        if (t.label.isNotBlank()) parts += t.label.take(40)
        if (t.recurrence != Recurrence.NONE) parts += "🔁 ${t.recurrence.displayName}"
        if (t.priority != Priority.NORMAL) parts += "${t.priority.marker} ${t.priority.displayName}"
        if (t.vibrateOnly) parts += getString(R.string.vibrate_only_tag)
        return parts.joinToString(" • ")
    }

    /**
     * The dot color on the left of each row picks the priority color for
     * non-normal priorities, else the project color if assigned, else
     * the theme accent — so the list has visual rhythm without needing a
     * dedicated template color field.
     */
    private fun templateDotColor(t: Template): Int {
        if (t.priority != Priority.NORMAL) return t.priority.color
        val proj = t.projectId?.let { ProjectStore.byId(this, it) }
        if (proj != null) return proj.color
        return ThemeManager.accentColor(this)
    }

    private fun showEditDialog(existing: Template?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_template_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.templateName)
        val labelInput = view.findViewById<EditText>(R.id.templateLabel)
        val notesInput = view.findViewById<EditText>(R.id.templateNotes)

        nameInput.setText(existing?.name.orEmpty())
        labelInput.setText(existing?.label.orEmpty())
        notesInput.setText(existing?.notes.orEmpty())

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_template else R.string.edit_template)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.err_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val clash = TemplateStore.all(this).any { other ->
                    other.id != existing?.id &&
                        other.name.equals(name, ignoreCase = true)
                }
                if (clash) {
                    Toast.makeText(this, R.string.err_name_taken, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val template = Template(
                    id = existing?.id ?: System.currentTimeMillis(),
                    name = name,
                    label = labelInput.text.toString().trim(),
                    notes = notesInput.text.toString().trim(),
                    // Preserve existing detail fields on edit (recurrence,
                    // priority, project, tags, vibrate) — only the basic
                    // text fields are editable in this quick dialog.
                    vibrateOnly = existing?.vibrateOnly ?: false,
                    recurrence = existing?.recurrence ?: Recurrence.NONE,
                    priority = existing?.priority ?: Priority.NORMAL,
                    projectId = existing?.projectId,
                    tagIds = existing?.tagIds ?: emptyList()
                )
                TemplateStore.save(this, template)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyPaletteColors() {
        val primary = ThemeManager.primaryColor(this)
        val primaryDark = ThemeManager.primaryDarkColor(this)
        val accent = ThemeManager.accentColor(this)
        binding.toolbar.setBackgroundColor(primary)
        window.statusBarColor = primaryDark
        binding.fabAdd.backgroundTintList = ColorStateList.valueOf(accent)
    }
}
