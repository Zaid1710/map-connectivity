package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kotlin.math.pow


class Map(mapView: SupportMapFragment?, activity: MainActivity) {
    private var mapView: SupportMapFragment? = mapView
    private var activity: MainActivity = activity
    private val gridPolygons: MutableList<Polygon> = mutableListOf()

    private lateinit var database: MeasureDB

    val LTE = 0
    val WIFI = 1
    val DB = 2

    private var WIFI_BAD = -75.0
    private var WIFI_OPT = -55.0
    private var LTE_BAD = -95.0
    private var LTE_OPT = -80.0
    private var DB_BAD = -80.0
    private var DB_OPT = -60.0

    private val mFusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)

    private val gridInARow = 5.0

    @SuppressLint("MissingPermission")
    fun loadMap(mode: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val manual = prefs.getBoolean("switch_preference_bounds", false)
        if (manual) {
            Log.d("Manual", "Sono manuale")
            WIFI_OPT = prefs.getString("opt_wifi", (-55).toString())?.toDoubleOrNull() ?: -55.0
            WIFI_BAD = prefs.getString("bad_wifi", (-75).toString())?.toDoubleOrNull() ?: -75.0
            LTE_BAD = prefs.getString("bad_lte", (-95).toString())?.toDoubleOrNull() ?: -95.0
            LTE_OPT = prefs.getString("opt_lte", (-80).toString())?.toDoubleOrNull() ?: -80.0
            DB_BAD = -(prefs.getString("bad_db", 80.toString())?.toDoubleOrNull() ?: 80.0)
            DB_OPT = -(prefs.getString("opt_db", 60.toString())?.toDoubleOrNull() ?: 60.0)
            Log.d("Manual", "DB_BAD: $DB_BAD, DB_OPT: $DB_OPT")
        } else {
            WIFI_BAD = -75.0
            WIFI_OPT = -55.0
            LTE_BAD = -95.0
            LTE_OPT = -80.0
            DB_BAD = -80.0
            DB_OPT = -60.0
        }

        mFusedLocationClient.lastLocation
            .addOnSuccessListener(activity) { location ->
                if (location != null) {
                    Log.d("LOCATION", "LAT: ${location.latitude}, LONG: ${location.longitude}")
                    mapView?.getMapAsync { googleMap ->
                        googleMap.uiSettings.isZoomControlsEnabled = true
                        googleMap.isMyLocationEnabled = true
                        googleMap.uiSettings.isMyLocationButtonEnabled = true
                        googleMap.uiSettings.isRotateGesturesEnabled = false

                        googleMap.setOnMapLoadedCallback {
                            val latlng = LatLng(location.latitude, location.longitude)
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 16F))
//                            drawGridOnMap(googleMap)
//                            googleMap.addMarker(
//                                MarkerOptions()
//                                    .title("Posizione rilevata")
//                                    .position(latlng)
//                            )
                        }

                        googleMap.setOnCameraIdleListener {
                            drawGridOnMap(googleMap, mode)
//                            Log.d("POLIGONOATTUALE", getCurrentPolygon(getPosition()).toString())
//                            val currpoly = getCurrentPolygon(getPosition())
//                            if (currpoly != null) {
//                                currpoly.fillColor = Color.RED
//                            }
                        }

                        googleMap.setOnCameraMoveListener {
//                            deleteGrid()
                        }
                    }
                }
            }
    }

    fun drawGridOnMap(googleMap: GoogleMap, mode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val zoom: Float = withContext(Dispatchers.Main) { googleMap.cameraPosition.zoom }

            val bounds =
                withContext(Dispatchers.Main) { googleMap.projection.visibleRegion.latLngBounds }
//            val meters = calculateGridSize(bounds.northeast, bounds.southwest)
            val meters = calculateGridSize(zoom)
            Log.d("METERS", "METERS: $meters, ZOOM: $zoom")
            val tlPoint = generateTopLeftSquare(
                meters,
                bounds.northeast.latitude,
                bounds.southwest.longitude
            ) // tl significa Top Left
            var trPoint = LatLng(tlPoint.latitude + metersToOffset(meters), tlPoint.longitude)
            var blPoint = LatLng(tlPoint.latitude, tlPoint.longitude + metersToOffset(meters))
            var brPoint = LatLng(
                tlPoint.latitude + metersToOffset(meters),
                tlPoint.longitude + metersToOffset(meters)
            )

            val polygonOptions = PolygonOptions()
                .add(tlPoint, trPoint, brPoint, blPoint)
//                .strokeWidth(2f)
                .strokeWidth(5f)
                .strokeColor(Color.BLACK) // Colore del bordo (nero)
                .fillColor(Color.argb(128, 255, 0, 0)) // Colore di riempimento

            withContext(Dispatchers.Main) {
                val polygon = googleMap.addPolygon(polygonOptions)

//                polygon.tag = "Polygon($i,$j)"

//                Log.d("Punticini", polygon.points[0].latitude.toString())
                gridPolygons.add(polygon)
            }
        }
    }


    private fun calculateGridSize(zoom: Float) : Double {
        val meters = 22 * (2.0.pow(-(zoom - 22) - 1.0)) // A partire da 21, ogni volta che lo zoom diminuisce di 1, meters raddoppia
        val cellSize = meters / gridInARow
        return cellSize
    }

    private fun generateTopLeftSquare(meters: Double, lat: Double, lon: Double) : LatLng {
        val offset = metersToOffset(meters)

        return LatLng(ceil(lat / offset) * offset, floor(lon / offset) * offset)
    }

    private fun metersToOffset(meters: Double) : Double {
        return meters / 111111.0
    }

    // Rimuove i poligoni della griglia precedente dalla mappa, se presenti
    private fun deleteGrid() {
        for (polygon in gridPolygons) {
            polygon.remove()
        }
        gridPolygons.clear()
    }

    @SuppressLint("MissingPermission")
    fun getPosition(): Location? {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        // Confronta le due posizioni e restituisci quella più recente
        return if (networkLocation != null && gpsLocation != null) {
            if (networkLocation.time > gpsLocation.time) networkLocation else gpsLocation
        } else {
            networkLocation ?: gpsLocation
        }
    }

    private fun getCurrentPolygon(currentPos: Location?): Polygon? {
        if (currentPos != null) {
            for (polygon in gridPolygons) {
                if (polygon.points[0].latitude >= currentPos.latitude && polygon.points[2].latitude <= currentPos.latitude && polygon.points[0].longitude >= currentPos.longitude && polygon.points[2].longitude <= currentPos.longitude) {
                    return polygon
                }
            }
        }
        return null
    }

    private fun getQuality(value: Double, bad: Double, optimal: Double): Int {
        return if (value <= bad ) {
            Color.argb(90, 255, 0, 0)
        } else if (value >= optimal) {
            Color.argb(90, 0, 255, 0)
        } else {
            Color.argb(90, 255, 255, 0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getScreenDimensions(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        val windowMetrics = windowManager.currentWindowMetrics
        val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.systemBars()
        )
        displayMetrics.widthPixels = windowMetrics.bounds.width() - insets.left - insets.right
        displayMetrics.heightPixels = windowMetrics.bounds.height() - insets.top - insets.bottom

        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        return Pair(screenWidth, screenHeight)
    }

}
