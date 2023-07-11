package com.example.mapconnectivity

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.room.Room
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.io.BufferedReader
import java.io.InputStreamReader

class SwapActivity : AppCompatActivity() {
    private lateinit var importBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var database: MeasureDB
    private lateinit var measureDao: MeasureDao
    private lateinit var mapper: ObjectMapper
    private lateinit var exportProgressBar: ProgressBar
    private lateinit var importProgressBar: ProgressBar

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val uri = result.data!!.data!!
                val inputStreamReader = InputStreamReader(contentResolver.openInputStream(uri))
                val bufferedReader = BufferedReader(inputStreamReader)
                val s = bufferedReader.readLine()

                val objectMapper = ObjectMapper()
                val importedMeasures = objectMapper.readValue(s, JsonNode::class.java)

                importData(importedMeasures)
            } catch (e: Exception) {
                Log.e("Import", e.toString())
                val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
                toast.show()
            }
        }
        importBtn.visibility = View.VISIBLE
        importProgressBar.visibility = View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swap)

        database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").fallbackToDestructiveMigration().build()
        measureDao = database.measureDao()

        importBtn = findViewById(R.id.importBtn)
        importProgressBar = findViewById(R.id.importProgressBar)
        exportBtn = findViewById(R.id.exportBtn)
        exportProgressBar = findViewById(R.id.exportProgressBar)

        importBtn.setOnClickListener {
            launcherImportData()
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
                startActivity(Intent.createChooser(i, "Condividi file"))

            } catch (e: Exception) {
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

    private fun launcherImportData() {
        importBtn.visibility = View.GONE
        importProgressBar.visibility = View.VISIBLE
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startForResult.launch(i)
    }

    private fun importData(importedMeasures: JsonNode) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var i = 0
                while (importedMeasures[i].get("imported").toString().toBoolean()) { i++ } // Ottiene l'indice per la prima misura non importata
                val senderId = importedMeasures[i].get("user_id").toString()
                measureDao.deleteMeasuresFrom(senderId)
                var measureCounter = 0
                for (measure in importedMeasures) {
                    if (!measure.get("imported").toString().toBoolean()) {
                        measureCounter++
                        var timestamp = measure.get("timestamp").toString()
                        timestamp = timestamp.removeRange(timestamp.length - 1, timestamp.length)
                        timestamp = timestamp.removeRange(0, 1)

                        var userId = measure.get("user_id").toString()
                        userId = userId.removeRange(userId.length - 1, userId.length)
                        userId = userId.removeRange(0, 1)

                        val measurements = Measure(
                            timestamp = timestamp,
                            lat = measure.get("lat").toString().toDouble(),
                            lon = measure.get("lon").toString().toDouble(),
                            lte = measure.get("lte").toString().toDouble(),
                            wifi = measure.get("wifi").toString().toDouble(),
                            db = measure.get("db").toString().toDouble(),
                            user_id = userId,
                            imported = true
                        )
                        measureDao.insertMeasure(measurements)
                    }
                }

                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Ho importato $measureCounter misure con successo!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            } catch (e: Exception) {
                Log.e("Import", e.toString())
                withContext(Dispatchers.Main) {
                    val toast = Toast.makeText(applicationContext, "Qualcosa è andato storto!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            }
        }
    }
}