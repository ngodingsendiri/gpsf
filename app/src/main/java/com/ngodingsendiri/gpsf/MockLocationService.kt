package com.ngodingsendiri.gpsf

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlin.math.*

class MockLocationService : Service() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
        val errorEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

        private val _currentLat = MutableStateFlow(-6.2000)
        val currentLat = _currentLat.asStateFlow()

        private val _currentLng = MutableStateFlow(106.8166)
        val currentLng = _currentLng.asStateFlow()
    }

    private val providers = arrayOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var mockJob: Job? = null
    private val locationCache = mutableMapOf<String, Location>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val lat = intent.getDoubleExtra("LAT", 0.0)
                val lng = intent.getDoubleExtra("LNG", 0.0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, buildNotification(lat, lng), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(1001, buildNotification(lat, lng))
                }
                startMocking(lat, lng)
            }
            "STOP" -> {
                stopMocking()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMocking()
        scope.cancel()
    }

    @kotlin.jvm.Volatile
    private var targetLat = 0.0
    @kotlin.jvm.Volatile
    private var targetLng = 0.0

    private fun startMocking(lat: Double, lng: Double) {
        targetLat = lat
        targetLng = lng
        _currentLat.value = lat
        _currentLng.value = lng

        if (_isRunning.value) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1001, buildNotification(lat, lng))
            return
        }

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        var hasSecurityException = false
        for (provider in providers) {
            try {
                lm.removeTestProvider(provider)
            } catch (_: Exception) {}
            
            try {
                @Suppress("WrongConstant")
                lm.addTestProvider(
                    provider, false, false, false, false, false, true, true, 
                    1, 
                    1
                )
                lm.setTestProviderEnabled(provider, true)
            } catch (e: SecurityException) {
                hasSecurityException = true
            } catch (_: Exception) {}
        }

        if (hasSecurityException) {
            errorEvent.tryEmit("Pilih aplikasi ini sebagai Mock Location di Developer Settings")
            stopSelf()
            return
        }

        _isRunning.value = true
        
        mockJob?.cancel()
        mockJob = scope.launch {
            while (isActive && _isRunning.value) {
                updateLocation(lm, targetLat, targetLng)
                delay(1000)
            }
        }
    }

    private fun stopMocking() {
        _isRunning.value = false
        mockJob?.cancel()
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (provider in providers) {
            try {
                lm.setTestProviderEnabled(provider, false)
                lm.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
    }

    private fun updateLocation(lm: LocationManager, baseLat: Double, baseLng: Double) {
        val radiusInDegrees = 50.0 / 111320.0
        val w = radiusInDegrees * sqrt(Random.nextDouble())
        val t = 2.0 * Math.PI * Random.nextDouble()
        val randomLat = baseLat + (w * sin(t))
        val cosLat = cos(Math.toRadians(baseLat))
        val safeCosLat = if (abs(cosLat) < 0.0001) 0.0001 else cosLat
        val randomLng = baseLng + ((w * cos(t)) / safeCosLat)

        for (provider in providers) {
            try {
                val loc = locationCache.getOrPut(provider) { Location(provider) }.apply {
                    latitude = randomLat
                    longitude = randomLng
                    altitude = 50.0
                    accuracy = 10f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    speed = 0f
                    bearing = 0f
                }
                lm.setTestProviderLocation(provider, loc)
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("fake_gps", "gpsf Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(lat: Double, lng: Double): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = Notification.Builder(this, "fake_gps")
        val coordText = String.format(java.util.Locale.US, "Lokasi: %.5f, %.5f (Jitter 50m)", lat, lng)

        return builder.setContentTitle("gpsf Aktif")
            .setContentText(coordText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
