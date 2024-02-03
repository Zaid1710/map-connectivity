package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
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
    private val locationListener: LocationListener =
        LocationListener { location ->
            Log.d("LOCLISTENER", "BBBB ${location.latitude}, ${location.longitude}")
            lastLocation = location
        }

    /**
     * All'avvio del servizio vengono inizializzate la variabili e il listener. Inoltre viene effetuata la misurazione periodica
     * @param intent Intent inviato dall'activity contenente le informazioni su ogni quanto tempo effettuare una misura
     * @param flags Ereditato da super
     * @param startId Ereditato da super
     * @return Costante che specifica il tipo del servizio da avviare
     * */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVIZIO", "Sono partito")
        var notification = createNotification(0)
        startForeground(SERVICE_NOTIFICATION_ID, notification)
        val sensor = Sensor(this)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, locationListener)

        isOn = true

        var counter = 0

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
                                addMeasurement(sensor)

                                // Aggiorna il counter e aggiorna la notifica
                                counter++
                                notification = createNotification(counter)
                                startForeground(SERVICE_NOTIFICATION_ID, notification)
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.e("SERVIZIO", "Qualcosa è andato storto", e)
                    e.printStackTrace()
                }
            }
        }

        return START_STICKY
    }

    /**
     * Alla distruzione del servizio interrompe anche il locationListener e la notifica
     * */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("SERVICEONDESTROY", locationManager.toString())
        locationManager?.removeUpdates(locationListener)
        isOn = false
        this.stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.deleteNotificationChannel(CHANNEL_ID)
        stopSelf()
        Log.d("SERVIZIO", "sono stato distrutto")
    }

    /**
     * Effettua una misura e la inserisce nel database
     * @param sensors Sensori per effettuare le misure
     * */
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

    /**
     * Crea e restituisce una notifica
     * @return Notifica creata
     * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(counter: Int): Notification {
        val notificationManager = NotificationManagerCompat.from(this)
        val name = "Misurazione in background"
        val description = "Notifica status bar per segnalare che la misurazione in background è attiva"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = description
        notificationManager.createNotificationChannel(channel)

        val mBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
        mBuilder
            .setContentTitle("Misura periodica")
            .setContentText("Misurazione in corso, misure effettuate: $counter")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(0,0,true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val notification = mBuilder.build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)

        return notification
    }
}