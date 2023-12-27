package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfoLte
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.math.log10

class Sensor(private var context: Context) {
    private lateinit var pressureSensorListener: PressureSensorListener

    /* Restituisce la potenza del segnale Wifi */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.R)
    fun fetchWifi(): Double {
//            if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
//                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_LOCATION)
//            } else {
//
//            }
        val wfm2: WifiManager =
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        var maxLevel = -200
        for (scanResult in wfm2.scanResults) {
            if (scanResult.level > maxLevel) {
                maxLevel = scanResult.level
            }
        }

        return maxLevel.toDouble()
    }

    /* Ottiene la potenza del segnale LTE */
    fun getLteSignalStrength(): Double {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        try {
            val cellInfoList = telephonyManager.allCellInfo
            Log.d("LTE", "cellInfoList $cellInfoList")
            if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                for (info in cellInfoList) {
                    if (info is CellInfoLte) {
                        val cellSignalStrength = info.cellSignalStrength
                        return cellSignalStrength.dbm.toDouble()
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.wtf("LTE", e)
        }
        return 1.0
    }

    /* Registra 5 secondi l'audio ambientale dal microfono e calcola la media dei dB recepiti */
    @RequiresApi(Build.VERSION_CODES.S)
    fun fetchMicrophone(): Double {
        val SECONDS_TO_FETCH = 1
        var avgAmplitude = 0.0
        var amplitudes = arrayOf<Double>()
        val recorder = MediaRecorder(context)
        val outputFile = File(context.filesDir, "temp.3gp")
        outputFile.createNewFile()

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        recorder.setOutputFile(outputFile.absolutePath)
        recorder.prepare()
        recorder.start()
        Log.d("MediaRecorder", "Started")
        repeat(SECONDS_TO_FETCH + 1) {
            val fetchedAmplitude = fetchAmplitude(recorder)
            amplitudes += fetchedAmplitude
            Thread.sleep(1000)
        }

        for (amplitude in amplitudes) {
            avgAmplitude += amplitude
        }
        avgAmplitude /= SECONDS_TO_FETCH
        recorder.stop()
        recorder.reset()

        return avgAmplitude

//        runOnUiThread {
//                microphoneText.text = avgAmplitude.toString()
//                microphoneText.setBackgroundColor(getQuality(-avgAmplitude, DB_BAD, DB_OPT))
//        }

//        Log.d("MediaRecorder", "Finished")
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
    inner class PressureSensorListener : SensorEventListener {
        var currentPressure: Float = 0f

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                currentPressure = event.values[0]
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /* Restituisce il livello di pressione atmosferica */
    fun getPressure(): Float {
        return pressureSensorListener.currentPressure
//        pressureText.text = pressureSensorListener.currentPressure.toString()
//        pressureText.setBackgroundColor(getQuality(pressureSensorListener.currentPressure.toDouble(), PRESSURE_BAD_LOW, PRESSURE_OPT))
    }


}