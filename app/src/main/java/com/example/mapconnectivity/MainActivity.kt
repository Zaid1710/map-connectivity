package com.example.mapconnectivity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.room.Room
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import androidx.lifecycle.Transformations.map
import androidx.preference.PreferenceManager
import androidx.preference.EditTextPreference
import android.text.InputType
import androidx.lifecycle.Transformations.map

/**
 * TODO: !!!!!!!!!!!!!!!!!!!!!!!!!!!!VMMV MODEL VIEW ECC!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *       SISTEMARE GRIGLIA (NON DEVE ESSERE UN FILTRO MA DEVE RESTARE ATTACCATA ALLA MAPPA)
 *       FIX RICHIESTA PERMESSI (riguardare)
 *       PULIZIA CODICE (abbiamo spostato le funzioni relative ai sensori)
 *       VEDERE SE C'E' POSSIBILITA' DI USARE QUALCOS'ALTRO PER AVERE LA TASTIERA NUMERICA (NEI SETTINGS DELLE SOGLIE) (gericoppazzo)
 *       IMPORT / EXPORT DATI
 *       SCANSIONE AUTOMATICA E/O QUANDO SI ENTRA IN UN NUOVO RIQUADRO
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
    private lateinit var measureProgressBar: ProgressBar
    private lateinit var settingsBtn: ImageButton
    private var mode: Int = 0

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
        measureBtn = findViewById(R.id.measureBtn)
        measureProgressBar = findViewById(R.id.measureProgressBar)

        settingsBtn = findViewById(R.id.settingsBtn)

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
            initMeasureBtn()

        }

        settingsBtn.setOnClickListener {
            val settings = Intent(this, SettingsActivity::class.java)
            startActivity(settings)
        }
    }

    override fun onResume() {
        super.onResume()
//        val sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
//        var pressureSensorListener = sensors.PressureSensorListener()
//        sensorManager.registerListener(pressureSensorListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        mode = prefs.getString("mode_preference", 0.toString())!!.toInt()
        map.loadMap(mode)
        Log.d("PREFERENCES", mode.toString())
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
                    map.loadMap(mode)
                    initMeasureBtn()
                } else if (requestCode == PERMISSION_MEASUREMENTS) {
                    getMeasurement()
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

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initMeasureBtn() {
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
                getMeasurement()
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun getMeasurement() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                measureBtn.visibility = View.GONE
                measureProgressBar.visibility = View.VISIBLE
            }
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
            withContext(Dispatchers.Main) {
                measureBtn.visibility = View.VISIBLE
                measureProgressBar.visibility = View.GONE
                mapView?.getMapAsync { googleMap ->
                    map.drawGridOnMap(googleMap, map.DB)
                }
            }
        }
    }

}