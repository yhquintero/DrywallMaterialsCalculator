package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.ProjectPhoto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectPhotoRepository @Inject constructor(private val db: AppDatabase) {
    fun getPhotosForProject(projectId: String): Flow<List<ProjectPhoto>> = db.projectPhotoDao().getPhotosForProject(projectId)
    suspend fun insert(photo: ProjectPhoto) = db.projectPhotoDao().insert(photo)
    suspend fun delete(photo: ProjectPhoto) = db.projectPhotoDao().delete(photo)
}
