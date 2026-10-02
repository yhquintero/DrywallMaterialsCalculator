package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.Material
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY name")
    fun getAllMaterials(): Flow<List<Material>>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: String): Material?

    @Query("SELECT * FROM materials WHERE providerId = :providerId")
    fun getMaterialsByProvider(providerId: Int): Flow<List<Material>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: Material)

    @Query("SELECT * FROM materials WHERE name = :name LIMIT 1")
    suspend fun getMaterialByName(name: String): Material?

    @Update
    suspend fun updateMaterial(material: Material)

    @Delete
    suspend fun deleteMaterial(material: Material)

    @Query("UPDATE materials SET quantity = quantity + :delta WHERE id = :materialId")
    suspend fun updateStock(materialId: String, delta: Double)

    @Query("DELETE FROM materials")
    suspend fun deleteAllMaterials()
}
