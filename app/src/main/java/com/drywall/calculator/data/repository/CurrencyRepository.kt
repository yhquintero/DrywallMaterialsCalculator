package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.CurrencyHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllHistory(): Flow<List<CurrencyHistory>> = db.currencyHistoryDao().getAllHistory()
    fun getLatestRate(): Flow<CurrencyHistory?> = db.currencyHistoryDao().getLatestRate()
    suspend fun insert(entry: CurrencyHistory) = db.currencyHistoryDao().insert(entry)
}
