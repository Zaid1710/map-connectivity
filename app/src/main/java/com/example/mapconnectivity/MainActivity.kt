package com.example.mapconnectivity

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
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
import android.opengl.Visibility
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.maps.MapView


/**
 * TODO: !!!!!!!!!!!!!!!!!!!!!!!!!!!!VMMV MODEL VIEW ECC!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 *       PULIZIA CODICE (abbiamo spostato le funzioni relative ai sensori)
 *       ORA CHE L'EXPORT HA UN NOME DIVERSO PER OGNI FILE (timestamp), BISOGNA CANCELLARE I FILE PRIMA CHE DIVENTINO TROPPI
 *       DA VALUTARE: PER ORA SE SI CLICCA SU UN FILE .mapc PORTA A SWAP_ACTIVITY, VALUTARE SE CONTINUARE CON L'IMPLEMENTAZIONE DELL'IMPORTAZIONE AUTOMATICA O MENO
 *       SE ELIMINI UNA MISURA IMPORTATA LA PUOI REIMPORTARE????
 *       AGGIUNGERE COROUTINE PER EVITARE CRASH X TIMEOUT (vedi INIZIO COROUTINE? e FINE COROUTINE?)
 *
 *       BUGS:
 *       A ZOOM MINIMO NON VIENE SPAWNATA LA GRIGLIA (ne su emulatore ne su telefono) - NON LA GESTIAMO
 *       SE UNO PROVA A IMPORTARE DA BLUETOOTH SENZA AVER DATO PRIMA I PERMESSI DA ERRORE - NON RIUSCIAMO A RIPRODURLO
 * */

class MainActivity : AppCompatActivity() {
    private val PERMISSION_INIT = 0
    private val PERMISSION_MEASUREMENTS = 1
    private val PERMISSION_OUTSIDE_MEASUREMENTS = 2
    private val PERMISSION_PERIODIC_FETCH = 3
    private val PERMISSION_BACKGROUND_PERIODIC_FETCH = 4
    private val PERMISSION_AUTOMATIC_FETCH = 5
    private val PERMISSION_NOTIFY = 6

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
    private var lastNotifySwitch: Boolean = false

    private lateinit var periodicFetchService: PeriodicFetchService
    private var bound = false

    private lateinit var loadingText: TextView
    private lateinit var loadingBar: ProgressBar

    private val MODES = arrayOf("LTE", "Wi-Fi", "Suono")
    private lateinit var selectedModeValue: TextView

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

        loadingText = findViewById(R.id.loadingText)
        loadingBar = findViewById(R.id.loadingBar)

        selectedModeValue = findViewById(R.id.selectedModeValue)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val theme = prefs.getString("theme_preference", 0.toString())
        when (theme) {
            "0" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            "1" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            "2" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }


        Log.d("INIZIALIZZAZIONE", "HO INIZIALIZZATO TUTTO, SOPRATTUTTO $map")

        val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION))
//        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
//            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
//        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PERMISSIONS", "LOCATION PERMISSION MISSING")
            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_INIT)
        } else {
            // Tutti i permessi sono stati già concessi
            Log.d("PERMISSIONS", "LOCATION PERMISSION GRANTED")
            map.initLocationListener()
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

        // TODO: INIZIO COROUTINE?
        val auto = intent.getStringExtra("automatic")
        val periodic = intent.getStringExtra("periodic")
        val backgroundPeriodic = intent.getStringExtra("background_periodic")
        val notify = intent.getStringExtra("notify")

        Log.d("AUTOINTENT", auto.toString())
        if (auto == "start") {
            intent.removeExtra("automatic")
            // Controllo permessi
            val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.RECORD_AUDIO))
//            if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
//                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
//            }

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
//                    map.initLocationListener(googleMap, map.AUTOMATIC)
                    map.listenerHandler(googleMap, true)
                }
            }
        } else if (notify == null){
            val preferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this)
            val editor: SharedPreferences.Editor = preferences.edit()
            editor.putBoolean("automatic_fetch", false)
            editor.apply()
        }


        Log.d("PERIODICINTENT", periodic.toString())
        if (periodic == "start") {
            intent.removeExtra("periodic")
            // Controllo permessi
            val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.RECORD_AUDIO))
