package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.CurrencyHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyHistoryDao {
    @Query("SELECT * FROM currency_history ORDER BY timestamp ASC LIMIT 500")
    fun getAllHistory(): Flow<List<CurrencyHistory>>

    @Query("SELECT * FROM currency_history ORDER BY timestamp DESC LIMIT 1")
    fun getLatestRate(): Flow<CurrencyHistory?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: CurrencyHistory)

    @Query("DELETE FROM currency_history")
    suspend fun deleteAll()
}
