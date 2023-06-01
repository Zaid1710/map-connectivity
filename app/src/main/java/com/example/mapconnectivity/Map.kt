package com.example.mapconnectivity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.MapsInitializer

class Map : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.onResume()
        mapView.getMapAsync(this)

        try {
            MapsInitializer.initialize(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Puoi personalizzare la mappa come desideri qui
    }

    // Assicurati di gestire i metodi del ciclo di vita della mappa
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}