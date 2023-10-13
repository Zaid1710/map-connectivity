package com.example.mapconnectivity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * TODO:
 *  FARE IN MODO CHE I PULSANTI NON VENGANO PREMUTI PIÙ DI UNA VOLTA A SESSIONE DI SCAMBIO
 *  FARE IN MODO DI POTER SPEGNERE LA DISCOVERABILITÀ (SMETTERE DI ESPORTARE)
 * */

class SwapActivity : AppCompatActivity() {
    private lateinit var importBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var importBtBtn: Button
    private lateinit var exportBtBtn: Button
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
    private var newFoundDevices: MutableList<BluetoothDevice?> = mutableListOf()
    private lateinit var devicesArrayAdapter: ArrayAdapter<BluetoothDevice>
    private val bluetooth_UUID = UUID.fromString("161201d1-1def-4267-89a4-c88b23b4bd87")
    private val bluetooth_NAME = "mapConnectivity"

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

    @RequiresApi(Build.VERSION_CODES.S)
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (isReceivers) {
                Log.d("BLUETOOTH", "sono il ricevitore")
                btReceiveHandler()
            } else {
                Log.d("BLUETOOTH", "sono il mandatore")
                btDiscoverHandler()
            }

        } else {
            val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    private val enableDiscoverabilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.d("BLUETOOTH", "Richiesta discoverability cancellata")
            val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
            toast.show()
        } else {
            Log.d("BLUETOOTH", "Discoverability abilitata con successo")

            if (this.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED) {
                Log.d("BLUETOOTH", "Receiver registrato")
            } else {
                Log.d("BLUETOOTH", "Permesso di posizione negato")
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swap)

        foundDevices = mutableListOf()
        newFoundDevices = mutableListOf()
        devicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, foundDevices)
        Log.d("BLUETOOTH", "Before:")
        for (i in 0 until devicesArrayAdapter.count) {
            val item = devicesArrayAdapter.getItem(i)
            Log.d("BLUETOOTH", "Item $i: $item")
        }
        Log.d("BLUETOOTH", "END")


//        //  Indicates a change in the Wi-Fi Peer-to-Peer status.
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
//
//        // Indicates a change in the list of available peers.
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
//
//        // Indicates the state of Wi-Fi P2P connectivity has changed.
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
//
//        // Indicates this device's details have changed.
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        importBtn = findViewById(R.id.importBtn)
        importProgressBar = findViewById(R.id.importProgressBar)
        exportBtn = findViewById(R.id.exportBtn)
        exportProgressBar = findViewById(R.id.exportProgressBar)

        importBtBtn = findViewById(R.id.importBtBtn)
        exportBtBtn = findViewById(R.id.exportBtBtn)

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
            btInit(false)
        }


//        val mManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
//        val mChannel = mManager.initialize(this, mainLooper, null)
    }



    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        Log.d("BLUETOOTH", "Receiver deregistrato")
        if(bluetoothAdapter.isDiscovering) {
            stopScanning()
        }
    }

//    override fun onResume() {
//        super.onResume()
//
//        wifiReceiver = object : BroadcastReceiver() {
//            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//            override fun onReceive(context: Context, intent: Intent) {
//                val action = intent.action
//                if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
//                    // Determine if Wifi Direct mode is enabled or not, alert
//                    // the Activity.
//                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
//                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
//
//                    } else {
//
//                    }
//                } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
//
//                    // The peer list has changed!  We should probably do something about
//                    // that.
//                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
//
//                    // Connection state changed!  We should probably do something about
//                    // that.
//                } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
//
//                    //Prendi i dati
//                    var device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
//                    Log.d("WIFI", "${device?.deviceName}")
//                }
//            }
//        }
//
//        registerReceiver(wifiReceiver, intentFilter)
//    }
//
//    override fun onPause() {
//        super.onPause()
//        unregisterReceiver(wifiReceiver)
//    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportData() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.GONE
                exportProgressBar.visibility = View.VISIBLE
            }
            mapper = jacksonObjectMapper()
            val measures = measureDao.getAllMeasures()

            val now = ZonedDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            val currTime = now.format(formatter)
            try {
                val file = File("${applicationContext.filesDir}/export_${currTime}.mapc")
                Log.d("FILE", file.toString())
                mapper.writeValue(file, measures)
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Ho esportato ${measures.last().id} misure con successo!", Toast.LENGTH_SHORT)
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
                    val toast = Toast.makeText(applicationContext, "Qualcosa � andato storto!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            }
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.VISIBLE
                exportProgressBar.visibility = View.GONE
            }
        }
    }

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

    private fun importData(importedMeasures: JsonNode) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var i = 0
                while (importedMeasures[i].get("imported").toString().toBoolean()) { i++ } // Ottiene l'indice per la prima misura non importata
                val senderId = importedMeasures[i].get("user_id").toString()
                measureDao.deleteMeasuresFrom(senderId)
                var measureCounter = 0
                for (measure in importedMeasures) {
                    if (!measure.get("imported").toString().toBoolean()) {
                        measureCounter++
                        var timestamp = measure.get("timestamp").toString()
                        timestamp = timestamp.removeRange(timestamp.length - 1, timestamp.length)
                        timestamp = timestamp.removeRange(0, 1)

                        var userId = measure.get("user_id").toString()
                        userId = userId.removeRange(userId.length - 1, userId.length)
                        userId = userId.removeRange(0, 1)

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
                }
            } catch (e: Exception) {
                Log.e("Import", e.toString())
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Qualcosa � andato storto!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            }
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
        }
    }

