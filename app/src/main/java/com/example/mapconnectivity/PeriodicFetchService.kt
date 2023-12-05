package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * TODO:
 * - STATUS BAR PER DIRTI QUANTE NE HA FATTE
 * - SOSTITUIRE IL WHILE TRUE CON WHILE LA SPUNTA DELLA MISURA IN BACKGROUND E' ATTIVA
 * */

class PeriodicFetchService : Service() {
    private var handler: Handler? = null
//    lateinit var mainActivity: MainActivity
//    private val binder: IBinder = LocalBinder()
    private var isOn = false
    private lateinit var sensors: Sensor
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao

    // Class used for the client Binder.
//    inner class LocalBinder : Binder() {
//        fun getService(): PeriodicFetchService {
//            // Return this instance of MyService so clients can call public methods
//            return this@PeriodicFetchService
//        }
//    }
//
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
//        mainActivity = MainActivity() NON SI FA
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVIZIO", "Sono partito")

        isOn = true

        val seconds = intent?.getIntExtra("seconds", 60)
        Log.d("SERVIZIO", "seconds = $seconds")
        // Aspetta seconds secondi e fai la chiamata a getMeasures()
        CoroutineScope(Dispatchers.IO).launch {
            while (isOn) {
                try {
                    if (seconds != null) {
                        delay((seconds * 1000L))
                        if (isOn) {
                            handler?.post {
                                Log.d("SERVIZIO", "Sto facendo una misura, $isOn")
                                val pippo = addMeasurement()
                                Log.d("SERVIZIO", "$pippo")
//                            Toast.makeText(applicationContext, "ciaooooooooooooo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.e("SERVIZIO", "Qualcosa è andato storto", e)
                    e.printStackTrace()
                }
            }
        }

//        return super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isOn = false
        Log.d("SERVIZIO", "sono stato distrutto")
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addMeasurement(): Measure {
        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        sensors = Sensor(this)

        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        val measurements = Measure(
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            lat = gpsLocation?.latitude,
            lon = gpsLocation?.longitude,
            lte = sensors.getLteSignalStrength(),
            wifi = sensors.fetchWifi(),
            db = sensors.fetchMicrophone(),
            user_id = User.getUserId(applicationContext),
            imported = false
        )
        CoroutineScope(Dispatchers.IO).launch { measureDao.insertMeasure(measurements) }


        return measurements
    }
}