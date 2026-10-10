package com.example.dhtrailbuilder

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

/**
 * OpenTopoMap - contour lines and hillshading, which matters more for a trail than street
 * detail - with no API key and no account to set up, unlike Google Maps. Usage policy requires
 * attribution (shown as the map's built-in copyright overlay) and a real User-Agent, set once in
 * [initOsmdroid].
 */
private val openTopoMap = XYTileSource(
    "OpenTopoMap",
    0, 17, 256, ".png",
    arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
    "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)"
)

/** Call once before the first [RouteMapView] - sets the tile cache location and a real
 * User-Agent, both expected by OSM's tile usage policy. */
fun initOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = context.packageName
    config.osmdroidBasePath = context.cacheDir.resolve("osmdroid")
    config.osmdroidTileCache = config.osmdroidBasePath.resolve("tiles")
}

/**
 * The recorded path on a real topo map - the geographic counterpart to [RideProfileChart]'s
 * altitude-over-distance view. Needs at least two GPS fixes with a lat/lon (absent in NoGps
 * mode, and on very old saved runs from before this existed).
 */
@Composable
fun RouteMapView(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val geoPoints = remember(points) { points.map { (lat, lon) -> GeoPoint(lat, lon) } }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(220.dp),
        factory = { context ->
            MapView(context).apply {
                setTileSource(openTopoMap)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            val route = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = AndroidColor.rgb(232, 214, 158) // GlowGold - matches the app's accent
                outlinePaint.strokeWidth = 8f
            }
            mapView.overlays.add(route)

            mapView.overlays.add(
                Marker(mapView).apply {
                    position = geoPoints.first()
                    title = "Start"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            )
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = geoPoints.last()
                    title = "End"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            )
            // Required by OSM's tile usage policy - added last so it draws on top.
            mapView.overlays.add(
                CopyrightOverlay(mapView.context).apply {
                    setCopyrightNotice(openTopoMap.copyrightNotice)
                }
            )

            // zoomToBoundingBox needs the MapView already laid out (a width/height), hence
            // post() rather than calling it immediately.
            mapView.post {
                val bounds = BoundingBox.fromGeoPoints(geoPoints)
                // A barely-moving run (stopped almost immediately) gives a near-zero-size box,
                // which zoomToBoundingBox can't fit sensibly - just center on it instead.
                if (bounds.latitudeSpan < 0.0005 && bounds.longitudeSpan < 0.0005) {
                    mapView.controller.setCenter(geoPoints.first())
                } else {
                    // Padded by 20% so the route isn't flush against the edges.
                    mapView.zoomToBoundingBox(bounds.increaseByScale(1.2f), true)
                }
            }
            mapView.invalidate()
        },
        onRelease = { mapView -> mapView.onDetach() }
    )
}
