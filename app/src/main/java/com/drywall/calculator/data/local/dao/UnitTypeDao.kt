package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.UnitType
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitTypeDao {
    @Query("SELECT * FROM unit_types")
    fun getAllUnitTypes(): Flow<List<UnitType>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnitType(unitType: UnitType)

    @Delete
    suspend fun deleteUnitType(unitType: UnitType)
}
