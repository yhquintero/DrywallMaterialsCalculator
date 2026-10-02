package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.LaborPrice
import kotlinx.coroutines.flow.Flow

@Dao
interface LaborPriceDao {
    @Query("SELECT * FROM labor_prices WHERE id = 1")
    fun getLaborPrices(): Flow<LaborPrice?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prices: LaborPrice)
}
