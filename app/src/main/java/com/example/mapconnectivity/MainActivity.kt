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
import android.widget.Toast
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
import android.content.SharedPreferences

/**
 * TODO: !!!!!!!!!!!!!!!!!!!!!!!!!!!!VMMV MODEL VIEW ECC!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *       FIX RICHIESTA PERMESSI (riguardare) ESEMPIO: audio non chiede permessi se fa direttamente periodic fetch
 *       PULIZIA CODICE (abbiamo spostato le funzioni relative ai sensori)
 *       ORA CHE L'EXPORT HA UN NOME DIVERSO PER OGNI FILE (timestamp), BISOGNA CANCELLARE I FILE PRIMA CHE DIVENTINO TROPPI
 *       DA VALUTARE: PER ORA SE SI CLICCA SU UN FILE .mapc PORTA A SWAP_ACTIVITY, VALUTARE SE CONTINUARE CON L'IMPLEMENTAZIONE DELL'IMPORTAZIONE AUTOMATICA O MENO
 *       SE ELIMINI UNA MISURA IMPORTATA LA PUOI REIMPORTARE????
 *       VALUTARE SE USARE IL LOCLISTENER PER TUTTO IL PROGETTO E NON SOLO PER PERIODICFETCHSERVICE - NO
 *       MAGARI SEPARARE PERIODIC BACKGROUND E NON
 *       Creare funzione per generare permissionToRequest per pulizia codice
 *
 *       BUGS:
 *       A ZOOM MINIMO NON VIENE SPAWNATA LA GRIGLIA (ne su emulatore ne su telefono) - NON LA GESTIAMO
 *       SE UNO PROVA A IMPORTARE DA BLUETOOTH SENZA AVER DATO PRIMA I PERMESSI DA ERRORE - NON RIUSCIAMO A RIPRODURLO
 *       SE NON DAI PERMESSI DI POSIZIONE ALL'INIZIO CAPITANO ERRORI, MAGARI CHIUDERE L'APP SE NON LI DAI O BLOCCA TUTTO E NOTIFICA LA MANCANZA
 *       GESTIRE PERIODICFETCH (E AUTOMATIC FETCH) ATTIVA AL PRIMO AVVIO
 * */

class MainActivity : AppCompatActivity() {
    private val PERMISSION_INIT = 0
    private val PERMISSION_MEASUREMENTS = 1
    private val PERMISSION_OUTSIDE_MEASUREMENTS = 2
//    val PERMISSION_BT = 3
    private val PERMISSION_PERIODIC_FETCH = 4
    private val PERMISSION_AUTOMATIC_FETCH = 5

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

    private lateinit var periodicFetchService: PeriodicFetchService
    private var bound = false

//    val serviceConnection: ServiceConnection = object : ServiceConnection {
//        override fun onServiceConnected(className: ComponentName?, service: IBinder) {
//            // cast the IBinder and get MyService instance
//            val binder: PeriodicFetchService.LocalBinder = service as PeriodicFetchService.LocalBinder
//            periodicFetchService = binder.getService()
//            bound = true
////            periodicFetchService.setCallbacks(this@MainActivity) // register
//        }
//
//        override fun onServiceDisconnected(arg0: ComponentName?) {
//            bound = false
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

        periodicFetchService = PeriodicFetchService()

        Log.d("INIZIALIZZAZIONE", "HO INIZIALIZZATO TUTTO, SOPRATTUTTO $map")

        val permissionsToRequest = mutableListOf<String>()
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PERMISSIONS", "LOCATION PERMISSION MISSING")
            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_INIT)
        } else {
            // Tutti i permessi sono stati già concessi
            Log.d("PERMISSIONS", "LOCATION PERMISSION GRANTED")
            map.loadMap(mode)
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

        val auto = intent.getStringExtra("automatic")
        if (auto == "start") {
            intent.removeExtra("automatic")
            // Controllo permessi
            val permissionsToRequest = mutableListOf<String>()
            if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }

            if (permissionsToRequest.isNotEmpty()) {
                // Qualche permesso non è stato dato
                Log.d("PERMISSIONS", "SOMETHING'S MISSING WITH AUTOMATIC FETCH PERMISSIONS")
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_AUTOMATIC_FETCH)

            } else {
                // Tutti i permessi sono stati già concessi
                Log.d("PERMISSIONS", "ALL AUTOMATIC FETCH PERMISSIONS GRANTED")
                val preferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val editor: SharedPreferences.Editor = preferences.edit()
                editor.putBoolean("automatic_fetch", true)
                editor.apply()

                mapView.getMapAsync { googleMap ->
                    map.automaticFetch(googleMap)
                }
            }
        } else if (auto == "stop") {
            intent.removeExtra("automatic")
            map.stopAutomaticFetch()
        }

        val periodic = intent.getStringExtra("periodic")
        if (periodic == "start") {
            intent.removeExtra("periodic")
            // Controllo permessi
            val permissionsToRequest = mutableListOf<String>()
            if (!checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!checkPermission(Manifest.permission.FOREGROUND_SERVICE)) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
            }
            if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }

            if (permissionsToRequest.isNotEmpty()) {
                // Qualche permesso non è stato dato
                Log.d("PERMISSIONS", "SOMETHING'S MISSING WITH PERIODIC FETCH PERMISSIONS")
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_PERIODIC_FETCH)

            } else {
                // Tutti i permessi sono stati già concessi
                Log.d("PERMISSIONS", "ALL PERIODIC FETCH PERMISSIONS GRANTED")
                periodicFetchStart()
            }
        } else if (periodic == "stop") {
            intent.removeExtra("periodic")
            periodicFetchStop()
        } else {
            intent.removeExtra("periodic")
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
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
                    addMeasurement(false)
                } else if (requestCode == PERMISSION_OUTSIDE_MEASUREMENTS) {
                    addMeasurement(true)
                } else if (requestCode == PERMISSION_PERIODIC_FETCH) {
                    periodicFetchStart()
                } else if (requestCode == PERMISSION_AUTOMATIC_FETCH) {
                    val preferences: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putBoolean("automatic_fetch", true)
                    editor.apply()

                    mapView.getMapAsync { googleMap ->
                        map.initAutomaticFetch(googleMap)
                    }
                }
            } else {
                if (requestCode == PERMISSION_PERIODIC_FETCH) {
                    val preferences: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putBoolean("periodic_fetch", false)
                    editor.apply()
                }

                // Almeno uno dei permessi è stato negato
                Log.d("PERMISSIONS", "ONE OR MORE PERMISSIONS MISSING IN $requestCode")
            }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
