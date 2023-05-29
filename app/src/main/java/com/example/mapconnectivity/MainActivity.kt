package com.example.mapconnectivity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

/* TODO:
    - Unificare le richieste dei permessi
*/
class MainActivity : AppCompatActivity() {
    private lateinit var microphoneText: TextView
    private lateinit var wifiText: TextView
    private lateinit var lteText: TextView
    private lateinit var pressureText: TextView

    private lateinit var pressureSensorListener: PressureSensorListener

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val microphoneBtn = findViewById<Button>(R.id.microphoneBtn)
        val lteBtn = findViewById<Button>(R.id.lteBtn)
        val wifiBtn = findViewById<Button>(R.id.wifiBtn)
        val pressureBtn = findViewById<Button>(R.id.pressureBtn)
        microphoneText = findViewById(R.id.microphoneText)
        wifiText = findViewById(R.id.wifiText)
        lteText = findViewById(R.id.lteText)
        pressureText = findViewById(R.id.pressureText)

        checkPermission(this, Manifest.permission.RECORD_AUDIO, 0)

        microphoneBtn.setOnClickListener { fetchMicrophone() }
        lteBtn.setOnClickListener { lteText.text = getLteSignalStrength(this).toString() }
        wifiBtn.setOnClickListener { fetchWifi() }
        pressureBtn.setOnClickListener { pressureText.text = getPressure().toString() }
    }

    override fun onResume() {
        super.onResume()

        checkPermission(this, Manifest.permission.INTERNET, 3)
        val sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        pressureSensorListener = PressureSensorListener()
        sensorManager.registerListener(pressureSensorListener, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /* Restituisce la potenza del segnale Wifi */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun fetchWifi() {
        Thread {
            checkPermission(this, Manifest.permission.ACCESS_WIFI_STATE, 2)
            val wfm2: WifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            var maxLevel = -200
            for (scanResult in wfm2.scanResults) {
                if (scanResult.level > maxLevel) {
                    maxLevel = scanResult.level
                }
            }
            runOnUiThread {
                Log.d("WIFI", maxLevel.toString())
                wifiText.text = maxLevel.toString()
            }
        }.start()
    }

    /* Verifica e richiedi un permesso */
    private fun checkPermission(activity: Activity, permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(
                activity,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        }
    }

    /* Ottiene la potenza del segnale LTE */
    private fun getLteSignalStrength(context: Context): Int {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION, 1)

        return try {
            val cellInfoList = telephonyManager.allCellInfo
            Log.d("LTE", "cellInfoList $cellInfoList")
            if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                for (info in cellInfoList) {
                    if (info is CellInfoLte) {
                        val cellSignalStrength = info.cellSignalStrength
                        return cellSignalStrength.dbm
                    }
                }
            }
            -1 // Valore di default se non � possibile ottenere la potenza del segnale
        } catch (e: SecurityException) {
            Log.wtf("LTE", e)
            -1 // Gestisci l'eccezione, restituendo il valore di default
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
            Log.d("MediaRecorder", "Started")
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

            runOnUiThread {
                microphoneText.text = avgAmplitude.toString()
            }

            Log.d("MediaRecorder", "Finished")
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

    /* Classe listener per ottenere i valori del sensore di umidita */
    private class PressureSensorListener : SensorEventListener {
        var currentPressure: Float = 0f

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                currentPressure = event.values[0]
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /* Restituisce il livello di pressione atmosferica */
    private fun getPressure(): Float {
        return pressureSensorListener.currentPressure
    }
}
