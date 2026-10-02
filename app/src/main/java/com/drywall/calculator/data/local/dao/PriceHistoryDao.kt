package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.PriceHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceHistoryDao {
    @Query("SELECT * FROM price_history WHERE materialId = :materialId ORDER BY date ASC, rowid ASC")
    fun getPriceHistoryForMaterial(materialId: String): Flow<List<PriceHistory>>

    @Query("SELECT * FROM price_history ORDER BY date DESC, rowid DESC")
    fun getAllPriceHistory(): Flow<List<PriceHistory>>

    @Query("SELECT * FROM price_history WHERE materialId = :materialId ORDER BY date DESC, rowid DESC LIMIT 1")
    suspend fun getLatestPriceForMaterial(materialId: String): PriceHistory?

    @Query("SELECT COUNT(*) FROM price_history WHERE materialId = :materialId")
    suspend fun getCountForMaterial(materialId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(entry: PriceHistory)

    @Update
    suspend fun updatePriceHistory(entry: PriceHistory)

    @Delete
    suspend fun deletePriceHistory(entry: PriceHistory)

    @Query("DELETE FROM price_history WHERE materialId = :materialId")
    suspend fun deleteAllForMaterial(materialId: String)
}
