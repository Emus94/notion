package com.example.reminderalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderalarm.databinding.ActivityEntitiesBinding
import java.util.Locale

/**
 * Manage saved places ("Dom", "Praca", "Biedronka Słowackiego") that
 * can be auto-matched from reminder labels. Each place carries a name
 * and GPS coordinates, which get attached as a geofence when the user
 * types the place name in the reminder editor.
 */
class PlacesActivity : BaseActivity() {

    private lateinit var binding: ActivityEntitiesBinding
    private lateinit var adapter: ColoredEntityAdapter

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingGpsAction?.invoke(granted)
        pendingGpsAction = null
    }
    private var pendingGpsAction: ((Boolean) -> Unit)? = null

    private var mapResultCallback: ((Double, Double) -> Unit)? = null
    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val lat = result.data?.getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0.0) ?: return@registerForActivityResult
            val lng = result.data?.getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0.0) ?: return@registerForActivityResult
            mapResultCallback?.invoke(lat, lng)
        }
        mapResultCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.places_title)
        binding.empty.text = getString(R.string.places_empty)
        binding.emptyIcon.setImageResource(R.drawable.ic_empty_folder)

        applyPaletteColors()

        adapter = ColoredEntityAdapter(
            onClick = { id -> PlaceStore.byId(this, id)?.let { showEditDialog(it) } },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.confirm_delete)
                    .setMessage(R.string.confirm_delete_place)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        PlaceStore.delete(this, id)
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

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val places = PlaceStore.all(this)
        adapter.submit(
            places.map { p ->
                ColoredEntityAdapter.Entry(
                    id = p.id,
                    name = p.name,
                    color = ThemeManager.accentColor(this),
                    subtitle = String.format(
                        Locale.getDefault(),
                        "%.4f, %.4f  •  %d m",
                        p.latitude, p.longitude, p.radiusMeters.toInt()
                    )
                )
            }
        )
        binding.emptyContainer.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditDialog(existing: Place?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_place_edit, null)
        val nameInput = view.findViewById<EditText>(R.id.placeName)
        val coordsText = view.findViewById<TextView>(R.id.placeCoords)
        val btnGps = view.findViewById<Button>(R.id.btnPlaceGps)
        val btnSearch = view.findViewById<Button>(R.id.btnPlaceSearch)

        var workLat: Double? = existing?.latitude
        var workLng: Double? = existing?.longitude

        nameInput.setText(existing?.name.orEmpty())

        fun repaintCoords() {
            coordsText.text = if (workLat != null && workLng != null) {
                String.format("%.5f, %.5f", workLat, workLng)
            } else {
                getString(R.string.location_no_coords)
            }
        }
        repaintCoords()

        btnGps.setOnClickListener {
            ensureLocationPermission { granted ->
                if (!granted) {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
                    return@ensureLocationPermission
                }
                fetchGps { lat, lng ->
                    workLat = lat; workLng = lng
                    repaintCoords()
                }
            }
        }

        btnSearch.setOnClickListener {
            showAddressSearchDialog { lat, lng ->
                workLat = lat; workLng = lng
                repaintCoords()
            }
        }

        val btnMap = view.findViewById<Button>(R.id.btnPlaceMap)
        btnMap.setOnClickListener {
            mapResultCallback = { lat, lng ->
                workLat = lat; workLng = lng
                repaintCoords()
            }
            val intent = android.content.Intent(this, MapPickerActivity::class.java)
            if (workLat != null && workLng != null) {
                intent.putExtra(MapPickerActivity.EXTRA_LAT, workLat!!)
                intent.putExtra(MapPickerActivity.EXTRA_LNG, workLng!!)
            }
            mapPickerLauncher.launch(intent)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_place else R.string.edit_place)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.err_empty_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (workLat == null || workLng == null) {
                    Toast.makeText(this, R.string.err_no_coords, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                PlaceStore.save(this, Place(
                    id = existing?.id ?: System.currentTimeMillis(),
                    name = name,
                    latitude = workLat!!,
                    longitude = workLng!!,
                    radiusMeters = existing?.radiusMeters ?: 150f
                ))
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Address search via Geocoder (no API key needed)
    // ------------------------------------------------------------------

    private fun showAddressSearchDialog(onResult: (Double, Double) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.search_address_hint)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.search_address)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isBlank()) return@setPositiveButton
                geocode(query, onResult)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun geocode(query: String, onResult: (Double, Double) -> Unit) {
        try {
            val gc = Geocoder(this, Locale.getDefault())
            val results = gc.getFromLocationName(query, 1)
            if (!results.isNullOrEmpty()) {
                onResult(results[0].latitude, results[0].longitude)
            } else {
                Toast.makeText(this, R.string.geocode_no_results, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.geocode_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------
    // GPS helpers
    // ------------------------------------------------------------------

    private fun ensureLocationPermission(action: (Boolean) -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) { action(true); return }
        pendingGpsAction = action
        locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun fetchGps(callback: (Double, Double) -> Unit) {
        val fused = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)
        fused.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null
        ).addOnSuccessListener { loc ->
            if (loc != null) callback(loc.latitude, loc.longitude)
            else Toast.makeText(this, R.string.location_fetch_failed, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, R.string.location_fetch_failed, Toast.LENGTH_SHORT).show()
        }
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
