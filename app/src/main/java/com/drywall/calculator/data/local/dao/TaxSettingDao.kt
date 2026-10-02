package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.TaxSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxSettingDao {
    @Query("SELECT * FROM tax_settings WHERE id = 1")
    fun getTaxSetting(): Flow<TaxSetting?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: TaxSetting)
}
