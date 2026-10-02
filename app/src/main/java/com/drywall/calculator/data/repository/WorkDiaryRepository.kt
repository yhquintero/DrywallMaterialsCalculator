package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.WorkDiary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkDiaryRepository @Inject constructor(private val db: AppDatabase) {
    fun getAll(): Flow<List<WorkDiary>> = db.workDiaryDao().getAll()
    fun getDiaryForProject(projectId: String): Flow<List<WorkDiary>> = db.workDiaryDao().getDiaryForProject(projectId)
    suspend fun insertEntry(entry: WorkDiary) = db.workDiaryDao().insert(entry)
    suspend fun updateEntry(entry: WorkDiary) = db.workDiaryDao().update(entry)
    suspend fun deleteEntry(entry: WorkDiary) = db.workDiaryDao().delete(entry)
}
