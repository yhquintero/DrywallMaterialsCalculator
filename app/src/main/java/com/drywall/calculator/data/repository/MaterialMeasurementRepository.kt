package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.MaterialMeasurement
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialMeasurementRepository @Inject constructor(private val db: AppDatabase) {
    fun getMeasurements(): Flow<List<MaterialMeasurement>> = db.materialMeasurementDao().getAllMeasurements()
    suspend fun saveMeasurements(measurements: MaterialMeasurement) = db.materialMeasurementDao().upsert(measurements)
    suspend fun deleteMeasurement(measurement: MaterialMeasurement) = db.materialMeasurementDao().delete(measurement)
}
