package com.example.mapconnectivity

import androidx.room.*

@Entity(tableName = "measures")
data class Measure(@PrimaryKey(autoGenerate = true) var id: Long? = null,
                   var timestamp: String,
                   var lat: Double?,
                   var lon: Double?,
                   var lte: Double,
                   var wifi: Double,
                   var db: Double)

data class AverageMeasures(
    val avgLte: Double?,
    val avgWifi: Double?,
    val avgDb: Double?
)

@Dao
interface MeasureDao {
    @Query("SELECT * FROM measures")
    fun getAllMeasures(): List<Measure>

    @Query("SELECT (CASE COUNT(*) WHEN 0 THEN NULL ELSE AVG(lte) END) AS avgLte, (CASE COUNT(*) WHEN 0 THEN NULL ELSE AVG(wifi) END) AS avgWifi, (CASE COUNT(*) WHEN 0 THEN NULL ELSE AVG(db) END) AS avgDb FROM measures WHERE :lat1 >= lat AND :lat2 <= lat AND :lon1 >= lon AND :lon2 <= lon")
    fun getAvgMeasuresInPolygon(lat1: Double, lon1: Double, lat2: Double, lon2: Double): AverageMeasures

    @Insert
    fun insertMeasure(measure: Measure): Long
}

@Database(entities = [Measure::class], version = 3)
abstract class MeasureDB: RoomDatabase() {
    abstract fun measureDao(): MeasureDao
}

//val database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").build()
//val measureDao = database.measureDao()
//val measures = measureDao.getAllMeasures()

//import java.util.UUID
//
//val id = UUID.randomUUID().toString()
