package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllProjects(): Flow<List<Project>> = db.projectDao().getAllProjects()
    suspend fun getProjectByName(name: String) = db.projectDao().getProjectByName(name)
    suspend fun insert(project: Project) = db.projectDao().insert(project)
    suspend fun update(project: Project) = db.projectDao().update(project)
    suspend fun delete(project: Project) = db.projectDao().delete(project)
}
