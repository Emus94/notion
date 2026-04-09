package com.example.reminderalarm

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderalarm.databinding.ActivityEntitiesBinding

class ProjectsActivity : BaseActivity() {

    private lateinit var binding: ActivityEntitiesBinding
    private lateinit var adapter: ColoredEntityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.projects_title)
        binding.empty.text = getString(R.string.no_projects)

        applyPaletteColors()

        adapter = ColoredEntityAdapter(
            onClick = { id -> ProjectStore.byId(this, id)?.let { showEditDialog(it) } },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete)
                    .setMessage(R.string.confirm_delete_project)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        ProjectStore.delete(this, id)
                        ReminderStore.clearProjectReferences(this, id)
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

    private fun refresh() {
        val projects = ProjectStore.all(this)
        val counts = ReminderStore.all(this)
            .groupingBy { it.projectId }
            .eachCount()
        adapter.submit(
            projects.map {
                ColoredEntityAdapter.Entry(it.id, it.name, it.color, counts[it.id] ?: 0)
            }
        )
        binding.empty.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(existing: Project?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_entity_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val colorPreview = view.findViewById<View>(R.id.colorPreview)
        val pickColorBtn = view.findViewById<Button>(R.id.btnPickColor)

        val colorHolder = intArrayOf(existing?.color ?: 0xFF1D3557.toInt())
        nameInput.setText(existing?.name.orEmpty())
        colorPreview.setBackgroundColor(colorHolder[0])

        pickColorBtn.setOnClickListener {
            ColorPickerHelper.show(
                this,
                getString(R.string.pick_color),
                colorHolder[0]
            ) { picked ->
                colorHolder[0] = picked
                colorPreview.setBackgroundColor(picked)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_project else R.string.edit_project)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.err_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val project = Project(
                    id = existing?.id ?: System.currentTimeMillis(),
                    name = name,
                    color = colorHolder[0]
                )
                ProjectStore.save(this, project)
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
