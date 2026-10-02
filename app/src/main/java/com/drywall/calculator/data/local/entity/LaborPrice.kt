package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labor_prices")
data class LaborPrice(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,
    val interiorDrywall: Double,      // purchase price
    val exteriorDrywall: Double,
    val dropCeiling: Double,
    val tileCeiling: Double,
    val profitPercent: Double          // enforced 25-30%
) {
    fun salePriceInterior(): Double = interiorDrywall * (1 + profitPercent / 100)
    fun salePriceExterior(): Double = exteriorDrywall * (1 + profitPercent / 100)
    fun salePriceDropCeiling(): Double = dropCeiling * (1 + profitPercent / 100)
    fun salePriceTileCeiling(): Double = tileCeiling * (1 + profitPercent / 100)
}
