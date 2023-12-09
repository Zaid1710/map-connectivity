package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
 * - SOSTITUIRE IL WHILE TRUE CON WHILE LA SPUNTA DELLA MISURA IN BACKGROUND E' ATTIVA
 * */

class PeriodicFetchService : Service() {
    private var handler: Handler? = null
//    lateinit var mainActivity: MainActivity
//    private val binder: IBinder = LocalBinder()
    private var isOn = false
//    private lateinit var sensors: Sensor
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao

    private val CHANNEL_ID = "PeriodicFetchChannel"
    private val SERVICE_NOTIFICATION_ID = 1

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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())

//        mainActivity = MainActivity() NON SI FA
    }

//    private var measurementJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVIZIO", "Sono partito")
        val notification = createNotification()
        startForeground(SERVICE_NOTIFICATION_ID, notification)
        val sensor = Sensor(this)

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
        isOn = false
        this.stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.deleteNotificationChannel(CHANNEL_ID)
        stopSelf()
        Log.d("SERVIZIO", "sono stato distrutto")
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addMeasurement(sensors: Sensor): Measure {
        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//        sensors = Sensor(this)

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

//    @SuppressLint("NotificationPermission")
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
            .setContentTitle("Picture Download")
            .setContentText("Download in progress")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)

//        notificationManager.notify(SERVICE_NOTIFICATION_ID, mBuilder.build())
//
//        val newIntent = Intent(this, MainActivity::class.java)
//        newIntent.flags =
//            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        newIntent.putExtra("CALLER", "notifyService")
//        val pendingIntent: PendingIntent =
//            PendingIntent.getActivity(this, 0, newIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        mBuilder.setContentIntent(pendingIntent)

        val notification = mBuilder.build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)


//        val notificationIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            SERVICE_NOTIFICATION_ID,
//            notificationIntent,
//            PendingIntent.FLAG_IMMUTABLE
//        )
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("Servizio di ricerca periodica")
//            .setContentText("Il servizio è in esecuzione in background")
//            .setSmallIcon(R.mipmap.ic_launcher_round)
//            .setContentIntent(pendingIntent)
//            .build()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                "Periodic Fetch Service Channel",
//                NotificationManager.IMPORTANCE_DEFAULT
//            )
//            val manager = getSystemService(NotificationManager::class.java)
//            manager.createNotificationChannel(channel)
//
//            manager.notify(SERVICE_NOTIFICATION_ID, notification)
//        }
        return notification
    }
}