package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
data class Provider(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val contactName: String,
    val phone: String,
    val email: String,
    val category: String,
    val rating: Int = 5,
    val notes: String = ""
)

@Entity(tableName = "work_diary")
data class WorkDiary(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: String,
    val date: Long = System.currentTimeMillis(),
    val activity: String,
    val progressPercent: Int,
    val weather: String = "Soleado",
    val observations: String = ""
)

@Entity(tableName = "project_photos")
data class ProjectPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: String,
    val photoUri: String,
    val date: Long = System.currentTimeMillis(),
    val description: String = ""
)
