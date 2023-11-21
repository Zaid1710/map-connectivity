package com.example.mapconnectivity

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TODO:
 * - STATUS BAR PER DIRTI QUANTE NE HA FATTE
 * */

class PeriodicFetchService : Service() {
    var handler: Handler? = null
    lateinit var mainActivity: MainActivity

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        mainActivity = MainActivity()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVIZIO", "Sono partito")

        val seconds = intent?.getStringExtra("seconds")?.toInt()

        // Aspetta seconds secondi e fai la chiamata a getMeasures()
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    if (seconds != null) {
                        delay((seconds * 1000).toLong())
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace();
                }
                handler?.post {
//                    mainActivity.addMeasurement(true)
                    Toast.makeText(applicationContext, "ciaooooooooooooo", Toast.LENGTH_SHORT).show()
                }
            }
        }

//        return super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
}