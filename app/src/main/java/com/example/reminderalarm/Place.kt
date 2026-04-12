package com.example.reminderalarm

/**
 * A user-defined favourite location ("Dom", "Praca", "Biedronka
 * Słowackiego"). Stored in [PlaceStore] and matched against the
 * reminder label by [NaturalDateParser] / [AddReminderActivity] so
 * typing "kup mleko biedronka" auto-attaches the geofence.
 */
data class Place(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f
)
