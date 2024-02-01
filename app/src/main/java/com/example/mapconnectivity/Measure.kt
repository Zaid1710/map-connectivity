package com.example.mapconnectivity

import androidx.room.*

@Entity(tableName = "measures")
data class Measure(@PrimaryKey(autoGenerate = true) var id: Long? = null,
                   var timestamp: String,
                   var lat: Double?,
                   var lon: Double?,
                   var lte: Double,
                   var wifi: Double,
                   var db: Double,
                   var user_id: String,
                   var imported: Boolean)


@Dao
interface MeasureDao {
    @Query("SELECT * FROM measures")
    fun getAllMeasures(): List<Measure>

    @Query("SELECT * FROM measures WHERE imported = :imported")
    fun getAllMeasuresImported(imported: Boolean): List<Measure>

    @Query("SELECT * FROM measures WHERE :lat1 >= lat AND :lat2 <= lat AND :lon1 >= lon AND :lon2 <= lon AND (imported = 0 OR imported = :imported) ORDER BY timestamp")
    fun getMeasuresInPolygon(lat1: Double, lon1: Double, lat2: Double, lon2: Double, imported: Boolean): List<Measure>

    @Query("SELECT COUNT(*) FROM measures WHERE :lat1 >= lat AND :lat2 <= lat AND :lon1 >= lon AND :lon2 <= lon AND imported = 1")
    fun countImportedMeasuresInPolygon(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int

    @Query("SELECT COUNT(*) FROM measures WHERE user_id = :user_id AND timestamp = :timestamp AND lat = :lat AND lon = :lon")
    fun countSameMeasures(user_id: String, timestamp: String, lat: Double, lon: Double): Int

    @Insert
    fun insertMeasure(measure: Measure): Long

    @Query("DELETE FROM measures WHERE id = :id")
    fun deleteMeasureWithId(id: Long?)

    @Query("DELETE FROM measures WHERE user_id = :user_id")
    fun deleteMeasuresFrom(user_id: String)

    @Query("DELETE FROM measures WHERE imported = :imported")
    fun deleteAllMeasuresImported(imported: Boolean)

    @Query("DELETE FROM measures")
    fun deleteAllMeasures()
}

@Database(entities = [Measure::class], version = 18)
abstract class MeasureDB: RoomDatabase() {
    abstract fun measureDao(): MeasureDao
}
