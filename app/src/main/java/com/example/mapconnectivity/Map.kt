package com.example.mapconnectivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import kotlin.math.pow

class Map(mapView: SupportMapFragment?, activity: MainActivity) {
    private var mapView: SupportMapFragment? = mapView
    private var activity: MainActivity = activity
    private val gridPolygons: MutableList<Polygon> = mutableListOf()
    private var lastLocation: Location? = null

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
    private lateinit var automaticLocationCallback: LocationCallback
    private lateinit var periodicLocationCallback: LocationCallback

    private val prefs = PreferenceManager.getDefaultSharedPreferences(activity)

    private val gridInARow = 5.0

    private var lastZoomValue: Float = -1.0f
    private var lastAutomatic: Boolean = prefs.getBoolean("automatic_fetch", false)
    private var lastPeriodic: Boolean = prefs.getBoolean("periodic_fetch", false)

    @SuppressLint("MissingPermission")
    fun loadMap(mode: Int) {
        database = Room.databaseBuilder(activity, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()

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
                        }

                        googleMap.setOnCameraIdleListener {
                            Log.d("URLA", "LUP")
                            deleteGrid()
                            drawGridOnMap(googleMap, mode)
                        }
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun drawGridOnMap(googleMap: GoogleMap, mode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val zoom: Float = withContext(Dispatchers.Main) { googleMap.cameraPosition.zoom }

            val bounds = withContext(Dispatchers.Main) { googleMap.projection.visibleRegion.latLngBounds }
            val meters = calculateGridSize(zoom)
            Log.d("METERS", "METERS: $meters, ZOOM: $zoom")

            val tlPoint = generateTopLeftPoint(meters, bounds.northeast.latitude, bounds.southwest.longitude) // tl significa Top Left
            var lastGeneratedPolygon = createPolygon(tlPoint, meters, mode)

            withContext(Dispatchers.Main) {
                val polygon = googleMap.addPolygon(lastGeneratedPolygon)
//                polygon.tag = "Polygon($i,$j)"
                gridPolygons.add(polygon)
            }

            var tr = withContext(Dispatchers.Main) { googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[3])}
            var bl = withContext(Dispatchers.Main) { googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[1])}

            val offset = 500
            val screen = Rect(0 - offset,0 - offset, activity.resources.displayMetrics.widthPixels + offset, activity.resources.displayMetrics.heightPixels + offset)


            while (screen.contains(bl.x, bl.y)) {
                val firstPolygon = lastGeneratedPolygon
                while (screen.contains(tr.x, tr.y)) {
                    lastGeneratedPolygon = createPolygon(lastGeneratedPolygon.points[3], meters, mode)

                    withContext(Dispatchers.Main) {
                        val polygon = googleMap.addPolygon(lastGeneratedPolygon)
                        gridPolygons.add(polygon)
                    }

                    tr = withContext(Dispatchers.Main) {
                        googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[3])
                    }
                }
                lastGeneratedPolygon = createPolygon(firstPolygon.points[1], meters, mode)

                withContext(Dispatchers.Main) {
                    val polygon = googleMap.addPolygon(lastGeneratedPolygon)
                    gridPolygons.add(polygon)
                }