//            if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
//                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
//            }

            if (permissionsToRequest.isNotEmpty()) {
                // Qualche permesso non è stato dato
                Log.d("PERMISSIONS", "SOMETHING'S MISSING WITH PERIODIC FETCH PERMISSIONS")
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_PERIODIC_FETCH)

            } else {
                // Tutti i permessi sono stati già concessi
                Log.d("PERMISSIONS", "ALL PERIODIC FETCH PERMISSIONS GRANTED")
                val preferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val editor: SharedPreferences.Editor = preferences.edit()
                editor.putBoolean("periodic_fetch", true)
                editor.apply()

//                mapView.getMapAsync { googleMap ->
//                    map.initLocationListener(googleMap, map.PERIODIC)
                map.periodicFetch()
//                }
            }
        } else if (notify == null) {
            val preferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this)
            val editor: SharedPreferences.Editor = preferences.edit()
            editor.putBoolean("periodic_fetch", false)
            editor.apply()
        }

        Log.d("BACKGROUNDPERIODICINTENT", backgroundPeriodic.toString())
        if (backgroundPeriodic == "start") {
            intent.removeExtra("background_periodic")

            // Controllo permessi
            val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.RECORD_AUDIO))
//            if (!checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
//                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
//            }
//            if (!checkPermission(Manifest.permission.FOREGROUND_SERVICE)) {
//                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE)
//            }
//            if (!checkPermission(Manifest.permission.RECORD_AUDIO)) {
//                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
//            }

            if (permissionsToRequest.isNotEmpty()) {
                // Qualche permesso non è stato dato
                Log.d("PERMISSIONS", "SOMETHING'S MISSING WITH PERIODIC FETCH PERMISSIONS")
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_BACKGROUND_PERIODIC_FETCH)

            } else {
                // Tutti i permessi sono stati già concessi
                Log.d("PERMISSIONS", "ALL PERIODIC FETCH PERMISSIONS GRANTED")
                periodicFetchStart()
            }
        } else if (backgroundPeriodic == "stop") {
            intent.removeExtra("background_periodic")
            periodicFetchStop()
        } else if (notify == null) {
//            intent.removeExtra("periodic")
            val preferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this)
            val editor: SharedPreferences.Editor = preferences.edit()
            editor.putBoolean("background_periodic_fetch", false)
            editor.apply()
        }

        // TODO FINE COROUTINE?

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        mode = prefs.getString("mode_preference", 0.toString())!!.toInt()
        selectedModeValue.text = MODES[mode]
        map.loadMap(mode)

        Log.d("PREFERENCES", mode.toString())

        val notifySwitch = prefs.getBoolean("notifyAbsentMeasure", false)

        if (notifySwitch && !lastNotifySwitch) {
            val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.POST_NOTIFICATIONS))
            if (permissionsToRequest.isNotEmpty()) {
                // Qualche permesso non è stato dato
                Log.d("PERMISSIONS", "SOMETHING'S MISSING WITH NOTIFY PERMISSIONS")
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_NOTIFY)

            } else {
                // Tutti i permessi sono stati già concessi
                Log.d("PERMISSIONS", "ALL NOTIFY PERMISSIONS GRANTED")
                val preferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val editor: SharedPreferences.Editor = preferences.edit()
                editor.putBoolean("notifyAbsentMeasure", true)
                editor.apply()
                mapView.getMapAsync { googleMap ->
//                    map.initLocationListener(googleMap, map.NOTIFICATION)
                    map.listenerHandler(googleMap, false)
                }
            }
        }

        lastNotifySwitch = notifySwitch

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
                when (requestCode) {
                    PERMISSION_INIT -> {
                        map.initLocationListener()
                        map.loadMap(mode)
                        initMeasureBtn()
                    }
                    PERMISSION_MEASUREMENTS -> {
                        addMeasurement(false)
                    }
                    PERMISSION_OUTSIDE_MEASUREMENTS -> {
                        addMeasurement(true)
                    }
                    PERMISSION_BACKGROUND_PERIODIC_FETCH -> {
                        periodicFetchStart()
                    }
                    PERMISSION_AUTOMATIC_FETCH -> {
                        val preferences: SharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(this)
                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putBoolean("automatic_fetch", true)
                        editor.apply()

                        mapView.getMapAsync { googleMap ->
//                            map.initLocationListener(googleMap, map.AUTOMATIC)
                            map.listenerHandler(googleMap, true)
                        }
                    }
                    PERMISSION_PERIODIC_FETCH -> {
                        val preferences: SharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(this)
                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putBoolean("periodic_fetch", true)
                        editor.apply()

//                        mapView.getMapAsync { googleMap ->
//                            map.initLocationListener(googleMap, map.PERIODIC)
                            map.periodicFetch()
//                        }
                    }
                    PERMISSION_NOTIFY -> {
                        val preferences: SharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(this)
                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putBoolean("notifyAbsentMeasure", true)
                        editor.apply()
                        mapView.getMapAsync { googleMap ->
//                            map.initLocationListener(googleMap, map.NOTIFICATION)
                            map.listenerHandler(googleMap, false)
                        }
                    }
                }

            } else {
                if (requestCode == PERMISSION_INIT) {
                    val dialogBuilder = AlertDialog.Builder(this, R.style.DialogTheme)
                    dialogBuilder.setTitle("Permessi di posizione mancanti")
                    dialogBuilder.setMessage("L'applicazione ha bisogno dei permessi di posizione. \nPer favore autorizzane l'utilizzo per proseguire.")
                    dialogBuilder.setNegativeButton("Prosegui") { _, _ -> }
                    dialogBuilder.setOnDismissListener {
//                        val permissionsToRequest = mutableListOf<String>()
                        val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION))
//                        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
//                            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
//                        }

                        if (permissionsToRequest.isNotEmpty()) {
                            Log.d("PERMISSIONS", "LOCATION PERMISSION MISSING")
                            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_INIT)
                        } else {
                            // Tutti i permessi sono stati già concessi
                            Log.d("PERMISSIONS", "LOCATION PERMISSION GRANTED")
                            map.loadMap(mode)
                            initMeasureBtn()
                        }
                    }
                    dialogBuilder.create().show()
                }

                if (requestCode == PERMISSION_BACKGROUND_PERIODIC_FETCH) {
                    val preferences: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putBoolean("background_periodic_fetch", false)
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
                    Log.d("CARICA", "Carico, $measureBtn, $measureProgressBar")
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
//            Log.d("DB", measureDao.getAllMeasures().toString())
            withContext(Dispatchers.Main) {
                if (::measureBtn.isInitialized && ::measureProgressBar.isInitialized) {
                    measureBtn.visibility = View.VISIBLE
                    measureProgressBar.visibility = View.GONE
                }
                Toast.makeText(applicationContext, "Misura registrata correttamente", Toast.LENGTH_SHORT).show()
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
//        val permissionsToRequest = mutableListOf<String>()
        val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO))
