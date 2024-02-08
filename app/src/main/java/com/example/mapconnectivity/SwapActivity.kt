package com.example.mapconnectivity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SwapActivity : AppCompatActivity() {
    private lateinit var importBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var importBtBtn: Button
    private lateinit var exportBtBtn: Button
    private lateinit var stopExportBtBtn: Button
    private lateinit var loadingView: FragmentContainerView
    private lateinit var mFragmentManager: FragmentManager
    private lateinit var mFragmentTransaction: FragmentTransaction
    private lateinit var mBundle: Bundle
    private lateinit var loadingFragment: Fragment
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao
    private lateinit var mapper: ObjectMapper
    private lateinit var exportProgressBar: ProgressBar
    private lateinit var importProgressBar: ProgressBar
    private val PERMISSION_BT_RECEIVER = 3
    private val PERMISSION_BT_DISCOVER = 4
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var receiver: BroadcastReceiver
    private var isReceivers: Boolean = true
    private var foundDevices: MutableList<BluetoothDevice?> = mutableListOf()
    private var devicesArrayNames : MutableList<String?> = mutableListOf()
    private var newFoundDevices: MutableList<BluetoothDevice?> = mutableListOf()
    private var newFoundDevicesNames: MutableList<String?> = mutableListOf()
    private lateinit var devicesArrayAdapter: ArrayAdapter<BluetoothDevice>
    private lateinit var devicesArrayNamesAdapter: ArrayAdapter<String?>
    private val bluetoothUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothNAME = "mapConnectivity"
    private lateinit var dialog: Dialog

    private lateinit var prefs : SharedPreferences
    private var discoverableDuration : Int? = 60

    private lateinit var importDescBtn: ImageButton
    private lateinit var exportDescBtn: ImageButton
    private lateinit var btImportDescBtn: ImageButton
    private lateinit var btExportDescBtn: ImageButton

//    private var isPermissionRequested = false


    /**
     * Viene chiamata quando si importa un file
     * */
    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val uri = result.data!!.data!!
                val inputStreamReader = InputStreamReader(contentResolver.openInputStream(uri))
                val bufferedReader = BufferedReader(inputStreamReader)
                val s = bufferedReader.readLine()

                val objectMapper = ObjectMapper()
                val importedMeasures = objectMapper.readValue(s, JsonNode::class.java)

                importData(importedMeasures)
            } catch (e: Exception) {
                Log.e("Import", e.toString())
                val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
                toast.show()
            }
        }
        importBtn.visibility = View.VISIBLE
        importProgressBar.visibility = View.GONE
    }

    /**
     * Viene chiamata all'accensione del Bluetooth
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (isReceivers) {
                Log.d("BLUETOOTH", "sono il ricevitore")
                btReceiveHandler()
            } else {
                Log.d("BLUETOOTH", "sono il sender")
                btDiscoverHandler()
            }

        } else {
            val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    /**
     * Viene chiamato quando viene attivata l'esportazione Bluetooth
     * */
    private val enableDiscoverabilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("BLUETOOTH", result.resultCode.toString())
        if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.d("BLUETOOTH", "Richiesta discoverability cancellata")
            val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
            toast.show()
        } else {
            Log.d("BLUETOOTH", "Discoverability abilitata con successo")

            if (this.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED) {
                Log.d("BLUETOOTH", "Receiver registrato")

                showFragment("Esportazione in corso...", false)
            } else {
                Log.d("BLUETOOTH", "Permesso di posizione negato")
            }
        }
    }

    /**
     * Viene chiamato quando si interrompe l'esportazione Bluetooth
     * */
    private val disableDiscoverabilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
            toast.show()
        } else {
            hideFragment()
        }
    }

    /**
     * Mostra il fragment di esportazione Bluetooth
     * */
    private fun showFragment(newText: String, isImport: Boolean) {
        mBundle = Bundle()
        mBundle.putString("swap", newText)
        mBundle.putBoolean("mode", isImport)
        if (!isImport && discoverableDuration != null) {
            mBundle.putLong("timer", discoverableDuration!!.toLong())
        }
        loadingFragment.arguments = mBundle
        mFragmentTransaction = mFragmentManager.beginTransaction()
        mFragmentTransaction.add(R.id.loadingFragment, loadingFragment).commit()
        Log.d("Bundle", mBundle.toString())

        importBtn.visibility = View.GONE
        exportBtn.visibility = View.GONE
        importBtBtn.visibility = View.GONE
        exportBtBtn.visibility = View.GONE
        importDescBtn.visibility = View.GONE
        exportDescBtn.visibility = View.GONE
        btImportDescBtn.visibility = View.GONE
        btExportDescBtn.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    /**
     * Nasconde il fragment di esportazione Bluetooth
     * */
    fun hideFragment() {
        mFragmentTransaction = mFragmentManager.beginTransaction()
        mFragmentTransaction.remove(loadingFragment).commit()
        loadingView.visibility = View.GONE
        importBtn.visibility = View.VISIBLE
        exportBtn.visibility = View.VISIBLE
        importBtBtn.visibility = View.VISIBLE
        exportBtBtn.visibility = View.VISIBLE
        importDescBtn.visibility = View.VISIBLE
        exportDescBtn.visibility = View.VISIBLE
        btImportDescBtn.visibility = View.VISIBLE
        btExportDescBtn.visibility = View.VISIBLE
    }


    /**
     * Inizializza le variabili, crea i collegamenti tra le funzioni e i bottoni relativi
     * @param savedInstanceState Bundle ereditato da super
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swap)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        discoverableDuration = prefs.getString("discovery_time", 60.toString())?.toIntOrNull() // Considera il doppio del tempo per scomparire, ad esempio se DISCOVERABLE_DURATION=30, il dispositivo verra' nascosto dopo 60 secondi circa (DA VERIFICARE).

//        isPermissionRequested = false

        foundDevices = mutableListOf()
        newFoundDevices = mutableListOf()
        devicesArrayNames = mutableListOf()
        newFoundDevicesNames = mutableListOf()
        devicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, foundDevices)
        devicesArrayNamesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devicesArrayNames)
        Log.d("BLUETOOTH", "Before:")
        for (i in 0 until devicesArrayAdapter.count) {
            val item = devicesArrayAdapter.getItem(i)
            Log.d("BLUETOOTH", "Item $i: $item")
        }
        Log.d("BLUETOOTH", "END")

        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        importBtn = findViewById(R.id.importBtn)
        importProgressBar = findViewById(R.id.importProgressBar)
        exportBtn = findViewById(R.id.exportBtn)
        exportProgressBar = findViewById(R.id.exportProgressBar)

        importBtBtn = findViewById(R.id.importBtBtn)
        exportBtBtn = findViewById(R.id.exportBtBtn)

        stopExportBtBtn = findViewById(R.id.stopExportBtBtn)
        loadingView = findViewById(R.id.loadingFragment)

        mFragmentManager = supportFragmentManager
        mBundle = Bundle()
        loadingFragment = Loading()

        importDescBtn = findViewById(R.id.importDescBtn)
        exportDescBtn = findViewById(R.id.exportDescBtn)
        btImportDescBtn = findViewById(R.id.btImportDescBtn)
        btExportDescBtn = findViewById(R.id.btExportDescBtn)

        importBtn.setOnClickListener {
            launcherImportData()
        }

        exportBtn.setOnClickListener {
            exportData()
        }

        importBtBtn.setOnClickListener {
            btInit(true)
        }

        exportBtBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val measures = measureDao.getAllMeasuresImported(false)
                if (measures.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        val toast = Toast.makeText(
                            applicationContext,
                            "Nessuna misura da esportare",
                            Toast.LENGTH_SHORT
                        )
                        toast.show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        btInit(false)
                    }
                }
            }
        }

        importDescBtn.setOnClickListener {
            showInfoDialog(importBtn.text.toString(), getString(R.string.importDescription))
        }
        exportDescBtn.setOnClickListener {
            showInfoDialog(exportBtn.text.toString(), getString(R.string.exportDescription))
        }
        btImportDescBtn.setOnClickListener {
            showInfoDialog(importBtBtn.text.toString(), getString(R.string.importBtDescription))
        }
        btExportDescBtn.setOnClickListener {
            showInfoDialog(exportBtBtn.text.toString(), getString(R.string.exportBtDescription))
        }
    }

    /**
     * Alla distruzione dell'attività l'adapter Bluetooth, se inizializzato e se in discovery mode, si interrompe
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        Log.d("BLUETOOTH", "Receiver deregistrato")
        if(this::bluetoothAdapter.isInitialized) {
            if (bluetoothAdapter.isDiscovering) {
                stopScanning()
            }
        }
    }

    /**
     * Esporta le misure effettuate dall'utente in un file e avvia l'intent per condividerlo
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportData() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.GONE
                exportProgressBar.visibility = View.VISIBLE
            }
            mapper = jacksonObjectMapper()  // Mapper per manipolare il file JSON
            val measures = measureDao.getAllMeasuresImported(false)

            if (measures.isEmpty()) {
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Nessuna misura da esportare", Toast.LENGTH_SHORT)
                    toast.show()
                }
            } else {
                val now = ZonedDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                val currTime = now.format(formatter)
                try {
                    val file = File("${applicationContext.filesDir}/export_${currTime}.mapc")
                    Log.d("FILE", file.toString())
                    mapper.writeValue(file, measures)
                    withContext(Dispatchers.Main) {
                        val toast = Toast.makeText(applicationContext, "Ho esportato ${measures.size} misure con successo!", Toast.LENGTH_SHORT)
                        toast.show()
                    }
                    val i = Intent(Intent.ACTION_SEND)
                    val uri = FileProvider.getUriForFile(applicationContext, "com.example.mapconnectivity.fileprovider", file)
                    i.type = "application/json"
                    i.putExtra(Intent.EXTRA_STREAM, uri)
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(i, "Condividi file"))

                } catch (e: Exception) {
                    Log.e("Export", e.toString())
                    withContext(Dispatchers.Main) {
                        val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
                        toast.show()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.VISIBLE
                exportProgressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Lancia l'intent per aprire la selezione del file da importare
     * */
    private fun launcherImportData() {
        importBtn.visibility = View.GONE
        importProgressBar.visibility = View.VISIBLE
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startForResult.launch(i)
    }

    /**
     * Importa nel database le misure presenti nel JSON in input scartando quelle già presenti
     * @param importedMeasures JSON con le misure da importare
     * */
    private fun importData(importedMeasures: JsonNode) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var senderId = importedMeasures[0].get("user_id").toString()
                senderId = senderId.substring( 1, senderId.length - 1 )
                var measureCounter = 0
                for (measure in importedMeasures) {
                    var timestamp = measure.get("timestamp").toString()
                    timestamp = timestamp.removeRange(timestamp.length - 1, timestamp.length) // Rimuove le virgolette alla fine
                    timestamp = timestamp.removeRange(0, 1)                                   // e all'inizio della stringa

                    // Se la misura non esiste già la importa
                    if (measureDao.countSameMeasures(senderId, timestamp, measure.get("lat").toString().toDouble(), measure.get("lon").toString().toDouble()).toString().toInt() == 0) {
                        measureCounter++

                        var userId = measure.get("user_id").toString()
                        userId = userId.removeRange(userId.length - 1, userId.length)       // Rimuove le virgolette alla fine
                        userId = userId.removeRange(0, 1)                                   // e all'inizio della stringa

                        val measurements = Measure(
                            timestamp = timestamp,
                            lat = measure.get("lat").toString().toDouble(),
                            lon = measure.get("lon").toString().toDouble(),
                            lte = measure.get("lte").toString().toDouble(),
                            wifi = measure.get("wifi").toString().toDouble(),
                            db = measure.get("db").toString().toDouble(),
                            user_id = userId,
                            imported = true
                        )
                        measureDao.insertMeasure(measurements)
                    }
                }

                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Ho importato $measureCounter misure con successo!", Toast.LENGTH_SHORT)
                    toast.show()
                    hideFragment()
                }
            } catch (e: Exception) {
                Log.e("Import", e.toString())
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            }
        }
    }

    /**
     * Verifica l'autorizzazione di un permesso
     * @param permission Permesso da controllare
     * @return true se l'autorizzazione è concessa, false altrimenti
     * */
    private fun checkPermission(permission: String): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED)
    }

    /**
     * Viene chiamata dopo la scelta dell'utente di fornire o meno le autorizzazioni ai permessi necessari
     * @param requestCode Codice della richiesta effettuata
     * @param permissions Permessi della richiesta effettuata (ereditato da super)
     * @param grantResults Array dei permessi accettati e non
     * */
    @SuppressLint("MissingPermission")
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
            Log.d("PERMISSIONS", "BT - ALL OK")
            if (requestCode == PERMISSION_BT_RECEIVER) {
                btInitHandler(true)
            } else if (requestCode == PERMISSION_BT_DISCOVER) {
                btInitHandler(false)
            }
        } else {
            // Almeno uno dei permessi è stato negato
            Log.d("PERMISSIONS", "ONE OR MORE BT PERMISSIONS MISSING")
            notifyMissingPermissions("L'applicazione ha bisogno dei permessi dei dispositivi nelle vicinanze per importare ed esportare tramite Bluetooth.")
        }
    }

    /**
     * Restituisce una lista di dispositivi con cui il telefono è già accoppiato
     * @return Lista dei dispositivi accoppiati
     * */
    @SuppressLint("MissingPermission")
    private fun getPairedDevices(): Set<BluetoothDevice>? {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        Log.d("PAIREDDEVICE", "BEGIN")
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            Log.d("PAIREDDEVICE", "$deviceName - $deviceHardwareAddress")
        }
        Log.d("PAIREDDEVICE", "END")
        return pairedDevices
    }


    /**
     * Controlla i permessi relativi al Bluetooth, altrimenti li chiede. Quindi sposta il controllo all'handler che controlla se il Bluetooth è attivo
     * @param isReceiver true se si agisce da CLIENT, false se da SERVER
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun btInit(isReceiver: Boolean) {
        val permissionsToRequest = mutableListOf<String>()
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (!checkPermission(Manifest.permission.BLUETOOTH)) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
        }
        if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissionsToRequest.isNotEmpty()) {
//            if (!isPermissionRequested) {
//                isPermissionRequested = true
                Log.d("PERMISSIONS", "BT PERMISSIONS MISSING")
                if (isReceiver) {
                    requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_BT_RECEIVER)
                } else {
                    requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_BT_DISCOVER)
                }
//            }  else {
//                notifyMissingPermissions("L'applicazione ha bisogno dei permessi dei dispositivi nelle vicinanze per importare ed esportare tramite Bluetooth.")
//            }

        } else {
            // Tutti i permessi sono stati già concessi
            Log.d("PERMISSIONS", "BT PERMISSIONS GRANTED")
            btInitHandler(isReceiver)
        }
    }

    /**
     * Controlla se il Bluetooth è attivo, altrimenti chiede di accenderlo. Quindi sposta il controllo all'handler richiesto (CLIENT o SERVER)
     * @param isReceiver true se si agisce da CLIENT, false se da SERVER
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun btInitHandler(isReceiver: Boolean) {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            isReceivers = isReceiver
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            if (isReceiver) {
                btReceiveHandler()
            } else {
                btDiscoverHandler()
            }
        }
    }

    /**
     * Inizializza la ricerca dei disposivi discoverabili tramite Bluetooth e salva i dispositivi scoperti
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun btReceiveHandler() {
        startScanning()

        // Vengono filtrati solo gli intent ACTION_FOUND, ovvero la scoperta di un dispositivo tramite Bluetooth
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)

        /**
         * Oggetto che salva l'indirizzo del dispositivo quando viene trovato
         * */
        receiver = object : BroadcastReceiver() {

            /**
             * Si attiva alla scoperta di un dispositivo
             * @param context Contesto dell'applicazione
             * @param intent Intent della scoperta del dispositivo
             * */
            @SuppressLint("MissingPermission")
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("BLUETOOTH", "Ricevuto intent: ${intent.action}")
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        newFoundDevices.add(device)
                        newFoundDevicesNames.add(device?.name)
                        if (!foundDevices.contains(device)) {
                            foundDevices.add(device)
                            devicesArrayAdapter.notifyDataSetChanged()

                            devicesArrayNames.add(device?.name)
                            devicesArrayNamesAdapter.notifyDataSetChanged()

                            Log.d("BLUETOOTH", "After:")
                            for (i in 0 until devicesArrayAdapter.count) {
                                val item = devicesArrayAdapter.getItem(i)
                                Log.d("BLUETOOTHARRAY", "Item $i: ${item?.name}")
                            }
                            Log.d("BLUETOOTH", "END")
                        }

                        val deviceName = device?.name
                        val deviceHardwareAddress = device?.address // MAC address

                        Log.d("BLUETOOTH", "$deviceName - $deviceHardwareAddress")
                    }
                }
            }
        }

        registerReceiver(receiver, filter)
        showListOfDevicesDialog()
    }

    /**
     * Inizializza la discoverability rendendosi rilevabile agli altri disposivi e avvia il socket del server
     * */
    @SuppressLint("MissingPermission")
    private fun btDiscoverHandler() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDuration)
        enableDiscoverabilityLauncher.launch(discoverableIntent)

        val serverSocket = AcceptThread()
        serverSocket.start()
    }

    /**
     * Crea e mostra un dialog contenente tutti i dispositivi trovati tramite Bluetooth e ne permette il collegamento
     * */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun showListOfDevicesDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.DialogTheme)
        dialogBuilder.setTitle("DISPOSITIVI NELLE VICINANZE")
        dialogBuilder.setNegativeButton("Chiudi") { _, _ ->
            stopScanning()
        }
        val listView = ListView(this)
        listView.adapter = devicesArrayNamesAdapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = devicesArrayAdapter.getItem(position)

            if (getPairedDevices()?.contains(selectedDevice) == true) {
                Log.d("BLUETOOTH", "${selectedDevice?.name} già pairato!")
            }

            if (selectedDevice != null) {
                Log.d("BLUETOOTH", "HAI SELEZIONATO: $selectedDevice")
                dialog.dismiss()
                showFragment("Connessione in corso a ${selectedDevice.name}", true)
                val clientSocket = ConnectThread(selectedDevice)
                clientSocket.start()
            }
        }

        dialogBuilder.setView(listView)

        dialog = dialogBuilder.create()
        dialog.show()
    }

    private val bluetoothHandler = Handler(Looper.getMainLooper())
    private val scanInterval = 2000 // Intervallo di scansione in millisecondi (2 secondi)

    /**
     * Oggetto che ricerca periodicamente dispositivi Bluetooth
     * */
    private val scanRunnable = object : Runnable {
        /**
         * Funzione principale dell'oggetto che avvia una scansione e elenca i dispositivi trovati
         * */
        @SuppressLint("MissingPermission")
        override fun run() {
            Log.d("LISTE", "NEWFOUNDDEVICES: $newFoundDevices")
            Log.d("LISTE", "FOUNDDEVICES: $foundDevices")
            foundDevices -= foundDevices.subtract(newFoundDevices.toSet())                          // Algoritmo per rimuovere indirizzi dei telefoni che non sono più discoverabili
            devicesArrayNames -= devicesArrayNames.subtract(newFoundDevicesNames.toSet())           // Algoritmo per rimuovere nomi dei telefoni che non sono più discoverabili
            Log.d("LISTE", "REMOVE: $foundDevices")
            devicesArrayAdapter.notifyDataSetChanged()
            devicesArrayNamesAdapter.notifyDataSetChanged()

            newFoundDevices.clear()
            newFoundDevicesNames.clear()
            bluetoothAdapter.startDiscovery()

            // Ripeti la scansione dopo l'intervallo
            bluetoothHandler.postDelayed(this, scanInterval.toLong())
        }
    }

    /**
     * Avvia la scansione Bluetooth
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun startScanning() {
        bluetoothHandler.post(scanRunnable)
    }

    /**
     * Interrompe la scansione Bluetooth
     * */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        bluetoothAdapter.cancelDiscovery()
        unregisterReceiver(receiver)
        bluetoothHandler.removeCallbacks(scanRunnable)
    }

    /**
     * Classe secondaria per la gestione del thread lato SERVER
     * */
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(bluetoothNAME, bluetoothUUID)
        }

        /**
         * Funzione principale del thread server che si rende disponibile ad accettare una connessione in arrivo
         * */
        override fun run() {
            // Rimane in ascolto finché non viene trovato un socket a cui connettersi o viene catturata un'eccezione
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept() // La connessione viene accettata
                } catch (e: IOException) {
                    Log.e("BLUETOOTH", "Accept() ha fallito", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    Log.d("BLUETOOTH", "Accept() ha avuto successo")
                    manageSocket(it)
                    Log.d("BLUETOOTH", "DOPO MANAGESOCKET")
                    mmServerSocket?.close()
                    Log.d("BLUETOOTH", "DOPO mmServerSocket?.close()")
                    shouldLoop = false
                }
            }
        }

        /**
         * Tenta di inviare le proprie misure al telefono collegato
         * @param socket Socket della connessione bluetooth
         * */
        fun manageSocket(socket: BluetoothSocket) {
            try {
                mapper = jacksonObjectMapper() // Mapper per manipolare il file JSON
                var payload = mapper.writeValueAsString(measureDao.getAllMeasuresImported(false))
                payload += "-- END --"
                Log.d("BLUETOOTH", "payload: $payload")

                // Calcola la lunghezza in byte utilizzando l'encoding UTF-8
                val byteLength = payload.toByteArray(Charsets.UTF_8).size
                Log.d("TRANSMISSION", "Lunghezza in byte: $byteLength")

                val payloadByteArray = payload.toByteArray()

                socket.outputStream.write(payloadByteArray)
                CoroutineScope(Dispatchers.Main).launch {
                    hideFragment()
                    val toast = Toast.makeText(applicationContext, "Esportazione effettuata", Toast.LENGTH_SHORT)
                    toast.show()
                }
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "ERRORE NELL'INVIO DEI DATI DA PARTE DEL SERVER", e)
            }
        }
    }

    /**
     * Classe secondaria per la gestione del thread lato CLIENT
     * */
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(bluetoothUUID)
        }

        /**
         * Funzione principale del thread client quando tenta di connettersi a un altro dispositivo
         * */
        @RequiresApi(Build.VERSION_CODES.S)
        override fun run() {
            if (this.isInterrupted) {
                return
            }
            // Cancella la ricerca perchè non più necessaria
            stopScanning()

            mmSocket.let { socket ->
                // Si connette al dispositivo remoto tramite il socket.
                if (socket.isConnected) {
                    Log.d("BLUETOOTH", "IL SOCKET E' ANCORA APERTO, PROVO A CHIUDERLO.")
                    cancel()
                }
                try {
                    socket.connect()
                } catch (e: IOException) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val toast = Toast.makeText(
                            applicationContext,
                            "Qualcosa è andato storto! Prova a richiedere l'esportazione",
                            Toast.LENGTH_LONG
                        )
                        toast.show()
                        hideFragment()
                    }
                }
                manageSocket(socket) // La connessione è stata effettuata con successo.
            }
        }

        /**
         * Tenta di chiudere il socket ConnectThread per terminare la connessione
         * */
        fun cancel() {
            try {
                Log.d("BLUETOOTH", "Chiusura socket ConnectThread")
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "Non è stato possibile chiudere il socket client", e)
            }
        }

        /**
         * Riceve i dati delle misure e li importa
         * @param socket Socket della connessione bluetooth
         * */
        fun manageSocket(socket: BluetoothSocket) {
            val mmInStream: InputStream = socket.inputStream
            val mmBuffer = ByteArray(1024)

            if (this.isInterrupted) {
                return
            }

            var fullMsg = ""
            var numBytes: Int

            try {
                // Continua a leggere dall'inputStream finché non incorre in un'eccezione
                while (true) {
                    numBytes = mmInStream.read(mmBuffer)
                    fullMsg += String(mmBuffer.copyOf(numBytes), Charsets.UTF_8)

                    if (fullMsg.contains("-- END --")) {
                        fullMsg = fullMsg.replace("-- END --", "")
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "Errore nella lettura da inputStream", e)
            } finally {
                try {
                    mmSocket.close()
                } catch (e: IOException) {
                    Log.e("BLUETOOTH", "Errore nella chiusura di InputStream o del socket", e)
                }
            }
            Log.d("TRANSMISSION", fullMsg)

            mapper = jacksonObjectMapper()
            val json = mapper.readTree(fullMsg)
            importData(json)
            cancel()
        }
    }

    /**
     * Interrompe la discoverability del dispositivo
     * */
    @SuppressLint("MissingPermission")
    fun stopDiscoverable() {
        // Viene inviata una richiesta di discoverability di 1 secondo per rendere il telefono non discoverabile alla scadenza
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 1)
        disableDiscoverabilityLauncher.launch(discoverableIntent)
    }

    /**
     * Crea e mostra un dialog di informazioni
     * @param title Titolo del dialog
     * @param info Testo del dialog
     * */
    private fun showInfoDialog(title: String, info: String) {
        val dialogBuilder = AlertDialog.Builder(this, R.style.DialogTheme)
        dialogBuilder.setTitle("Descrizione di $title")

        dialogBuilder.setMessage(info)
        dialogBuilder.setNegativeButton("Chiudi") { _, _ -> }
        dialogBuilder.create().show()
    }

    /**
     * Crea e mostra la notifica che allerta l'utente che mancano i permessi e lo indirizza alle impostazioni dell'applicazione per fornirli
     * @param str Testo del dialog
     * */
    private fun notifyMissingPermissions(str: String) {
        val dialogBuilder = AlertDialog.Builder(this, R.style.DialogTheme)
        dialogBuilder.setTitle("Permessi di posizione mancanti")
        dialogBuilder.setMessage("$str \nPer favore autorizzane l'utilizzo per proseguire.")
        dialogBuilder.setNegativeButton("Prosegui") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)

            finish()
        }
        dialogBuilder.setNeutralButton("Chiudi") { _, _ -> }
        dialogBuilder.create().show()
    }
}

