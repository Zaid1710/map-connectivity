package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class Map(mapView: SupportMapFragment?, activity: MainActivity) {
    private lateinit var location: LatLng
    private var mapView: SupportMapFragment? = mapView
    private var activity: MainActivity = activity

    @SuppressLint("MissingPermission")
    fun loadMap() {
        val mFusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)

        mFusedLocationClient.lastLocation
            .addOnSuccessListener(activity) { location ->
                if (location != null) {
                    Log.d("LOCATION", "LAT: ${location.latitude}, LONG: ${location.longitude}")
                    mapView?.getMapAsync { googleMap ->
                        googleMap.setOnMapLoadedCallback {
                            val latlng = LatLng(location.latitude, location.longitude)
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 16F))

                            googleMap.addMarker(
                                MarkerOptions()
//                                    .title("Posizione rilevata")
                                    .position(latlng)
                            )
                        }


                    }

                }
            }
    }
}
