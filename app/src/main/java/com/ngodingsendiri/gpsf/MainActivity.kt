package com.ngodingsendiri.gpsf

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().cacheMapTileCount = 12.toShort()
        Configuration.getInstance().cacheMapTileOvershoot = 4.toShort()
        
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 28) perms.add(Manifest.permission.FOREGROUND_SERVICE)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())

        setContent {
            MaterialTheme(
                colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                } else {
                    if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                }
            ) {
                GpsfApp()
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

@Composable
fun GpsfApp() {
    val ctx = LocalContext.current
    val sharedPrefs = remember(ctx) { ctx.getSharedPreferences("gpsf_prefs", Context.MODE_PRIVATE) }

    var lat by remember { mutableDoubleStateOf(sharedPrefs.getFloat("lat", -6.2000f).toDouble()) }
    var lng by remember { mutableDoubleStateOf(sharedPrefs.getFloat("lng", 106.8166f).toDouble()) }
    var centerMapTrigger by remember { mutableIntStateOf(0) }
    
    val isRunning by MockLocationService.isRunning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Restore last coordinates from running service if active when app opens
    LaunchedEffect(Unit) {
        if (MockLocationService.isRunning.value) {
            lat = MockLocationService.currentLat.value
            lng = MockLocationService.currentLng.value
            centerMapTrigger++ // Center map on active mock pin automatically on start
        }
    }

    LaunchedEffect(Unit) {
        MockLocationService.errorEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lat = lat,
                lng = lng,
                centerMapTrigger = centerMapTrigger,
                onSelect = { newLat, newLng ->
                    lat = newLat
                    lng = newLng
                    sharedPrefs.edit().putFloat("lat", newLat.toFloat()).putFloat("lng", newLng.toFloat()).apply()
                    if (isRunning) {
                        ctx.startService(Intent(ctx, MockLocationService::class.java).apply {
                            action = "START"
                            putExtra("LAT", lat)
                            putExtra("LNG", lng)
                        })
                    }
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Developer Options Button
                FilledIconButton(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Settings, "Developer Options", tint = MaterialTheme.colorScheme.onSurface)
                }

                // Center on Pin Button
                FilledIconButton(
                    onClick = { centerMapTrigger++ },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Pusatkan ke Pin",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isRunning) {
                                PulsingDot()
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isRunning) "Mocking Aktif" else "Pilih Area",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${"%.5f".format(lat)}, ${"%.5f".format(lng)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Radius 50m (Acak)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isRunning) {
                                ctx.startService(Intent(ctx, MockLocationService::class.java).apply { action = "STOP" })
                            } else {
                                sharedPrefs.edit().putFloat("lat", lat.toFloat()).putFloat("lng", lng.toFloat()).apply()
                                val intent = Intent(ctx, MockLocationService::class.java).apply {
                                    action = "START"
                                    putExtra("LAT", lat)
                                    putExtra("LNG", lng)
                                }
                                ctx.startService(intent)
                            }
                        },
                        containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        AnimatedContent(targetState = isRunning, label = "icon") { running ->
                            Icon(if (running) Icons.Default.Close else Icons.Default.PlayArrow, "Toggle")
                        }
                    }
                }
            }
        }
    }
}

fun getCirclePoints(centerLat: Double, centerLng: Double, radiusMeters: Double): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    val earthRadius = 6378137.0
    for (i in 0..360 step 5) {
        val angle = i * Math.PI / 180.0
        val dx = radiusMeters * cos(angle)
        val dy = radiusMeters * sin(angle)
        val lat = centerLat + (180 / Math.PI) * (dy / earthRadius)
        val lng = centerLng + (180 / Math.PI) * (dx / earthRadius) / cos(centerLat * Math.PI / 180.0)
        points.add(GeoPoint(lat, lng))
    }
    return points
}

@Composable
fun OsmMap(
    modifier: Modifier = Modifier, 
    lat: Double, 
    lng: Double, 
    centerMapTrigger: Int,
    onSelect: (Double, Double) -> Unit
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var circle by remember { mutableStateOf<Polygon?>(null) }
    val isDark = isSystemInDarkTheme()
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Optimize: Cache the 72 point allocations, only recalculate when lat/lng shifts
    val circlePoints = remember(lat, lng) {
        getCirclePoints(lat, lng, 50.0)
    }

    // Handles manual centering requests from the floating center-on-pin action button
    LaunchedEffect(centerMapTrigger) {
        if (centerMapTrigger > 0) {
            mapView?.let {
                it.controller.animateTo(GeoPoint(lat, lng))
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(17.5)

                val pt = GeoPoint(lat, lng)
                controller.setCenter(pt)

                val c = Polygon().apply {
                    points = circlePoints
                    fillPaint.color = android.graphics.Color.parseColor(if(isDark) "#4490CAF9" else "#441976D2")
                    outlinePaint.color = android.graphics.Color.parseColor(if(isDark) "#8890CAF9" else "#881976D2")
                    outlinePaint.strokeWidth = 3f
                }
                overlays.add(c)
                circle = c

                val m = Marker(this).apply {
                    position = pt
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.ic_pin)
                    infoWindow = null
                }
                overlays.add(m)
                marker = m

                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        p?.let {
                            currentOnSelect(it.latitude, it.longitude)
                            controller.animateTo(it) // Smoothly center on selection
                        }
                        return true
                    }
                    override fun longPressHelper(p: GeoPoint?) = false
                }))
                mapView = this
            }
        },
        update = {
            val pt = GeoPoint(lat, lng)
            marker?.position = pt
            circle?.points = circlePoints
            circle?.fillPaint?.color = android.graphics.Color.parseColor(if(isDark) "#4490CAF9" else "#441976D2")
            circle?.outlinePaint?.color = android.graphics.Color.parseColor(if(isDark) "#8890CAF9" else "#881976D2")
            it.invalidate()
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView?.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }
}
