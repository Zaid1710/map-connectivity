package com.example.mapconnectivity

import androidx.room.*

@Entity(tableName = "measures")
data class Measure(@PrimaryKey(autoGenerate = true) var id: Long? = null,
                   var timestamp: String,
                   var lat: Double?,
                   var lon: Double?,
                   var lte: Int,
                   var wifi: Int,
                   var db: Double)

data class AverageMeasures(
    val avgLte: Int,
    val avgWifi: Int,
    val avgDb: Double
)

@Dao
interface MeasureDao {
    @Query("SELECT * FROM measures")
    fun getAllMeasures(): List<Measure>

    @Query("SELECT AVG(lte) AS avgLte, AVG(wifi) AS avgWifi, AVG(db) AS avgDb FROM measures WHERE :lat1 >= lat AND :lat2 <= lat AND :lon1 >= lon AND :lon2 <= lon")
    fun getAvgMeasuresInPolygon(lat1: Double, lon1: Double, lat2: Double, lon2: Double): AverageMeasures?

    @Insert
    fun insertMeasure(measure: Measure): Long
}

@Database(entities = [Measure::class], version = 2)
abstract class MeasureDB: RoomDatabase() {
    abstract fun measureDao(): MeasureDao
}

//val database = Room.databaseBuilder(this, MeasureDB::class.java, "measuredb").build()
//val measureDao = database.measureDao()
//val measures = measureDao.getAllMeasures()

//import java.util.UUID
//
//val id = UUID.randomUUID().toString()