//    @SuppressLint("MissingPermission")
//    private fun getPairedDevices(): Set<BluetoothDevice>? {
//        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
//        Log.d("PAIREDDEVICE", "BEGIN")
//        pairedDevices?.forEach { device ->
//            val deviceName = device.name
//            val deviceHardwareAddress = device.address // MAC address
//            Log.d("PAIREDDEVICE", "$deviceName - $deviceHardwareAddress")
//        }
//        Log.d("PAIREDDEVICE", "END")
//        return pairedDevices
//    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun btInit(isReceiver: Boolean) {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

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
//        if (!checkPermission(Manifest.permission.BLUETOOTH_ADMIN)) {
//            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
//        }
//        if (!checkPermission(Manifest.permission.BLUETOOTH_PRIVILEGED)) {
//            permissionsToRequest.add(Manifest.permission.BLUETOOTH_PRIVILEGED)
//        }
//        if (!checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
//            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
//        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PERMISSIONS", "BT PERMISSIONS MISSING")
            if (isReceiver) {
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_BT_RECEIVER)
            } else {
                requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_BT_DISCOVER)
            }

        } else {
            // Tutti i permessi sono stati già concessi
            Log.d("PERMISSIONS", "BT PERMISSIONS GRANTED")
            btInitHandler(isReceiver)
        }
    }

    // Questa funzione è inutile, si può accorpare con quella sopra
    @RequiresApi(Build.VERSION_CODES.S)
    private fun btInitHandler(isReceiver: Boolean) {
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

    // Quello che importa. Rileva gli altri dipositivi e ci si connette come client
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun btReceiveHandler() {
        startScanning()

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("BLUETOOTH", "Ricevuto intent: ${intent.action}")
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        newFoundDevices.add(device)
                        if (!foundDevices.contains(device)) {
                            foundDevices.add(device)
                            devicesArrayAdapter.notifyDataSetChanged()

                            Log.d("BLUETOOTH", "After:")
                            for (i in 0 until devicesArrayAdapter.count) {
                                val item = devicesArrayAdapter.getItem(i)
                                Log.d("BLUETOOTH", "Item $i: $item")
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

    // Quello che esporta. Si rende rilevabile agli altri dispositivi e fa da server
    @SuppressLint("MissingPermission")
    private fun btDiscoverHandler() {
        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        Log.d("BLUETOOTH", "Richiesta di discoverability in corso...")
        enableDiscoverabilityLauncher.launch(discoverableIntent)

        CoroutineScope(Dispatchers.IO).launch {
            var serverSocket = AcceptThread()
            serverSocket.start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showListOfDevicesDialog() {
        val dialogBuilder = AlertDialog.Builder(this, R.style.DialogTheme)
        dialogBuilder.setTitle("DISPOSITIVI NELLE VICINANZE")
        dialogBuilder.setNegativeButton("Chiudi") { _, _ -> }

        val listView = ListView(this)
        listView.adapter = devicesArrayAdapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = devicesArrayAdapter.getItem(position)

            if (selectedDevice != null) {
                Log.d("BLUETOOTH", selectedDevice.toString())
                var clientSocket = ConnectThread(selectedDevice)
                clientSocket.start()
            }
        }

        dialogBuilder.setView(listView)

//        dialogBuilder.setAdapter(devicesArrayAdapter, null)
//        dialogBuilder.setItems(measures_titles) { _, which ->
//            showDetailsDialog(measures[which])
//        }


        val dialog = dialogBuilder.create()
        dialog.show()
    }

    private val bluetoothHandler = Handler(Looper.getMainLooper())
    private val scanInterval = 2000 // Intervallo di scansione in millisecondi (2 secondi)

    private val scanRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            // Avvia una nuova scansione
            Log.d("LISTE", "NEWFOUNDDEVICES: $newFoundDevices")
            Log.d("LISTE", "FOUNDDEVICES: $foundDevices")
            foundDevices -= foundDevices.subtract(newFoundDevices)
            Log.d("LISTE", "REMOVE: $foundDevices")
            devicesArrayAdapter.notifyDataSetChanged()

            newFoundDevices.clear()
            bluetoothAdapter.startDiscovery()

            // Ripeti la scansione dopo l'intervallo
            bluetoothHandler.postDelayed(this, scanInterval.toLong())
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun startScanning() {
        // Inizia la scansione periodica
        bluetoothHandler.post(scanRunnable)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        // Interrompi la scansione periodica
        bluetoothAdapter.cancelDiscovery()
        unregisterReceiver(receiver)
        bluetoothHandler.removeCallbacks(scanRunnable)
    }

    // SERVER //
    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(bluetooth_NAME, bluetooth_UUID)
        }

        override fun run() {
            // Rimane in ascolto finché non viene trovato un socket o viene catturata un'eccezione
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e("BLUETOOTH", "Accept() ha fallito", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    Log.d("BLUETOOTH", "Accept() ha avuto successo")
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Chiude il socket e termina la connessione
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "Non è stato possibile chiudere il socket server", e)
            }
        }
    }

    // CLIENT //
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(bluetooth_UUID)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        override fun run() {
            // Cancella la ricerca perchè non più necessaria
            stopScanning()

            mmSocket?.let { socket ->
                // Si connette al dispositivo remoto tramite il socket.
                socket.connect()

                // La connessione è stata effettuata con successo.
                Log.d("BLUETOOTH", "Connessione effettuata con successo")
            }
        }

        // Chiude il socket e termina la connessione
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "Non è stato possibile chiudere il socket client", e)
            }
        }
    }
}

