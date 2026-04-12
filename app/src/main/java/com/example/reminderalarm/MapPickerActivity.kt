package com.example.reminderalarm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.reminderalarm.databinding.ActivityMapPickerBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.util.Locale

/**
 * Full-screen OpenStreetMap picker. The user taps the map to place a
 * pin, or searches an address via the Geocoder bar at the top. The
 * confirmed coordinates are returned to the calling activity as
 * extras "lat" and "lng".
 *
 * Uses osmdroid (no API key needed) — tiles come from the free
 * Mapnik tile server.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private var marker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid setup — must happen before inflating MapView.
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Map basics
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(15.0)

        // Centre on the initial coords if provided, else default to
        // Warsaw so the user isn't staring at a blank ocean.
        val initLat = intent.getDoubleExtra(EXTRA_LAT, 52.2297)
        val initLng = intent.getDoubleExtra(EXTRA_LNG, 21.0122)
        val startPoint = GeoPoint(initLat, initLng)
        binding.map.controller.setCenter(startPoint)

        // If editing an existing location, show a marker right away.
        if (intent.hasExtra(EXTRA_LAT) && intent.hasExtra(EXTRA_LNG)) {
            placeMarker(startPoint)
        }

        // Tap anywhere on the map → move / place the pin.
        binding.map.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val proj = mapView.projection
                val point = proj.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                placeMarker(point)
                return true
            }
        })

        // Search bar
        binding.btnSearch.setOnClickListener { geocodeSearch() }
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            geocodeSearch(); true
        }

        // Confirm
        binding.btnConfirm.setOnClickListener {
            val m = marker ?: return@setOnClickListener
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_LAT, m.position.latitude)
                putExtra(EXTRA_LNG, m.position.longitude)
            })
            finish()
        }

        // Try to centre on GPS if no initial coords given and we have
        // location permission.
        if (!intent.hasExtra(EXTRA_LAT) && hasLocationPermission()) {
            centreOnGps()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

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

    @Suppress("DEPRECATION")
    private fun geocodeSearch() {
        val query = binding.searchInput.text?.toString()?.trim()
        if (query.isNullOrBlank()) return
        try {
            val gc = Geocoder(this, Locale.getDefault())
            val results = gc.getFromLocationName(query, 1)
            if (!results.isNullOrEmpty()) {
                val r = results[0]
                val point = GeoPoint(r.latitude, r.longitude)
                binding.map.controller.animateTo(point)
                placeMarker(point)
            } else {
                Toast.makeText(this, R.string.geocode_no_results, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.geocode_failed, Toast.LENGTH_SHORT).show()
        }
    }

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
                val point = GeoPoint(loc.latitude, loc.longitude)
                binding.map.controller.animateTo(point)
            }
        }
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
    }
}
