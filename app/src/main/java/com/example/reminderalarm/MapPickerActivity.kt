package com.example.reminderalarm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.reminderalarm.databinding.ActivityMapPickerBinding
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Full-screen map picker with clean road-only CartoDB tiles and
 * Nominatim address autocomplete. Tap to place a pin, or type an
 * address and pick from the dropdown suggestions.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private var marker: Marker? = null

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autocompleteDebounce = Runnable { fetchSuggestions() }

    /** Nominatim results: display name → GeoPoint. */
    private val suggestions = mutableListOf<Pair<String, GeoPoint>>()
    private lateinit var suggestionsAdapter: ArrayAdapter<String>

    // Clean road-only tiles (CartoDB Positron — light gray, no terrain).
    private val cartoPositron = XYTileSource(
        "CartoDB-Positron", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Map setup — clean tiles, multi-touch.
        binding.map.setTileSource(cartoPositron)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(15.0)

        val initLat = intent.getDoubleExtra(EXTRA_LAT, 52.2297)
        val initLng = intent.getDoubleExtra(EXTRA_LNG, 21.0122)
        val startPoint = GeoPoint(initLat, initLng)
        binding.map.controller.setCenter(startPoint)

        if (intent.hasExtra(EXTRA_LAT) && intent.hasExtra(EXTRA_LNG)) {
            placeMarker(startPoint)
        }

        // Tap to place pin.
        binding.map.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val point = mapView.projection
                    .fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                placeMarker(point)
                return true
            }
        })

        // Autocomplete search (Nominatim).
        suggestionsAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>()
        )
        binding.searchInput.setAdapter(suggestionsAdapter)
        binding.searchInput.threshold = 3

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                mainHandler.removeCallbacks(autocompleteDebounce)
                if ((s?.length ?: 0) >= 3) {
                    mainHandler.postDelayed(autocompleteDebounce, 500)
                }
            }
        })

        binding.searchInput.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (position < suggestions.size) {
                    val (_, point) = suggestions[position]
                    binding.map.controller.animateTo(point)
                    binding.map.controller.setZoom(17.0)
                    placeMarker(point)
                }
            }

        binding.btnSearch.setOnClickListener { fetchSuggestions() }

        // Confirm button.
        binding.btnConfirm.setOnClickListener {
            val m = marker ?: return@setOnClickListener
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_LAT, m.position.latitude)
                putExtra(EXTRA_LNG, m.position.longitude)
            })
            finish()
        }

        if (!intent.hasExtra(EXTRA_LAT) && hasLocationPermission()) {
            centreOnGps()
        }
    }

    override fun onResume() { super.onResume(); binding.map.onResume() }
    override fun onPause() { super.onPause(); binding.map.onPause() }

    // ------------------------------------------------------------------

    private fun placeMarker(point: GeoPoint) {
        marker?.let { binding.map.overlays.remove(it) }
        marker = Marker(binding.map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = String.format("%.5f, %.5f", point.latitude, point.longitude)
        }
        binding.map.overlays.add(marker)
        binding.map.invalidate()
        binding.coordsLabel.text =
            String.format("%.5f, %.5f", point.latitude, point.longitude)
        binding.btnConfirm.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // Nominatim autocomplete
    // ------------------------------------------------------------------

    private fun fetchSuggestions() {
        val query = binding.searchInput.text?.toString()?.trim()
        if (query.isNullOrBlank() || query.length < 3) return

        ioExecutor.execute {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = java.net.URL(
                    "https://nominatim.openstreetmap.org/search" +
                        "?format=json&q=$encoded&limit=5&addressdetails=1"
                )
                val conn = url.openConnection().apply {
                    setRequestProperty("User-Agent", packageName)
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val json = conn.getInputStream().bufferedReader().readText()
                val arr = JSONArray(json)
                val results = mutableListOf<Pair<String, GeoPoint>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("display_name", "")
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)
                    if (name.isNotBlank() && lat != 0.0) {
                        results.add(name to GeoPoint(lat, lon))
                    }
                }
                mainHandler.post {
                    suggestions.clear()
                    suggestions.addAll(results)
                    suggestionsAdapter.clear()
                    suggestionsAdapter.addAll(results.map { it.first })
                    suggestionsAdapter.notifyDataSetChanged()
                    if (results.isNotEmpty()) {
                        binding.searchInput.showDropDown()
                    }
                }
            } catch (_: Exception) {
                // Silently ignore network errors — the user can retry.
            }
        }
    }

    // ------------------------------------------------------------------

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun centreOnGps() {
        val fused = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)
        fused.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            null
        ).addOnSuccessListener { loc ->
            if (loc != null) {
                binding.map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
            }
        }
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
    }
}
