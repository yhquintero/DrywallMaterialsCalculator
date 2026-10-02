package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.Provider
import com.drywall.calculator.data.local.entity.WorkDiary
import com.drywall.calculator.data.local.entity.ProjectPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY name ASC")
    fun getAllProviders(): Flow<List<Provider>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: Provider)

    @Query("SELECT * FROM providers WHERE name = :name LIMIT 1")
    suspend fun getProviderByName(name: String): Provider?

    @Update
    suspend fun update(provider: Provider)
    @Delete
    suspend fun delete(provider: Provider)
}

@Dao
interface WorkDiaryDao {
    @Query("SELECT * FROM work_diary ORDER BY date DESC LIMIT 500")
    fun getAll(): Flow<List<WorkDiary>>

    @Query("SELECT * FROM work_diary WHERE projectId = :projectId ORDER BY date DESC LIMIT 200")
    fun getDiaryForProject(projectId: String): Flow<List<WorkDiary>>
    @Insert
    suspend fun insert(entry: WorkDiary)
    @Update
    suspend fun update(entry: WorkDiary)
    @Delete
    suspend fun delete(entry: WorkDiary)
}

@Dao
interface ProjectPhotoDao {
    @Query("SELECT * FROM project_photos ORDER BY date DESC LIMIT 500")
    fun getAllPhotos(): Flow<List<ProjectPhoto>>
    @Query("SELECT * FROM project_photos WHERE projectId = :projectId ORDER BY date DESC LIMIT 200")
    fun getPhotosForProject(projectId: String): Flow<List<ProjectPhoto>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: ProjectPhoto)
    @Delete
    suspend fun delete(photo: ProjectPhoto)
}
