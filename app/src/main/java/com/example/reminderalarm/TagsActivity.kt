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

class TagsActivity : BaseActivity() {

    private lateinit var binding: ActivityEntitiesBinding
    private lateinit var adapter: ColoredEntityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.tags_title)
        binding.empty.text = getString(R.string.no_tags)

        applyPaletteColors()

        adapter = ColoredEntityAdapter(
            onClick = { id -> TagStore.byId(this, id)?.let { showEditDialog(it) } },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete)
                    .setMessage(R.string.confirm_delete_tag)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        TagStore.delete(this, id)
                        ReminderStore.clearTagReferences(this, id)
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
        val tags = TagStore.all(this)
        adapter.submit(tags.map { ColoredEntityAdapter.Entry(it.id, it.name, it.color) })
        binding.empty.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(existing: Tag?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_entity_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val colorPreview = view.findViewById<View>(R.id.colorPreview)
        val pickColorBtn = view.findViewById<Button>(R.id.btnPickColor)

        val colorHolder = intArrayOf(existing?.color ?: 0xFF0077B6.toInt())
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
            .setTitle(if (existing == null) R.string.new_tag else R.string.edit_tag)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.err_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val tag = Tag(
                    id = existing?.id ?: System.currentTimeMillis(),
                    name = name,
                    color = colorHolder[0]
                )
                TagStore.save(this, tag)
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
