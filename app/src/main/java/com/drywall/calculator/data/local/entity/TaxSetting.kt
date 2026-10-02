package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tax_settings")
data class TaxSetting(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,
    val country: String,
    val taxType: String,   // e.g., "IVA", "GST", "VAT", "Sales Tax"
    val percentage: Double
)