//            unbindService(serviceConnection)
            bound = false
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
            manageMeasurePermissions(false)
        }
    }

//    @RequiresApi(Build.VERSION_CODES.S)
//    fun tmpAddMeasurement(): Measure {
//        Log.d("SERVIZIO", "sono un servizio")
//        val measurements = Measure(
//            timestamp = "sdoigsdg",
//            lat = 23.0,
//            lon = 44.0,
//            lte = 12.3,
//            wifi = 1.6,
//            db = 45.0,
//            user_id = "sdogkjlgksdj",
//            imported = false
//        )
//        CoroutineScope(Dispatchers.IO).launch { measureDao.insertMeasure(measurements) }
//
//        return measurements
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun addMeasurement(isOutside: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (::measureBtn.isInitialized && ::measureProgressBar.isInitialized) {
                    measureBtn.visibility = View.GONE
                    measureProgressBar.visibility = View.VISIBLE
                }
            }

            val measurements = Measure(
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
                if (::measureBtn.isInitialized && ::measureProgressBar.isInitialized) {
                    measureBtn.visibility = View.VISIBLE
                    measureProgressBar.visibility = View.GONE
                }
                Toast.makeText(applicationContext, "Misura fatta", Toast.LENGTH_SHORT).show()
                if (!isOutside) {
                    mapView.getMapAsync { googleMap ->
                        map.deleteGrid()
                        map.drawGridOnMap(googleMap, mode)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun manageMeasurePermissions(isOutside: Boolean) {
        val permissionsToRequest = mutableListOf<String>()
        if (!this.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.d("MEASUREPERMISSIONS", "Mi manca ACCESS_FINE_LOCATION")
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!this.checkPermission(Manifest.permission.RECORD_AUDIO)) {
            Log.d("MEASUREPERMISSIONS", "Mi manca RECORD_AUDIO")
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
//        if (!this.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
//            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
//        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MEASUREPERMISSIONS", "Mi manca qualche permesso per le misure")
            if (isOutside) {
                this.requestPermissions(permissionsToRequest.toTypedArray(), this.PERMISSION_OUTSIDE_MEASUREMENTS)
            } else {
                this.requestPermissions(permissionsToRequest.toTypedArray(), this.PERMISSION_MEASUREMENTS)
            }
        } else {
            Log.d("MEASUREPERMISSIONS", "Ho tutti i permessi per le misure")
            CoroutineScope(Dispatchers.IO).launch {
                addMeasurement(isOutside)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun periodicFetchStart() {
        Log.d("SERVIZIO", "STO AVVIANDO IL SERVIZIO")
        measureBtn.visibility = View.GONE
        measureProgressBar.visibility = View.VISIBLE
        val seconds = PreferenceManager.getDefaultSharedPreferences(this).getString("periodic_fetch_interval", 10.toString())!!.toInt()

        var serviceIntent = Intent(this, PeriodicFetchService::class.java)
        serviceIntent.putExtra("seconds", seconds)
//        this.bindService(serviceIntent, this.serviceConnection, Context.BIND_AUTO_CREATE)
        this.startForegroundService(serviceIntent)
    }

    private fun periodicFetchStop() {
        measureBtn.visibility = View.VISIBLE
        measureProgressBar.visibility = View.GONE
//        unbindService(serviceConnection)
        this.stopService(Intent(this, PeriodicFetchService::class.java))
//        this.stopService(periodicFetchService)

        Log.d("SERVIZIO", "HO INTERROTTO IL SERVIZIO")
    }

}