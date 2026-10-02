package com.drywall.calculator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import com.drywall.calculator.utils.AppConfigConstants
import java.util.UUID

@Keep
@Entity(tableName = "materials")
data class Material(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "quantity")
    var quantity: Double = 0.0,
    @ColumnInfo(name = "purchasePrice")
    val purchasePrice: Double = 0.0,
    @ColumnInfo(name = "salePrice")
    var salePrice: Double = 0.0,
    @ColumnInfo(name = "profitPercentage")
    val profitPercentage: Double = 0.0,
    @ColumnInfo(name = "unitType")
    val unitType: String = "unidad",   // e.g., "unidad", "kg", "m²", "rollo", "caja"
    @ColumnInfo(name = "providerId")
    val providerId: Int? = null        // Link to Provider.id
) {
    fun isValidProfitMargin(): Boolean {
        if (purchasePrice <= 0) return false
        val margin = (salePrice - purchasePrice) / purchasePrice * 100
        return margin in AppConfigConstants.DEFAULT_MIN_PROFIT..AppConfigConstants.DEFAULT_MAX_PROFIT
    }

    fun recalculateSalePrice(profitPercent: Double): Double {
        val validProfit = profitPercent.coerceIn(AppConfigConstants.DEFAULT_MIN_PROFIT, AppConfigConstants.DEFAULT_MAX_PROFIT)
        return purchasePrice * (1 + validProfit / 100)
    }
}
