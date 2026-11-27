package com.example.hardware

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Xml
import androidx.core.content.ContextCompat
import com.example.data.model.RouteWaypoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.InputStream
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.xmlpull.v1.XmlPullParser

data class GpsReading(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val speedMps: Float,
  val timestampMs: Long,
  val isPoorSignal: Boolean,
)

class GpsTrackingManager(context: Context) {
  private val appContext = context.applicationContext
  private val fusedClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(appContext)

  private val _latestReading = MutableStateFlow<GpsReading?>(null)
  val latestReading: StateFlow<GpsReading?> = _latestReading.asStateFlow()

  private val _gpsWarning = MutableStateFlow<String?>(null)
  val gpsWarning: StateFlow<String?> = _gpsWarning.asStateFlow()

  private var locationCallback: LocationCallback? = null
  private var previousAcceptedLocation: Location? = null

  fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      appContext,
      android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(
        appContext,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
  }

  @SuppressLint("MissingPermission")
  fun startTracking(onAcceptedLocation: (GpsReading, deltaMeters: Double) -> Unit) {
    if (!hasLocationPermission()) {
      _gpsWarning.value = "Location permission is required for GPS race tracking."
      return
    }
    stopTracking()
    previousAcceptedLocation = null
    _gpsWarning.value = null

    val request =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
        .setMinUpdateIntervalMillis(1000L)
        .setMinUpdateDistanceMeters(2.0f)
        .build()

    val callback =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          val location = result.lastLocation ?: return
          val accuracy = if (location.hasAccuracy()) location.accuracy else 50f
          val poorSignal = accuracy > MAX_ACCEPTABLE_ACCURACY_M
          if (poorSignal) {
            _gpsWarning.value = "Weak GPS signal (${accuracy.toInt()}m accuracy). Filtering drift."
            _latestReading.value =
              GpsReading(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = accuracy,
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                timestampMs = System.currentTimeMillis(),
                isPoorSignal = true,
              )
            return
          }

          _gpsWarning.value = null
          val prev = previousAcceptedLocation
          var delta = 0.0
          if (prev != null) {
            val rawDist =
              haversineMeters(prev.latitude, prev.longitude, location.latitude, location.longitude)
            val dtSec = ((location.time - prev.time).coerceAtLeast(1000L)) / 1000.0
            val impliedSpeed = rawDist / dtSec
            // Reject sudden GPS drift spikes (> 12.5 m/s = 45 km/h) and sub-noise jitter (< 1.5m)
            if (impliedSpeed > 12.5) {
              _gpsWarning.value = "GPS drift spike filtered."
              return
            }
            if (rawDist >= 1.5) {
              delta = rawDist
              previousAcceptedLocation = location
            }
          } else {
            previousAcceptedLocation = location
          }

          val reading =
            GpsReading(
              latitude = location.latitude,
              longitude = location.longitude,
              accuracyMeters = accuracy,
              speedMps = if (location.hasSpeed()) location.speed else 0f,
              timestampMs = System.currentTimeMillis(),
              isPoorSignal = false,
            )
          _latestReading.value = reading
          onAcceptedLocation(reading, delta)
        }
      }

    locationCallback = callback
    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
  }

  fun stopTracking() {
    locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    locationCallback = null
    previousAcceptedLocation = null
  }

  companion object {
    const val MAX_ACCEPTABLE_ACCURACY_M = 28f
    const val ROUTE_DEVIATION_THRESHOLD_M = 35.0
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val dLat = Math.toRadians(lat2 - lat1)
      val dLon = Math.toRadians(lon2 - lon1)
      val a =
        sin(dLat / 2).pow(2) +
          cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
      val c = 2 * atan2(sqrt(a), sqrt(1 - a))
      return EARTH_RADIUS_M * c
    }

    private const val METERS_PER_DEGREE = EARTH_RADIUS_M * Math.PI / 180.0

    /**
     * Shortest distance (metres) from a point to the segment A-B, using a local flat-earth
     * projection (accurate to well under a metre for route-sized segments).
     */
    fun distanceToSegmentMeters(
      lat: Double,
      lon: Double,
      aLat: Double,
      aLon: Double,
      bLat: Double,
      bLon: Double,
    ): Double {
      val lonScale = METERS_PER_DEGREE * cos(Math.toRadians((aLat + bLat) / 2.0))
      val px = (lon - aLon) * lonScale
      val py = (lat - aLat) * METERS_PER_DEGREE
      val bx = (bLon - aLon) * lonScale
      val by = (bLat - aLat) * METERS_PER_DEGREE
      val lenSq = bx * bx + by * by
      if (lenSq < 1e-6) return haversineMeters(lat, lon, aLat, aLon)
      val t = ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
      val dx = px - t * bx
      val dy = py - t * by
      return sqrt(dx * dx + dy * dy)
    }

    /**
     * Minimum distance from the runner to the route polyline. Measuring to the segments (not
     * just the waypoints) matters: GPX files often have points 50-200 m apart, and a runner
     * mid-segment would otherwise trigger false "off route" warnings.
     */
    fun calculateMinDeviationMeters(
      lat: Double,
      lon: Double,
      route: List<RouteWaypoint>,
    ): Double {
      if (route.isEmpty()) return 0.0
      if (route.size == 1) return haversineMeters(lat, lon, route[0].latitude, route[0].longitude)
      var best = Double.MAX_VALUE
      for (i in 0 until route.lastIndex) {
        val a = route[i]
        val b = route[i + 1]
        val d = distanceToSegmentMeters(lat, lon, a.latitude, a.longitude, b.latitude, b.longitude)
        if (d < best) best = d
      }
      return best
    }

    fun parseGpxOrTcx(inputStream: InputStream): List<RouteWaypoint> {
      val waypoints = mutableListOf<RouteWaypoint>()
      val parser = Xml.newPullParser()
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      parser.setInput(inputStream, null)

      var eventType = parser.eventType
      var currentLat: Double? = null
      var currentLon: Double? = null
      var currentEle = 0.0
      var currentTag = ""

      while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
          XmlPullParser.START_TAG -> {
            currentTag = parser.name.lowercase()
            if (currentTag == "trkpt" || currentTag == "rtept" || currentTag == "wpt") {
              val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
              val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
              if (lat != null && lon != null) {
                currentLat = lat
                currentLon = lon
              }
            }
          }
          XmlPullParser.TEXT -> {
            val text = parser.text?.trim().orEmpty()
            if (text.isNotEmpty()) {
              when (currentTag) {
                "ele", "altitudemeters" -> currentEle = text.toDoubleOrNull() ?: 0.0
                "latitudedegrees" -> currentLat = text.toDoubleOrNull()
                "longitudedegrees" -> currentLon = text.toDoubleOrNull()
              }
            }
          }
          XmlPullParser.END_TAG -> {
            val endTag = parser.name.lowercase()
            if (endTag == "trkpt" || endTag == "rtept" || endTag == "wpt" || endTag == "trackpoint") {
              if (currentLat != null && currentLon != null) {
                waypoints.add(RouteWaypoint(currentLat, currentLon, currentEle))
              }
              currentLat = null
              currentLon = null
              currentEle = 0.0
            }
            currentTag = ""
          }
        }
        eventType = parser.next()
      }
      return waypoints
    }

    fun computeRouteTotalMeters(waypoints: List<RouteWaypoint>): Double {
      if (waypoints.size < 2) return 0.0
      var total = 0.0
      for (i in 0 until waypoints.lastIndex) {
        total +=
          haversineMeters(
            waypoints[i].latitude,
            waypoints[i].longitude,
            waypoints[i + 1].latitude,
            waypoints[i + 1].longitude,
          )
      }
      return total
    }
  }
}
