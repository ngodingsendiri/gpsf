package com.ngodingsendiri.gpsf

/** Shared app constants (UI + mock service stay in sync). */
object GpsfConstants {
    const val DEFAULT_LAT = -6.2000
    const val DEFAULT_LNG = 106.8166
    const val JITTER_RADIUS_METERS = 50.0
    const val UPDATE_INTERVAL_MS = 1000L

    const val PREFS_NAME = "gpsf_prefs"
    const val PREF_LAT = "lat"
    const val PREF_LNG = "lng"
    const val OSM_PREFS = "osmdroid"
}
