package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unit_types")
data class UnitType(
    @PrimaryKey
    val name: String,
    val isCustom: Boolean = true
)
