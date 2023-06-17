package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.VisibleRegion


class Map(mapView: SupportMapFragment?, activity: MainActivity) {
    private var mapView: SupportMapFragment? = mapView
    private var activity: MainActivity = activity
    private val gridLines: MutableList<Polyline> = mutableListOf()

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
                            drawGridOnMap(googleMap)
                        }

                        googleMap.setOnCameraMoveListener {
                            deleteGrid()
                        }
                    }
                }
            }
    }

    private fun drawGridOnMap(googleMap: GoogleMap) {
        val visibleRegion: VisibleRegion = googleMap.projection.visibleRegion
        val topLeft: LatLng = visibleRegion.farLeft
        val bottomRight: LatLng = visibleRegion.nearRight
        val lineWidth = 3f

        val zoom = googleMap.cameraPosition.zoom
        val gridSize = calculateGridSize(zoom)

        val cellWidth = (bottomRight.longitude - topLeft.longitude) / gridSize
        val cellHeight = (topLeft.latitude - bottomRight.latitude) / gridSize

        for (i in 0..gridSize) {
            val lat = topLeft.latitude - i * cellHeight
            val start = LatLng(lat, topLeft.longitude)
            val end = LatLng(lat, bottomRight.longitude)
            val line = googleMap.addPolyline(PolylineOptions().add(start, end).width(lineWidth))
            gridLines.add(line)
        }

        for (i in 0..gridSize) {
            val lng = topLeft.longitude + i * cellWidth * 2
            val start = LatLng(topLeft.latitude, lng)
            val end = LatLng(bottomRight.latitude, lng)
            val line = googleMap.addPolyline(PolylineOptions().add(start, end).width(lineWidth))
            gridLines.add(line)
        }
    }

    private fun calculateGridSize(zoom: Float): Int {
        return (zoom).toInt()
    }

    // Rimuove le linee della griglia precedente dalla mappa, se presenti
    private fun deleteGrid() {
        for (line in gridLines) {
            line.remove()
        }
        gridLines.clear()

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

}
