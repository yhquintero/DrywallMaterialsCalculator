package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "material_measurements")
data class MaterialMeasurement(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val thickness: Double = 0.0,
    val length: Double = 0.0,
    val unitSystem: String = "metric",
    val colorHex: String = "#6200EE"
)
