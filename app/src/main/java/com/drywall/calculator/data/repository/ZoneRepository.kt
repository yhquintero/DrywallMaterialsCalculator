package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.Zone
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(private val db: AppDatabase) {
    fun getZonesForProject(projectId: String): Flow<List<Zone>> = db.zoneDao().getZonesForProject(projectId)
    suspend fun deleteZonesForProject(projectId: String) = db.zoneDao().deleteZonesForProject(projectId)
    suspend fun insertZone(zone: Zone) = db.zoneDao().insertZone(zone)
}
