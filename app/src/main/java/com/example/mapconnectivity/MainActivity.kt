package com.example.mapconnectivity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)

    private fun fetchWifi() {
        Thread {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_WIFI_STATE), 7)

            }
            val wfm2: WifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            var maxLevel = -200
            for(scanResult in wfm2.scanResults) {
                if (scanResult.level > maxLevel) {
                    maxLevel = scanResult.level
                }
            }
            Log.d("WIFI", maxLevel.toString())
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun fetchLTE() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 3)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_NUMBERS), 4)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 5)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 6)
        }

        try {

            val tm: TelephonyManager = this.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val infoLte = tm.allCellInfo[2] as CellInfoLte
            val lte = infoLte.cellSignalStrength.dbm
            Log.d("LTE", "LTE: $lte")
        } catch (e: Throwable) {
            Log.wtf("LTE", e)
        }
    }

    /* Registra 5 secondi l'audio ambientale dal microfono e calcola la media dei dB recepiti */
    @RequiresApi(Build.VERSION_CODES.S)
    fun fetchMicrophone() {
        Thread {
            var amplitudes = arrayOf<Double>()
            val recorder = MediaRecorder(applicationContext)
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile("${externalCacheDir?.absolutePath}/temp.3gp")
            recorder.prepare()
            recorder.start()
            // Log.d("MediaRecorder", "Started")
            repeat(6) {
                val fetchedAmplitude = fetchAmplitude(recorder)
                amplitudes += fetchedAmplitude
                Thread.sleep(1000)
            }
            var avgAmplitude = 0.0
            for (amplitude in amplitudes) {
                avgAmplitude += amplitude
            }
            avgAmplitude /= 5
            recorder.stop()
            recorder.reset()

            Log.d("MediaRecorder", avgAmplitude.toString())
        }.start()
    }

    /* Prende in input l'ampiezza e la converte in dB */
    private fun fetchAmplitude(recorder: MediaRecorder): Double {
        val amplitude = recorder.maxAmplitude
        Log.d("MediaRecorder", "Amp $amplitude")
        var db = 0.0
        if (amplitude != 0) {
            db = (20 * log10(amplitude.toDouble()))
        }
        Log.d("MediaRecorder", "Db $db")
        return db
    }
}