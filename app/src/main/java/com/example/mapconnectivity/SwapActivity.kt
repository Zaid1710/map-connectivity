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

/**
 * TODO:
 * */

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
    private val bluetooth_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetooth_NAME = "mapConnectivity"
    private lateinit var dialog: Dialog

    private lateinit var prefs : SharedPreferences
    private var DISCOVERABLE_DURATION : Int? = 60

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

    private fun showFragment(newText: String, isImport: Boolean) {
        mBundle = Bundle()
        mBundle.putString("swap", newText)
        mBundle.putBoolean("mode", isImport)
        if (!isImport && DISCOVERABLE_DURATION != null) {
            mBundle.putLong("timer", DISCOVERABLE_DURATION!!.toLong())
        }
        loadingFragment.arguments = mBundle
        mFragmentTransaction = mFragmentManager.beginTransaction()
        mFragmentTransaction.add(R.id.loadingFragment, loadingFragment).commit()
        Log.d("Bundle", mBundle.toString())

        importBtn.visibility = View.GONE
        exportBtn.visibility = View.GONE
        importBtBtn.visibility = View.GONE
        exportBtBtn.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    fun hideFragment() {
        mFragmentTransaction = mFragmentManager.beginTransaction()
        mFragmentTransaction.remove(loadingFragment).commit()
        loadingView.visibility = View.GONE
        importBtn.visibility = View.VISIBLE
        exportBtn.visibility = View.VISIBLE
        importBtBtn.visibility = View.VISIBLE
        exportBtBtn.visibility = View.VISIBLE
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swap)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        DISCOVERABLE_DURATION = prefs.getString("discovery_time", 60.toString())?.toIntOrNull() // Considera il doppio del tempo per scomparire, ad esempio se DISCOVERABLE_DURATION=30, il dispositivo verra' nascosto dopo 60 secondi circa (DA VERIFICARE).

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

//        var stopBtn : Button = findViewById(R.id.loadingFragment)

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
                val measures = measureDao.getOwnMeasures()
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

//        stopExportBtBtn.setOnClickListener {
//            stopDiscoverable()
//            stopExportBtBtn.visibility = View.GONE
//            exportBtBtn.visibility = View.VISIBLE
//        }
    }

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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportData() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.GONE
                exportProgressBar.visibility = View.VISIBLE
            }
            mapper = jacksonObjectMapper()
            val measures = measureDao.getOwnMeasures()

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
//                var i = 0
//                while (importedMeasures[i].get("imported").toString().toBoolean()) { i++ } // Ottiene l'indice per la prima misura non importata
                var senderId = importedMeasures[0].get("user_id").toString()
                senderId = senderId.substring( 1, senderId.length - 1 )
//                measureDao.deleteMeasuresFrom(senderId)
                var measureCounter = 0
                for (measure in importedMeasures) {
                    var timestamp = measure.get("timestamp").toString()
                    timestamp = timestamp.removeRange(timestamp.length - 1, timestamp.length)
                    timestamp = timestamp.removeRange(0, 1)
                    if (measureDao.countSameMeasures(senderId, timestamp, measure.get("lat").toString().toDouble(), measure.get("lon").toString().toDouble()).toString().toInt() == 0) {
                        measureCounter++

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
                    hideFragment()
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

//    private fun manageData() {
//        var data = receivedText
//        receivedText = ""
//        data = data.replace("-- END --", "")
//        Log.d("TRANSMISSION", data)
//        Log.d("TRANSMISSION", "Trasmissione finita, buonanotte")
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
//        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
//        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
//        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        receiver = object : BroadcastReceiver() {
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

    // Quello che esporta. Si rende rilevabile agli altri dispositivi e fa da server
    @SuppressLint("MissingPermission")
    private fun btDiscoverHandler() {
//        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
//            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION)
//        }
//        Log.d("BLUETOOTH", "Richiesta di discoverability in corso...")
//        enableDiscoverabilityLauncher.launch(discoverableIntent)

        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION)
        enableDiscoverabilityLauncher.launch(discoverableIntent)

        val serverSocket = AcceptThread()
        serverSocket.start()
    }

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

    private val scanRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            // Avvia una nuova scansione
            Log.d("LISTE", "NEWFOUNDDEVICES: $newFoundDevices")
            Log.d("LISTE", "FOUNDDEVICES: $foundDevices")
            foundDevices -= foundDevices.subtract(newFoundDevices)
            devicesArrayNames -= devicesArrayNames.subtract(newFoundDevicesNames)
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
                    manageSocket(it)
                    Log.d("BLUETOOTH", "DOPO MANAGESOCKET")
                    mmServerSocket?.close()
                    Log.d("BLUETOOTH", "DOPO mmServerSocket?.close()")
                    shouldLoop = false
                }
            }
        }

