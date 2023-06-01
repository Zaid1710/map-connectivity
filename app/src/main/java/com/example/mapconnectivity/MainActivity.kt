package com.example.mapconnectivity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10

class MainActivity : AppCompatActivity() {
    private lateinit var pressureSensorListener: PressureSensorListener
//    private lateinit var mapHandler: MapHandler

    private val PRESSURE_BAD_LOW = 500.0
    //    private val PRESSURE_BAD_HIGH = 500.0
    private val PRESSURE_OPT = 1000.0
    private val WIFI_BAD = -75.0
    private val WIFI_OPT = -55.0
    private val LTE_BAD = -95.0
    private val LTE_OPT = -80.0
    private val DB_BAD = -80.0
    private val DB_OPT = -60.0

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission(this, Manifest.permission.RECORD_AUDIO, 0)
        checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION, 1)
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
            checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION, 1)
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
//                wifiText.text = maxLevel.toString()
//                wifiText.setBackgroundColor(getQuality(maxLevel.toDouble(), WIFI_BAD, WIFI_OPT))
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
    private fun getLteSignalStrength() {
        Thread {
            val telephonyManager = this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION, 1)

            try {
                val cellInfoList = telephonyManager.allCellInfo
                Log.d("LTE", "cellInfoList $cellInfoList")
                if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                    for (info in cellInfoList) {
                        if (info is CellInfoLte) {
                            val cellSignalStrength = info.cellSignalStrength
//                            return cellSignalStrength.dbm
                            runOnUiThread {
//                                lteText.text = cellSignalStrength.dbm.toString()
//                                lteText.setBackgroundColor(getQuality(cellSignalStrength.dbm.toDouble(), LTE_BAD, LTE_OPT))
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.wtf("LTE", e)
            }
        }.start()
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
//                microphoneText.text = avgAmplitude.toString()
//                microphoneText.setBackgroundColor(getQuality(-avgAmplitude, DB_BAD, DB_OPT))
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
    private fun getPressure() {
//        pressureText.text = pressureSensorListener.currentPressure.toString()
//        pressureText.setBackgroundColor(getQuality(pressureSensorListener.currentPressure.toDouble(), PRESSURE_BAD_LOW, PRESSURE_OPT))
    }

    private fun getQuality(value: Double, bad: Double, optimal: Double): Int {
//        if (bad_high == null) {
//            return if (value <= bad_low) {
//                Color.rgb(255, 0, 0)
//            } else if (value >= optimal) {
//                Color.rgb(0, 255, 0)
//            } else {
//                Color.rgb(128, 128, 0)
//            }
//        } else {
        return if (value <= bad ) {
            Color.rgb(255, 0, 0)    // Rosso
        } else if (value >= optimal) {
            Color.rgb(0, 255, 0)    // Verde
        } else {
            Color.rgb(255, 255, 20) // Giallo
//            }
        }
    }
}