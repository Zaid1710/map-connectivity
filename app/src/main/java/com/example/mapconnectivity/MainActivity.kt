package com.example.mapconnectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.room.Room
import com.google.android.gms.maps.SupportMapFragment
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * TODO: FIX RICHIESTA PERMESSI (riguardare)
 *       AGGIORNARE IL COLORE DEL RETTANGOLO DIRETTAMENTE DOPO LA MISURA (SENZA NECESSITA' DI SPOSTARE LA GRIGLIA PER REFRESHARLA)
 *       OSCURARE IL PULSANTE "MISURA" DURANTE UNA MISURAZIONE
 *       PULIZIA CODICE (abbiamo spostato le funzioni relative ai sensori)
 * */

class MainActivity : AppCompatActivity() {

    private val PERMISSION_INIT = 0
    private val PERMISSION_MEASUREMENTS = 1


    private lateinit var fm: FragmentManager
    private lateinit var mapView: SupportMapFragment
    private lateinit var map: Map
    private lateinit var sensors: com.example.mapconnectivity.Sensor
    private lateinit var measureBtn: Button
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

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
                            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                            lat = map.getPosition()?.latitude,
                            lon = map.getPosition()?.longitude,
                            lte = sensors.getLteSignalStrength(),
                            wifi = sensors.fetchWifi(),
                            db = sensors.fetchMicrophone()
                        )
                        measureDao.insertMeasure(measurements)
                        Log.d("MEASURE", measurements.toString())
                        Log.d("DB", measureDao.getAllMeasures().toString())
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
                                    timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                                    lat = map.getPosition()?.latitude,
                                    lon = map.getPosition()?.longitude,
                                    lte = sensors.getLteSignalStrength(),
                                    wifi = sensors.fetchWifi(),
                                    db = sensors.fetchMicrophone()
                                )
                                measureDao.insertMeasure(measurements)
                                Log.d("MEASURE", measurements.toString())
                                Log.d("DB", measureDao.getAllMeasures().toString())
                            }.start()
                        }

                    }
                } else if (requestCode == PERMISSION_MEASUREMENTS) {
                    Thread {
                        var measurements = Measure(
                            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                            lat = map.getPosition()?.latitude,
                            lon = map.getPosition()?.longitude,
                            lte = sensors.getLteSignalStrength(),
                            wifi = sensors.fetchWifi(),
                            db = sensors.fetchMicrophone()
                        )
                        measureDao.insertMeasure(measurements)
                        Log.d("MEASURE", measurements.toString())
                        Log.d("DB", measureDao.getAllMeasures().toString())
                    }.start()
                }
            } else {
                // Almeno uno dei permessi è stato negato
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

}