                bl = withContext(Dispatchers.Main) {
                    googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[1])
                }
                tr = withContext(Dispatchers.Main) {
                    googleMap.projection.toScreenLocation(lastGeneratedPolygon.points[3])
                }
            }

            val automatic = prefs.getBoolean("automatic_fetch", false)
            if (zoom != lastZoomValue || automatic != lastAutomatic) {
                withContext(Dispatchers.Main) { automaticFetch(googleMap, meters.toFloat()) }
            }

            val periodic = prefs.getBoolean("periodic_fetch", false)
            if (periodic != lastPeriodic) {
                withContext(Dispatchers.Main) { periodicFetch() }
            }
            lastZoomValue = zoom
            lastAutomatic = automatic
            lastPeriodic = periodic
        }
    }

    private fun calculateGridSize(zoom: Float) : Double {
        val meters = 22 * (2.0.pow(-(zoom - 22) - 1.0)) // A partire da 21, ogni volta che lo zoom diminuisce di 1, meters raddoppia
        val cellSize = meters / gridInARow
        return cellSize
    }

    private fun generateTopLeftPoint(meters: Double, lat: Double, lon: Double) : LatLng {
        val offset = metersToOffset(meters)

        return LatLng(ceil(lat / offset) * offset, floor(lon / offset) * offset)
    }

    private fun metersToOffset(meters: Double) : Double {
        return meters / 111111.0
    }

    // polygon.points = [tl, bl, br, tr]
    private fun createPolygon(tlPoint: LatLng, meters: Double, mode: Int) : PolygonOptions {
        val trPoint = LatLng(tlPoint.latitude, tlPoint.longitude + metersToOffset(meters))
        val blPoint = LatLng(tlPoint.latitude - metersToOffset(meters), tlPoint.longitude)
        val brPoint = LatLng(tlPoint.latitude - metersToOffset(meters), tlPoint.longitude + metersToOffset(meters))

        var color = Color.TRANSPARENT

        val imported = prefs.getBoolean("view_imported", true)

        val measureDao = database.measureDao()
        val measurements = measureDao.getAvgMeasuresInPolygon(
            trPoint.latitude,
            trPoint.longitude,
            blPoint.latitude,
            blPoint.longitude,
            imported
        )

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

        val polygon = PolygonOptions()
            .add(tlPoint, trPoint, brPoint, blPoint)
            .strokeWidth(2f)
            .strokeColor(Color.BLACK) // Colore del bordo (nero)
//            .fillColor(Color.argb(128, 255, 0, 0)) // Colore di riempimento
            .fillColor(color) // Colore di riempimento

        return polygon
    }

    // Rimuove i poligoni della griglia precedente dalla mappa, se presenti
    fun deleteGrid() {
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

        // Confronta le due posizioni e restituisce quella più recente
        return if (networkLocation != null && gpsLocation != null) {
            if (networkLocation.time > gpsLocation.time) networkLocation else gpsLocation
        } else {
            networkLocation ?: gpsLocation
        }
    }

    private fun getPolygon(currentPos: Location?): Polygon? {
        if (currentPos != null) {
            for (polygon in gridPolygons) {
                if (polygon.points[0].latitude >= currentPos.latitude && polygon.points[2].latitude <= currentPos.latitude && polygon.points[0].longitude <= currentPos.longitude && polygon.points[2].longitude >= currentPos.longitude) {
                    return polygon
                }
            }
        }
        return null
    }

    private fun areInTheSamePolygon(pos1: Location?, pos2: Location?): Boolean {
        return getPolygon(pos1)?.equals(getPolygon(pos2)) ?: false
    }

    private fun getQuality(value: Double, bad: Double, optimal: Double): Int {
        return if (value <= bad) {
            Color.argb(90, 255, 0, 0)
        } else if (value >= optimal) {
            Color.argb(90, 0, 255, 0)
        } else {
            Color.argb(90, 255, 255, 0)
        }
    }

    private fun automaticFetch(googleMap: GoogleMap, meters: Float) {

        val automatic = prefs.getBoolean("automatic_fetch", false)

        if (this::automaticLocationCallback.isInitialized) {
            mFusedLocationClient.removeLocationUpdates(automaticLocationCallback)
        }

        automaticLocationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val mLastLocation = locationResult.lastLocation

                if (getPolygon(mLastLocation) == null) {
                    if (mLastLocation != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val newPolygon = createPolygon(
                                generateTopLeftPoint(
                                    meters.toDouble(),
                                    mLastLocation.latitude,
                                    mLastLocation.longitude
                                ), meters.toDouble(), 0
                            )
                            withContext(Dispatchers.Main) {
                                val polygon = googleMap.addPolygon(newPolygon)
                                gridPolygons.add(polygon)
                            }
                        }
                    }
                }
                if (lastLocation != null && !areInTheSamePolygon(mLastLocation, lastLocation)) {
                    val permissionsToRequest = mutableListOf<String>()
                    if (!activity.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (!activity.checkPermission(Manifest.permission.RECORD_AUDIO)) {
                        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                    }

                    if (permissionsToRequest.isNotEmpty()) {
                        Log.d("PERMISSIONS", "SOMETHING'S MISSING 2")
                        activity.requestPermissions(permissionsToRequest.toTypedArray(), activity.PERMISSION_OUTSIDE_MEASUREMENTS)
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            activity.addMeasurement(true)
                        }
                    }
                    Log.d("EHEHE", "HO CAMBIATO QUADRATOZZO")
                }
                lastLocation = mLastLocation
            }
        }

        if (automatic) {
            val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5000).setMinUpdateDistanceMeters(meters/4).build()
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, automaticLocationCallback, Looper.myLooper())
        }
    }

    private fun periodicFetch() {
        val periodic = prefs.getBoolean("periodic_fetch", false)

        if (this::periodicLocationCallback.isInitialized) {
            mFusedLocationClient.removeLocationUpdates(periodicLocationCallback)
        }

        periodicLocationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                val permissionsToRequest = mutableListOf<String>()
                if (!activity.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (!activity.checkPermission(Manifest.permission.RECORD_AUDIO)) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                }

                if (permissionsToRequest.isNotEmpty()) {
                    Log.d("PERMISSIONS", "SOMETHING'S MISSING 2")
                    activity.requestPermissions(permissionsToRequest.toTypedArray(), activity.PERMISSION_MEASUREMENTS)
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        activity.addMeasurement(false)
                    }
                }
            }
        }
        val seconds = prefs.getString("periodic_fetch_interval", 10.toString())!!.toInt()

        if (periodic) {
            val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, (seconds * 1000).toLong()).build()
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, periodicLocationCallback, Looper.myLooper())
        }
    }

}
