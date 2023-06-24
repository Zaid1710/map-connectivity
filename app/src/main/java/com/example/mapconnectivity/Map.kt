package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.room.Room
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


class Map(mapView: SupportMapFragment?, activity: MainActivity) {
    private var mapView: SupportMapFragment? = mapView
    private var activity: MainActivity = activity
    private val gridPolygons: MutableList<Polygon> = mutableListOf()

    private lateinit var database: MeasureDB

    private val LTE = 0
    private val WIFI = 1
    private val DB = 2

    private val WIFI_BAD = -75.0
    private val WIFI_OPT = -55.0
    private val LTE_BAD = -95.0
    private val LTE_OPT = -80.0
    private val DB_BAD = -80.0
    private val DB_OPT = -60.0

    private val mFusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)

    @SuppressLint("MissingPermission")
    fun loadMap() {
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
                            drawGridOnMap(googleMap, DB)
                            Log.d("POLIGONOATTUALE", getCurrentPolygon(getPosition()).toString())
                            val currpoly = getCurrentPolygon(getPosition())
                            if (currpoly != null) {
                                currpoly.fillColor = Color.RED
                            }
                        }

                        googleMap.setOnCameraMoveListener {
                            deleteGrid()
                        }
                    }
                }
            }
    }

    private fun drawGridOnMap(googleMap: GoogleMap, mode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            var bounds = withContext(Dispatchers.Main) { googleMap.projection.visibleRegion.latLngBounds }

            val topLeft = bounds.northeast
            val bottomRight = bounds.southwest
            val gridSize = 10

            database = Room.databaseBuilder(activity, MeasureDB::class.java, "measuredb")
                .fallbackToDestructiveMigration().build()
            var measureDao = database.measureDao()

            val cellWidth = (bottomRight.longitude - topLeft.longitude) / gridSize
            val cellHeight = (topLeft.latitude - bottomRight.latitude) / gridSize

            for (i in 0 until gridSize) {
                for (j in 0 until gridSize) {
                    val latLng1 =
                        LatLng(topLeft.latitude - i * cellHeight, topLeft.longitude + j * cellWidth)
                    val latLng2 = LatLng(
                        topLeft.latitude - (i + 1) * cellHeight,
                        topLeft.longitude + j * cellWidth
                    )
                    val latLng3 = LatLng(
                        topLeft.latitude - (i + 1) * cellHeight,
                        topLeft.longitude + (j + 1) * cellWidth
                    )
                    val latLng4 = LatLng(
                        topLeft.latitude - i * cellHeight,
                        topLeft.longitude + (j + 1) * cellWidth
                    )

                    var color = Color.TRANSPARENT

                    val measurements = measureDao.getAvgMeasuresInPolygon(
                        latLng1.latitude,
                        latLng1.longitude,
                        latLng3.latitude,
                        latLng3.longitude
                    )
                    Log.d("BABA", measurements.toString())
                    var avgModeMeasure: Double? = 0.0
                    var lowerBound = 0.0
                    var upperBound = 0.0
                    when (mode) {
                        LTE -> { avgModeMeasure = measurements.avgLte; lowerBound = LTE_BAD; upperBound = LTE_OPT }
                        WIFI -> { avgModeMeasure = measurements.avgWifi; lowerBound = WIFI_BAD; upperBound = WIFI_OPT }
                        DB -> { avgModeMeasure = measurements.avgDb?.times(-1); lowerBound = DB_BAD; upperBound = DB_OPT }
                    }
                    if (avgModeMeasure != null) {
                        color = getQuality(avgModeMeasure, lowerBound, upperBound)
                    }

                    val polygonOptions = PolygonOptions()
                        .add(latLng1, latLng2, latLng3, latLng4)
                        .strokeWidth(2f)
                        .strokeColor(Color.BLACK) // Colore del bordo (nero)
                        .fillColor(color) // Colore di riempimento

                    withContext(Dispatchers.Main) {
                        val polygon = googleMap.addPolygon(polygonOptions)

                        polygon.tag = "Polygon($i,$j)"

                        Log.d("Punticini", polygon.points[0].latitude.toString())
                        gridPolygons.add(polygon)
                    }
                }
            }
        }
    }

    private fun calculateGridSize(zoom: Float): Int {
        return (zoom).toInt()
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

}
