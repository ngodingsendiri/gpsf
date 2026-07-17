package com.ngodingsendiri.gpsf

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import java.io.File

/**
 * OpenStreetMap / osmdroid setup that actually works on modern Android:
 * - app-private tile cache (scoped storage)
 * - identifiable User-Agent (OSM blocks library defaults)
 * - HTTPS tile endpoints
 */
object OsmMapConfig {

    /** Primary OSM tiles over HTTPS. */
    val OSM_HTTPS: OnlineTileSourceBase = object : XYTileSource(
        "OSM-HTTPS",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.tile.openstreetmap.org/",
            "https://b.tile.openstreetmap.org/",
            "https://c.tile.openstreetmap.org/"
        ),
        "© OpenStreetMap contributors"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
        }
    }

    /**
     * CartoCDN basemap (also OSM data) — fallback if OSM CDN is flaky.
     * Free for light use; identify with User-Agent.
     */
    val CARTO_VOYAGER: OnlineTileSourceBase = object : XYTileSource(
        "CartoVoyager",
        0,
        20,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        ),
        "© OpenStreetMap contributors © CARTO"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        val base = File(appCtx.cacheDir, "osmdroid").apply { mkdirs() }
        val tiles = File(base, "tiles").apply { mkdirs() }

        Configuration.getInstance().apply {
            // Identifiable UA is required by OSM tile policy (library defaults are blocked).
            userAgentValue =
                "gpsf/${BuildConfig.VERSION_NAME} (${appCtx.packageName})"
            osmdroidBasePath = base
            osmdroidTileCache = tiles
            // Keep a reasonable in-memory + disk cache so pans stay smooth.
            cacheMapTileCount = 24.toShort()
            cacheMapTileOvershoot = 8.toShort()
            tileDownloadThreads = 4.toShort()
            tileFileSystemCacheMaxBytes = 100L * 1024L * 1024L
            tileFileSystemCacheTrimBytes = 80L * 1024L * 1024L
            // Prefer Wi‑Fi but still allow mobile data for tiles.
            isMapTileDownloaderFollowRedirects = true
            load(appCtx, appCtx.getSharedPreferences(GpsfConstants.OSM_PREFS, Context.MODE_PRIVATE))
        }
    }
}