//        // Chiude il socket e termina la connessione
//        fun cancel() {
//            try {
//                mmServerSocket?.close()
//            } catch (e: IOException) {
//                Log.e("BLUETOOTH", "Non è stato possibile chiudere il socket server", e)
//            }
//        }

        // Send data
        fun manageSocket(socket: BluetoothSocket) {
            try {
                mapper = jacksonObjectMapper()
                var payload = mapper.writeValueAsString(measureDao.getOwnMeasures())
                payload += "-- END --"
                Log.d("BLUETOOTH", "payload: $payload")

                // Calcola la lunghezza in byte utilizzando l'encoding UTF-8
                val byteLength = payload.toByteArray(Charsets.UTF_8).size
                Log.d("TRANSMISSION", "Lunghezza in byte: $byteLength")

                val payloadByteArray = payload.toByteArray()

//                socket.use {
                socket.outputStream.write(payloadByteArray)
                CoroutineScope(Dispatchers.Main).launch {
                    hideFragment()
                    val toast = Toast.makeText(applicationContext, "Esportazione effettuata", Toast.LENGTH_SHORT)
                    toast.show()
                }
//                }

//                while (socket.isConnected) {
//                    Log.d("SOCKETTONE", "socket.isConnected = ${socket.isConnected}")
//                }
//                socket.outputStream.flush()

                // Altro codice qui

//                socket.outputStream.flush()

//                socket.outputStream.close()
//                socket.inputStream.close()
//                socket.close()

            } catch (e: IOException) {
                Log.e("BLUETOOTH", "ERRORE NELL'INVIO DEI DATI DA PARTE DEL SERVER", e)
            }
        }
    }

    // CLIENT //
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(bluetooth_UUID)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        override fun run() {
            if (this.isInterrupted) {
                return
            }
            // Cancella la ricerca perchè non più necessaria
            stopScanning()

            mmSocket.let { socket ->
                // Si connette al dispositivo remoto tramite il socket.
                Log.d("VEDIAMO", "A")
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
                manageSocket(socket)
                Log.d("VEDIAMO", "B e C")
                // La connessione è stata effettuata con successo.
        //                Log.d("BLUETOOTH", "Connessione effettuata con successo")
        //                } else {
        //                    val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
        //                    toast.show()
        //                }
            }
        }

        // Chiude il socket e termina la connessione
        fun cancel() {
            try {
                Log.d("BLUETOOTH", "Chiusura socket ConnectThread")
//                mmSocket?.inputStream?.close()
//                mmSocket?.outputStream?.close()
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "Non è stato possibile chiudere il socket client", e)
            }
        }

        // Receive data
        fun manageSocket(socket: BluetoothSocket) {
//            Log.d("VEDIAMO", "D")
//            thread.start()
//            Log.d("VEDIAMO", "E")
//            thread.join()
//            Log.d("VEDIAMO", "E2")
//            cancel()
//            Log.d("VEDIAMO", "E3")
//            this.interrupt()
//            Log.d("VEDIAMO", "E4")
////            Log.d("TRANSMISSION", messageHandler.obtainMessage().toString())

            val mmInStream: InputStream = socket.inputStream
            val mmBuffer = ByteArray(1024) // mmBuffer store for the stream

            if (this.isInterrupted) {
                return
            }

            var fullMsg = ""
            Log.d("VEDIAMO", "F")
            var numBytes: Int // bytes returned from read()
//            var receivedData = ""

            try {
                // Keep listening to the InputStream until an exception occurs.
                while (true) {
                    Log.d("MIAORIZIO", "BEFORE: $fullMsg")

                    // Read from the InputStream.
//                if ( mmInStream.available() > 0 ) {
                    Log.d("VEDIAMO", "G")
                    numBytes = mmInStream.read(mmBuffer)
                    fullMsg += String(mmBuffer.copyOf(numBytes), Charsets.UTF_8)

                    Log.d("MIAORIZIO", "AFTER: $fullMsg")

//                }
//                else {
//                    SystemClock.sleep(100)
//                }

                    Log.d("VEDIAMO", "I")
                    // Send the obtained bytes to the UI activity.
//                val readMsg = messageHandler.obtainMessage(MESSAGE_READ, numBytes, -1, mmBuffer)
//                readMsg.sendToTarget()
//                Log.d("TRANSMISSION", "PRIMA: ${String(mmBuffer, Charsets.UTF_8)}")
                    Log.d("VEDIAMO", "J")
//                receivedData += String(mmBuffer, 0, numBytes)

                    if (fullMsg.contains("-- END --")) {
                        fullMsg = fullMsg.replace("-- END --", "")
                        break
                    }

                }
            } catch (e: IOException) {
                Log.e("BLUETOOTH", "Error reading from InputStream", e)
            } finally {
                try {
//                    mmInStream.close()
                    mmSocket.close()
                } catch (e: IOException) {
                    Log.e("BLUETOOTH", "Error closing InputStream or socket", e)
                }
            }
            Log.d("TRANSMISSION", fullMsg)

            mapper = jacksonObjectMapper()
            val json = mapper.readTree(fullMsg)
            importData(json)
            cancel()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 1)
//        startActivity(discoverableIntent)
        disableDiscoverabilityLauncher.launch(discoverableIntent)
    }

}

