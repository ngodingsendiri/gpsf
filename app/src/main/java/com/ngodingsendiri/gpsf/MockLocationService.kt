package com.ngodingsendiri.gpsf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_LAT = "LAT"
        const val EXTRA_LNG = "LNG"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "fake_gps"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        val errorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)

        private val _currentLat = MutableStateFlow(GpsfConstants.DEFAULT_LAT)
        val currentLat = _currentLat.asStateFlow()

        private val _currentLng = MutableStateFlow(GpsfConstants.DEFAULT_LNG)
        val currentLng = _currentLng.asStateFlow()
    }

    private val providers = arrayOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null
    private val locationCache = mutableMapOf<String, Location>()

    @Volatile
    private var targetLat = 0.0

    @Volatile
    private var targetLng = 0.0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, GpsfConstants.DEFAULT_LAT)
                val lng = intent.getDoubleExtra(EXTRA_LNG, GpsfConstants.DEFAULT_LNG)
                promoteToForeground(lat, lng)
                startMocking(lat, lng)
            }
            ACTION_STOP -> {
                stopMocking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (!_isRunning.value) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMocking()
        scope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(lat: Double, lng: Double) {
        val notification = buildNotification(lat, lng)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMocking(lat: Double, lng: Double) {
        targetLat = lat
        targetLng = lng
        _currentLat.value = lat
        _currentLng.value = lng

        if (_isRunning.value) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(lat, lng))
            return
        }

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var hasSecurityException = false
        var providersReady = 0

        for (provider in providers) {
            try {
                lm.removeTestProvider(provider)
            } catch (_: Exception) {
                // Provider may not exist yet.
            }

            try {
                // powerRequirement=1 (LOW), accuracy=1 (FINE)
                @Suppress("WrongConstant")
                lm.addTestProvider(
                    provider,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    1,
                    1
                )
                lm.setTestProviderEnabled(provider, true)
                providersReady++
            } catch (_: SecurityException) {
                hasSecurityException = true
            } catch (_: IllegalArgumentException) {
                try {
                    lm.setTestProviderEnabled(provider, true)
                    providersReady++
                } catch (_: Exception) {
                    // ignore
                }
            } catch (_: Exception) {
                // Ignore non-security setup failures per provider.
            }
        }

        if (hasSecurityException || providersReady == 0) {
            val message = if (hasSecurityException) {
                "Pilih aplikasi ini sebagai Mock Location di Developer Settings"
            } else {
                "Gagal mengaktifkan mock location provider"
            }
            errorEvent.tryEmit(message)
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        _isRunning.value = true
        mockJob?.cancel()
        mockJob = scope.launch {
            while (isActive && _isRunning.value) {
                updateLocation(lm, targetLat, targetLng)
                delay(GpsfConstants.UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopMocking() {
        _isRunning.value = false
        mockJob?.cancel()
        mockJob = null

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        for (provider in providers) {
            try {
                lm.setTestProviderEnabled(provider, false)
            } catch (_: Exception) {
                // Best-effort cleanup.
            }
            try {
                lm.removeTestProvider(provider)
            } catch (_: Exception) {
                // Best-effort cleanup.
            }
        }
        locationCache.clear()
    }

    private fun updateLocation(lm: LocationManager, baseLat: Double, baseLng: Double) {
        val radiusInDegrees = GpsfConstants.JITTER_RADIUS_METERS / 111_320.0
        val w = radiusInDegrees * sqrt(Random.nextDouble())
        val t = 2.0 * PI * Random.nextDouble()
        val randomLat = baseLat + (w * sin(t))
        val cosLat = cos(Math.toRadians(baseLat))
        val safeCosLat = if (abs(cosLat) < 1e-6) 1e-6 else cosLat
        val randomLng = baseLng + ((w * cos(t)) / safeCosLat)

        for (provider in providers) {
            try {
                val loc = locationCache.getOrPut(provider) { Location(provider) }.apply {
                    latitude = randomLat.coerceIn(-90.0, 90.0)
                    longitude = normalizeLongitude(randomLng)
                    altitude = 50.0
                    accuracy = 10f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    speed = 0f
                    bearing = 0f
                    completeForTestProvider()
                    markAsMock()
                }
                lm.setTestProviderLocation(provider, loc)
            } catch (_: Exception) {
                // Provider may have been revoked while running.
            }
        }
    }

    /** Some OEMs reject incomplete Location objects from test providers. */
    private fun Location.completeForTestProvider() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bearingAccuracyDegrees = 0.1f
            verticalAccuracyMeters = 10f
            speedAccuracyMetersPerSecond = 0.1f
        }
    }

    private fun Location.markAsMock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock = true
        } else {
            @Suppress("DEPRECATION")
            extras = (extras ?: Bundle()).apply {
                putBoolean("mockLocation", true)
            }
        }
    }

    private fun normalizeLongitude(lng: Double): Double {
        var x = lng % 360.0
        if (x > 180.0) x -= 360.0
        if (x < -180.0) x += 360.0
        return x
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "gpsf Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifikasi saat mock lokasi aktif"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(lat: Double, lng: Double): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val radius = GpsfConstants.JITTER_RADIUS_METERS.toInt()
        val coordText = String.format(
            Locale.US,
            "Lokasi: %.5f, %.5f (Jitter %dm)",
            lat,
            lng,
            radius
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("gpsf Aktif")
            .setContentText(coordText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}
