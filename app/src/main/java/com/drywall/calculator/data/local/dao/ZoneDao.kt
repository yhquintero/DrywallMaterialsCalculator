package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.Zone
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones WHERE projectId = :projectId")
    fun getZonesForProject(projectId: String): Flow<List<Zone>>

    @Insert
    suspend fun insertZone(zone: Zone)

    @Query("DELETE FROM zones WHERE projectId = :projectId")
    suspend fun deleteZonesForProject(projectId: String)
}
