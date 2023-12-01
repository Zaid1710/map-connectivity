package com.example.mapconnectivity

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TODO:
 * - STATUS BAR PER DIRTI QUANTE NE HA FATTE
 * - SOSTITUIRE IL WHILE TRUE CON WHILE LA SPUNTA DELLA MISURA IN BACKGROUND E' ATTIVA
 * */

class PeriodicFetchService : Service() {
    private var handler: Handler? = null
//    lateinit var mainActivity: MainActivity
    private var serviceCallbacks: ServiceCallbacks? = null
    private val binder: IBinder = LocalBinder()
    private var isOn = false

    // Class used for the client Binder.
    inner class LocalBinder : Binder() {
        fun getService(): PeriodicFetchService {
            // Return this instance of MyService so clients can call public methods
            return this@PeriodicFetchService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("SERVIZIO", "Sono partito nella onBind")
        return binder
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
                                serviceCallbacks?.addMeasurement(true)
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

    fun setCallbacks(callbacks: ServiceCallbacks?) {
        serviceCallbacks = callbacks
    }
}