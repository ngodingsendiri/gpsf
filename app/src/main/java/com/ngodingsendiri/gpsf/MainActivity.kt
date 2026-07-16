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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                MockGpsApp()
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
fun MockGpsApp() {
    var lat by remember { mutableDoubleStateOf(-6.2000) }
    var lng by remember { mutableDoubleStateOf(106.8166) }
    
    val isRunning by MockLocationService.isRunning.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                onSelect = { newLat, newLng ->
                    lat = newLat
                    lng = newLng
                    if (isRunning) {
                        ctx.startService(Intent(ctx, MockLocationService::class.java).apply {
                            action = "START"
                            putExtra("LAT", lat)
                            putExtra("LNG", lng)
                        })
                    }
                }
            )

            FilledIconButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Settings, "Developer Options", tint = MaterialTheme.colorScheme.onSurface)
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
fun OsmMap(modifier: Modifier = Modifier, lat: Double, lng: Double, onSelect: (Double, Double) -> Unit) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var circle by remember { mutableStateOf<Polygon?>(null) }
    val isDark = isSystemInDarkTheme()
    val currentOnSelect by rememberUpdatedState(onSelect)

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
                    points = getCirclePoints(lat, lng, 50.0)
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
                        }
                        return true
                    }
                    override fun longPressHelper(p: GeoPoint?) = false
                }))
                mapView = this
            }
        },
        update = {
            marker?.position = GeoPoint(lat, lng)
            circle?.points = getCirclePoints(lat, lng, 50.0)
            circle?.fillPaint?.color = android.graphics.Color.parseColor(if(isDark) "#4490CAF9" else "#441976D2")
            circle?.outlinePaint?.color = android.graphics.Color.parseColor(if(isDark) "#8890CAF9" else "#881976D2")
            it.invalidate()
        }
    )

    DisposableEffect(Unit) { onDispose { mapView?.onDetach() } }
}
