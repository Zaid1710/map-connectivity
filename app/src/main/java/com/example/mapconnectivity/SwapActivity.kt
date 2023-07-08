package com.example.mapconnectivity

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.room.Room
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SwapActivity : AppCompatActivity() {
    private lateinit var importBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao
    private lateinit var mapper: ObjectMapper
    private lateinit var exportProgressBar: ProgressBar

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swap)

        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        importBtn = findViewById(R.id.importBtn)
        exportBtn = findViewById(R.id.exportBtn)
        exportProgressBar = findViewById(R.id.exportProgressBar)

        importBtn.setOnClickListener {

        }

        exportBtn.setOnClickListener {
            exportData()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun exportData() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.GONE
                exportProgressBar.visibility = View.VISIBLE
            }
            mapper = jacksonObjectMapper()
            val measures = measureDao.getAllMeasures()

            val now = ZonedDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            val currTime = now.format(formatter)
            try {
                val file = File("${applicationContext.filesDir}/export_${currTime}.mapc")
                Log.d("FILE", file.toString())
                mapper.writeValue(file, measures)
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Ho esportato ${measures.last().id} misure con successo!", Toast.LENGTH_SHORT)
                    toast.show()
                }
                val i = Intent(Intent.ACTION_SEND)
                val uri = FileProvider.getUriForFile(applicationContext, "com.example.mapconnectivity.fileprovider", file)
                i.type = "application/json"
                i.putExtra(Intent.EXTRA_STREAM, uri)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(i, "Share File"))

            } catch (e: IOException) {
                Log.e("Export", e.toString())
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            }
            withContext(Dispatchers.Main) {
                exportBtn.visibility = View.VISIBLE
                exportProgressBar.visibility = View.GONE
            }
        }
    }
}