//        if (!this.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
//            Log.d("MEASUREPERMISSIONS", "Mi manca ACCESS_FINE_LOCATION")
//            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//        if (!this.checkPermission(Manifest.permission.RECORD_AUDIO)) {
//            Log.d("MEASUREPERMISSIONS", "Mi manca RECORD_AUDIO")
//            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
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
        showLoading()
//        measureProgressBar.visibility = View.VISIBLE
        val seconds = PreferenceManager.getDefaultSharedPreferences(this).getString("periodic_fetch_interval", 10.toString())!!.toInt()

        var serviceIntent = Intent(this, PeriodicFetchService::class.java)
        serviceIntent.putExtra("seconds", seconds)
//        this.bindService(serviceIntent, this.serviceConnection, Context.BIND_AUTO_CREATE)
        this.startForegroundService(serviceIntent)
    }

    private fun periodicFetchStop() {
        measureBtn.visibility = View.VISIBLE
//        measureProgressBar.visibility = View.GONE
//        unbindService(serviceConnection)
        this.stopService(Intent(this, PeriodicFetchService::class.java))
        hideLoading()
//        this.stopService(periodicFetchService)

        Log.d("SERVIZIO", "HO INTERROTTO IL SERVIZIO")
    }

    private fun generatePermissionRequest(requests: MutableList<String>) : MutableList<String> {
        val permissionsToRequest = mutableListOf<String>()
        for (request in requests) {
            if (!checkPermission(request)) {
                permissionsToRequest.add(request)
            }
        }

        return permissionsToRequest
    }

    private fun showLoading() {
        mapView.view?.visibility = View.GONE
        loadingText.visibility = View.VISIBLE
        loadingBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        mapView.view?.visibility = View.VISIBLE
        loadingText.visibility = View.GONE
        loadingBar.visibility = View.GONE
    }
}