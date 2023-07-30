package com.example.mapconnectivity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * TODO: !!!!!!!!!!!!!!!!!!!!!!!!!!!!VMMV MODEL VIEW ECC!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *       FIX RICHIESTA PERMESSI (riguardare)
 *       PULIZIA CODICE (abbiamo spostato le funzioni relative ai sensori)
 *       VEDERE SE C'E' POSSIBILITA' DI USARE QUALCOS'ALTRO PER AVERE LA TASTIERA NUMERICA (NEI SETTINGS DELLE SOGLIE) (gericoppazzo)
 *       SCANSIONE AUTOMATICA E/O QUANDO SI ENTRA IN UN NUOVO RIQUADRO
 *       SE CLICCHI SU UN QUADRATO TI FA VEDERE TUTTE E TRE LE MEDIE DELLE MISURE (LTE, WIFI E AUDIO)
 *       ORA CHE L'EXPORT HA UN NOME DIVERSO PER OGNI FILE (timestamp), BISOGNA CANCELLARE I FILE PRIMA CHE DIVENTINO TROPPI
 *       AGGIUNGERE CONTROLLO DELLE SOGLIE A SCELTA - LA OTTIMALE NON PUO' ESSERE MINORE DELLA PESSIMA
 *       OPZIONALE: AGGIUNGERE INFO SULLE MISURE (QUANTE CE NE SONO ECC...)
 *       DA VALUTARE: PER ORA SE SI CLICCA SU UN FILE .mapc PORTA A SWAP_ACTIVITY, VALUTARE SE CONTINUARE CON L'IMPLEMENTAZIONE DELL'IMPORTAZIONE AUTOMATICA O MENO
 *       BUG: A ZOOM MINIMO NON VIENE SPAWNATA LA GRIGLIA (nè su emulatore nè su telefono)
 *       NELLA UPDATELOCATION GESTIRE IL CAMBIAMENTO DI ZOOM
 *       FAI QUADRATO QUANDO NON C'È
 *       FETCH AUTOMATICO OGNI TOT SECONDI
 *       QUANTO SONO GRANDI I QUADRATI QUANDO L'APP È SPENTA? :3
 * */

class MainActivity : AppCompatActivity() {
    private val PERMISSION_INIT = 0
    private val PERMISSION_MEASUREMENTS = 1

    private lateinit var fm: FragmentManager
    private lateinit var mapView: SupportMapFragment
    private lateinit var map: Map
    private lateinit var sensors: Sensor
    private lateinit var measureBtn: Button
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao
    private lateinit var measureProgressBar: ProgressBar
    private lateinit var settingsBtn: ImageButton
    private lateinit var swapBtn: ImageButton
    private var mode: Int = 0

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        User.getUserId(this)

        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        fm = supportFragmentManager
        mapView = fm.findFragmentById(R.id.mapView) as SupportMapFragment
        map = Map(mapView, this)
        sensors = Sensor(this)
        measureBtn = findViewById(R.id.measureBtn)
        measureProgressBar = findViewById(R.id.measureProgressBar)

        settingsBtn = findViewById(R.id.settingsBtn)

        swapBtn = findViewById(R.id.swapBtn)

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

        swapBtn.setOnClickListener {
            val swap = Intent(this, SwapActivity::class.java)
            startActivity(swap)
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
                    addMeasurement()
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
                addMeasurement()
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun addMeasurement() {
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
                db = sensors.fetchMicrophone(),
                user_id = User.getUserId(applicationContext),
                imported = false
            )
            measureDao.insertMeasure(measurements)
            Log.d("MEASURE", measurements.toString())
            Log.d("DB", measureDao.getAllMeasures().toString())
            withContext(Dispatchers.Main) {
                measureBtn.visibility = View.VISIBLE
                measureProgressBar.visibility = View.GONE
                mapView.getMapAsync { googleMap ->
                    map.deleteGrid()
                    map.drawGridOnMap(googleMap, mode)
                }
            }
        }
    }

}