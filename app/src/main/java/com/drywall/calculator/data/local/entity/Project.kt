package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import java.util.UUID

@Keep
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val clientName: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val address: String = "",
    val totalAreaM2: Double = 0.0,
    val height: Double = 0.0,
    val length: Double = 0.0,
    val width: Double = 0.0,
    val constructionType: String = "",
    val specialPartsJson: String = "", // JSON storage for ConstructionBlock list
    val specialRate: Double = 0.0,
    val specialCurrency: String = "USD",
    val status: String = "Pendiente", // "Pendiente", "En Progreso", "Finalizado"
    val date: Long = System.currentTimeMillis()
)
