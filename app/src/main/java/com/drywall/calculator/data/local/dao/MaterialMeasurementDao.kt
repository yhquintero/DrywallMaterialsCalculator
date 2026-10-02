package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.MaterialMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialMeasurementDao {
    @Query("SELECT * FROM material_measurements")
    fun getAllMeasurements(): Flow<List<MaterialMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(measurements: MaterialMeasurement)

    @Delete
    suspend fun delete(measurement: MaterialMeasurement)
}
