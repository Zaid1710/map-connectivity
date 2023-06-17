package com.example.mapconnectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.gms.maps.SupportMapFragment

/**
 * TODO: FIX RICHIESTA PERMESSI (riguardare)
 *       PULIZIA CODICE (abbiamo spostato le funzioni relative ai sensori)
 * */

class MainActivity : AppCompatActivity() {

    private val PERMISSION_INIT = 0
    private val PERMISSION_MEASUREMENTS = 1

//    private val PRESSURE_BAD_LOW = 500.0
//    private val PRESSURE_BAD_HIGH = 500.0
//    private val PRESSURE_OPT = 1000.0
//    private val WIFI_BAD = -75.0
//    private val WIFI_OPT = -55.0
//    private val LTE_BAD = -95.0
//    private val LTE_OPT = -80.0
//    private val DB_BAD = -80.0
//    private val DB_OPT = -60.0

    private lateinit var fm: FragmentManager
    private lateinit var mapView: SupportMapFragment
    private lateinit var map: Map
    private lateinit var sensors: com.example.mapconnectivity.Sensor
    private lateinit var measureBtn: Button


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fm = supportFragmentManager
        mapView = fm.findFragmentById(R.id.mapView) as SupportMapFragment
        map = Map(mapView, this)
        sensors = Sensor(this)
        measureBtn  = findViewById(R.id.measureBtn)

        val permissionsToRequest = mutableListOf<String>()
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PERMISSIONS", "SOMETHING'S MISSING 1")
            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_INIT)
        } else {
            // Tutti i permessi sono stati già concessi
            // Esegui il resto del programma qui
            Log.d("PERMISSIONS", "ALL PERMISSIONS GRANTED")
            map.loadMap()
            measureBtn.setOnClickListener {
                Log.d("MEASURE", "Sono entrato")
                val permissionsToRequest = mutableListOf<String>()
                if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                }

                if (permissionsToRequest.isNotEmpty()) {
                    Log.d("PERMISSIONS", "SOMETHING'S MISSING 2")
                    requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_MEASUREMENTS)
                } else {
                    Thread {
                        var measurements = Measure(
                            map.getPosition()?.latitude,
                            map.getPosition()?.longitude,
                            sensors.getLteSignalStrength(),
                            sensors.fetchWifi(),
                            sensors.fetchMicrophone()
                        )
                        Log.d("MEASURE", measurements.toString())
                    }.start()
                }

            }
        }


    }

    override fun onResume() {
        super.onResume()

        val sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        var pressureSensorListener = sensors.PressureSensorListener()
        sensorManager.registerListener(pressureSensorListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted) {
                // Tutti i permessi sono stati concessi
                // Esegui il resto del programma qui
                Log.d("PERMISSIONS", "ALL OK")
                if (requestCode == PERMISSION_INIT) {
                    map.loadMap()
                    measureBtn.setOnClickListener {
                        Log.d("MEASURE", "Sono entrato")
                        val permissionsToRequest = mutableListOf<String>()
                        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
                            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                        }

                        if (permissionsToRequest.isNotEmpty()) {
                            Log.d("PERMISSIONS", "SOMETHING'S MISSING 2")
                            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_MEASUREMENTS)
                        } else {
                            Thread {
                                var measurements = Measure(
                                    map.getPosition()?.latitude,
                                    map.getPosition()?.longitude,
                                    sensors.getLteSignalStrength(),
                                    sensors.fetchWifi(),
                                    sensors.fetchMicrophone()
                                )
                                Log.d("MEASURE", measurements.toString())
                            }.start()
                        }

                    }
                } else if (requestCode == PERMISSION_MEASUREMENTS) {
                    Thread {
                        var measurements = Measure(
                            map.getPosition()?.latitude,
                            map.getPosition()?.longitude,
                            sensors.getLteSignalStrength(),
                            sensors.fetchWifi(),
                            sensors.fetchMicrophone()
                        )
                        Log.d("MEASURE", measurements.toString())
                    }.start()
                }
            } else {
                // Almeno uno dei permessi è stato negato
                // Gestisci di conseguenza, ad esempio mostrando un messaggio all'utente
                Log.d("PERMISSIONS", "ONE OR MORE PERMISSIONS MISSING")
            }
    }


    /* Verifica un permesso */
    private fun checkPermission(permission: String): Boolean {
        return (ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    private fun getQuality(value: Double, bad: Double, optimal: Double): Int {
        return if (value <= bad ) {
            Color.rgb(255, 0, 0)    // Rosso
        } else if (value >= optimal) {
            Color.rgb(0, 255, 0)    // Verde
        } else {
            Color.rgb(255, 255, 20) // Giallo
            //            }
        }
    }

}