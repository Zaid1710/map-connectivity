package com.example.mapconnectivity

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
 *       BUGS:
 *       A ZOOM MINIMO NON VIENE SPAWNATA LA GRIGLIA (ne su emulatore ne su telefono)
 *       NEL TELEFONO LA MISURA CREATA HA UN FUSO ORARIO DIVERSO (QUANDO LO SI GUARDA CLICCANDO SUL QUADRATO)
 *       SU EMULATORE DURANTE BACKGROUND PERIODIC FETCH CRASH PER TIMEOUT SOLO QUANDO SI RIMANE IN MAINACTIVITY
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

    private lateinit var loadingText: TextView
    private lateinit var loadingBar: ProgressBar

    private val MODES = arrayOf("LTE", "Wi-Fi", "Suono")
    private lateinit var selectedMode: TextView
    private lateinit var selectedModeValue: TextView

    private var isMapStarted = false
    private var isPermissionRequested = false

    /**
     * Inizializza il programma e controlla se c'è qualche modalità da eseguire (automatic, periodic o background periodic)
     * */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        isPermissionRequested = false
        User.getUserId(this)

        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        isMapStarted = false
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

        selectedMode = findViewById(R.id.selectedMode)
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

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PERMISSIONS", "LOCATION PERMISSION MISSING")
            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_INIT)
        } else {
            // Tutti i permessi sono stati già concessi
            Log.d("PERMISSIONS", "LOCATION PERMISSION GRANTED")
            initMap(mode)
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
        val periodic = intent.getStringExtra("periodic")
        val backgroundPeriodic = intent.getStringExtra("background_periodic")
        val notify = intent.getStringExtra("notify")

        Log.d("AUTOINTENT", auto.toString())
        if (auto == "start") {
            intent.removeExtra("automatic")
            // Controllo permessi
            val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.RECORD_AUDIO))

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


                map.periodicFetch()
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
            val preferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this)
            val editor: SharedPreferences.Editor = preferences.edit()
            editor.putBoolean("background_periodic_fetch", false)
            editor.apply()
        }

    }

    /**
     * Viene chiamato al resume dell'activity, inizializza il loclistener e controlla se lo switch delle notifiche è attivo
     * */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        mode = prefs.getString("mode_preference", 0.toString())!!.toInt()
        selectedModeValue.text = MODES[mode]

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
                    map.listenerHandler(googleMap, false)
                }

            }
        }

        val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.POST_NOTIFICATIONS))
        if (permissionsToRequest.isNotEmpty()) {
            val preferences: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this)
            val editor: SharedPreferences.Editor = preferences.edit()
            editor.putBoolean("notifyAbsentMeasure", false)
            editor.apply()
        }

        lastNotifySwitch = notifySwitch

    }

    /**
     * Viene chiamata quando si ritorna all'activity e controlla i permessi di posizione
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRestart() {
        super.onRestart()
        val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION))

        if (permissionsToRequest.isEmpty()) {
            initMap(mode)
        } else {
            notifyMissingPermissions("L'applicazione ha bisogno dei permessi di posizione.", true)
        }
    }

    /**
     * Viene chiamata dopo la scelta dell'utente di fornire o meno le autorizzazioni ai permessi necessari
     * @param requestCode Codice della richiesta effettuata
     * @param permissions Permessi della richiesta effettuata (ereditato da super)
     * @param grantResults Array dei permessi accettati e non
     * */
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
                    initMap(mode)
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
                        map.listenerHandler(googleMap, true)
                    }
                }
                PERMISSION_PERIODIC_FETCH -> {
                    val preferences: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putBoolean("periodic_fetch", true)
                    editor.apply()

                    map.periodicFetch()

                }
                PERMISSION_NOTIFY -> {
                    val preferences: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putBoolean("notifyAbsentMeasure", true)
                    editor.apply()
                    mapView.getMapAsync { googleMap ->
                        map.listenerHandler(googleMap, false)
                    }
                }
            }

        } else {
            if (requestCode == PERMISSION_INIT) {
                notifyMissingPermissions("L'applicazione ha bisogno dei permessi di posizione.", true)
            }

            if (requestCode == PERMISSION_BACKGROUND_PERIODIC_FETCH) {
                val preferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val editor: SharedPreferences.Editor = preferences.edit()
                editor.putBoolean("background_periodic_fetch", false)
                editor.apply()
            }

            if (requestCode == PERMISSION_NOTIFY) {
                val preferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val editor: SharedPreferences.Editor = preferences.edit()
                editor.putBoolean("notifyAbsentMeasure", false)
                editor.apply()

                notifyMissingPermissions("Le notifiche sono state disattivate perché l'applicazione ha bisogno dei permessi relativi.", false)
            }

            // Almeno uno dei permessi è stato negato
            Log.d("PERMISSIONS", "ONE OR MORE PERMISSIONS MISSING IN $requestCode")
        }
    }

    /**
     * Viene chiamata all'interruzione dell'activity. Interrompe il listener della posizione
     * */
    override fun onStop() {
        super.onStop()
        map.stopLocationListener()
        isMapStarted = false
    }


    /**
     * Verifica un permesso
     * @param permission Permesso da verificare
     * @return True se è concesso, false altrimenti
     * */
    private fun checkPermission(permission: String): Boolean {
        return (ContextCompat.checkSelfPermission(
                this@MainActivity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    /**
     * Inizializza il bottone per eseguire una misura
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun initMeasureBtn() {
        measureBtn.setOnClickListener {
            manageMeasurePermissions(false)
        }
    }

    /**
     * Effettua una misura e la inserisce nel database
     * @param isOutside Flag per determinare se la posizione dell'utente si trova al di fuori dell'area visualizzata
     * */
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

    /**
     * Chiede i permessi necessari per eseguire una misura e la esegue dopo che sono stati concessi
     * @param isOutside Flag per determinare se la posizione dell'utente si trova al di fuori dell'area visualizzata
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    fun manageMeasurePermissions(isOutside: Boolean) {
        val permissionsToRequest = generatePermissionRequest(mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO))


        if (permissionsToRequest.isNotEmpty()) {
            if (!isPermissionRequested) {
                isPermissionRequested = true
                Log.d("MEASUREPERMISSIONS", "Mi manca qualche permesso per le misure")
                if (isOutside) {
                    this.requestPermissions(
                        permissionsToRequest.toTypedArray(),
                        this.PERMISSION_OUTSIDE_MEASUREMENTS
                    )
                } else {
                    this.requestPermissions(
                        permissionsToRequest.toTypedArray(),
                        this.PERMISSION_MEASUREMENTS
                    )
                }
            } else {
                notifyMissingPermissions("L'applicazione ha bisogno dei permessi del microfono per eseguire una misura.", false)
            }
        } else {
            Log.d("MEASUREPERMISSIONS", "Ho tutti i permessi per le misure")
            CoroutineScope(Dispatchers.IO).launch {
                addMeasurement(isOutside)
            }
        }
    }

    /**
     * Avvia il servizio di background periodic fetch
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun periodicFetchStart() {
        Log.d("SERVIZIO", "STO AVVIANDO IL SERVIZIO")
        measureBtn.visibility = View.GONE
        showLoading()
        val seconds = PreferenceManager.getDefaultSharedPreferences(this).getString("periodic_fetch_interval", 10.toString())!!.toInt()

        val serviceIntent = Intent(this, PeriodicFetchService::class.java)
        serviceIntent.putExtra("seconds", seconds)
        this.startForegroundService(serviceIntent)
    }

    /**
     * Interrompe il servizio di background periodic fetch
     * */
    private fun periodicFetchStop() {
        measureBtn.visibility = View.VISIBLE

        this.stopService(Intent(this, PeriodicFetchService::class.java))
        hideLoading()

        Log.d("SERVIZIO", "HO INTERROTTO IL SERVIZIO")
    }

    /**
     * Data una lista di permessi, filtra quelli già presenti e restituisce una lista con quelli mancanti
     * @param requests Lista di permessi
     * @return Lista di permessi da richiedere
     * */
    private fun generatePermissionRequest(requests: MutableList<String>) : MutableList<String> {
        val permissionsToRequest = mutableListOf<String>()
        for (request in requests) {
            if (!checkPermission(request)) {
                permissionsToRequest.add(request)
            }
        }

        return permissionsToRequest
    }

    /**
     * Mostra la schermata di caricamento (visibile in background periodic fetch)
     * */
    private fun showLoading() {
        mapView.view?.visibility = View.GONE
        selectedMode.visibility = View.GONE
        selectedModeValue.visibility = View.GONE
        loadingText.visibility = View.VISIBLE
        loadingBar.visibility = View.VISIBLE
    }

    /**
     * Nasconde la schermata di caricamento (visibile in background periodic fetch)
     * */
    private fun hideLoading() {
        mapView.view?.visibility = View.VISIBLE
        selectedMode.visibility = View.VISIBLE
        selectedModeValue.visibility = View.VISIBLE
        loadingText.visibility = View.GONE
        loadingBar.visibility = View.GONE
    }

    /**
     * Controlla se la mappa è avviata e inizializza listener e mappa
     * @param mode Modalità di visualizzazione
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun initMap(mode: Int) {
        if (isMapStarted) { return }
        map.initLocationListener()
        map.loadMap(mode)
        isMapStarted = true
    }

    /**
     * Crea e mostra la notifica che allerta l'utente che mancano i permessi e lo indirizza alle impostazioni dell'applicazione per fornirli
     * @param str Testo del dialog
     * @param isInit True se si stanno notificando i permessi iniziali di posizione, false altrimenti
     * */
    private fun notifyMissingPermissions(str: String, isInit: Boolean) {
        val dialogBuilder = AlertDialog.Builder(this, R.style.DialogTheme)
        dialogBuilder.setTitle("Permessi mancanti")
        dialogBuilder.setMessage("$str \nPer favore autorizzane l'utilizzo per proseguire.")
        dialogBuilder.setNegativeButton("Prosegui") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
        if (isInit) {
            dialogBuilder.setOnDismissListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        } else {
            dialogBuilder.setNeutralButton("Chiudi") { _, _ -> }
        }
        dialogBuilder.create().show()
    }
}