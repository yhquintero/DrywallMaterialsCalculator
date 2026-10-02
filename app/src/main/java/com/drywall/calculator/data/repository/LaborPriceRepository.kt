package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.LaborPrice
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaborPriceRepository @Inject constructor(private val db: AppDatabase) {
    fun getLaborPrices(): Flow<LaborPrice?> = db.laborPriceDao().getLaborPrices()
    suspend fun saveLaborPrices(prices: LaborPrice) = db.laborPriceDao().upsert(prices)
}
