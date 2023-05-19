package com.example.mapconnectivity

import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10

class MainActivity : AppCompatActivity() {
    private lateinit var microphoneText: TextView

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val fetchBtn = findViewById<Button>(R.id.fetchBtn)
        microphoneText = findViewById(R.id.microphoneText)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
        }

        fetchBtn.setOnClickListener { fetchMicrophone() }
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
}