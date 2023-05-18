package com.example.mapconnectivity

import android.annotation.SuppressLint
import android.content.Context
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
    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val fetchBtn = findViewById<Button>(R.id.fetchBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        val amplitudeBtn = findViewById<Button>(R.id.amplitudeBtn)
        val microphoneText = findViewById<TextView>(R.id.microphoneText)
        val recorder = MediaRecorder(applicationContext)



        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
        }

        fetchBtn.setOnClickListener {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.maxAmplitude
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile("/data/user/0/com.example.mapconnectivity/files/file.3gpp")
            recorder.prepare()
            recorder.start()   // Recording is now started
            microphoneText.text = "BELLO"
            Log.d("MediaRecorder", "Started")
        }

        stopBtn.setOnClickListener {
            recorder.stop()
            microphoneText.text = "BRUTTO"
            Log.d("MediaRecorder", "Stopped")
            recorder.reset()   // You can reuse the object by going back to setAudioSource() step
            // recorder.release() // Now the object cannot be reused
        }

        amplitudeBtn.setOnClickListener {
            // var maxAmplitude = recorder.maxAmplitude.toString()
            val amplitude = recorder.maxAmplitude
            if (amplitude != 0) {
                val db = (20 * log10(amplitude.toDouble())).toString()
                microphoneText.text = "$db dB"
                Log.d("MediaRecorder", db)
            }
        }
    }
}