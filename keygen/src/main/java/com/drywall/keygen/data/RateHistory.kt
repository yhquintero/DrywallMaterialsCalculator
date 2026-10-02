package com.drywall.keygen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rate_history")
data class RateHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val currencyCode: String,
    val rate: Double,
    val timestamp: Long = System.currentTimeMillis()
)
