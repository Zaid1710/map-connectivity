package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
 * */

class PeriodicFetchService : Service() {
    private var handler: Handler? = null
    private var isOn = false
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null

    private val CHANNEL_ID = "PeriodicFetchChannel"
    private val SERVICE_NOTIFICATION_ID = 1

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
    }


    // Listener per la posizione
    private val locationListener: android.location.LocationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d("LOCLISTENER", "${location.latitude}, ${location.longitude}")
            lastLocation = location
        }
//            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
//            override fun onProviderEnabled(provider: String) {}
//            override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVIZIO", "Sono partito")
        val notification = createNotification()
        startForeground(SERVICE_NOTIFICATION_ID, notification)
        val sensor = Sensor(this)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, locationListener)

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
                                val pippo = addMeasurement(sensor)
                                Log.d("SERVIZIO", "$pippo")
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
        locationManager?.removeUpdates(locationListener)
        isOn = false
        this.stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.deleteNotificationChannel(CHANNEL_ID)
        stopSelf()
        Log.d("SERVIZIO", "sono stato distrutto")
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addMeasurement(sensors: Sensor) {
        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()


        val measurements = Measure(
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            lat = lastLocation?.latitude,
            lon = lastLocation?.longitude,
            lte = sensors.getLteSignalStrength(),
            wifi = sensors.fetchWifi(),
            db = sensors.fetchMicrophone(),
            user_id = User.getUserId(applicationContext),
            imported = false
        )

        Log.d("SERVIZIO", measurements.toString())

        CoroutineScope(Dispatchers.IO).launch {
            measureDao.insertMeasure(measurements)
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        val notificationManager = NotificationManagerCompat.from(this)
        val name = "nome"
        val description = "descrizione"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = description
        notificationManager.createNotificationChannel(channel)

        val mBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        mBuilder
            .setContentTitle("Misura periodica")
            .setContentText("Misurazione in corso")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0,0,true)
            .setOngoing(true)

        val notification = mBuilder.build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)

        return notification
    }
}