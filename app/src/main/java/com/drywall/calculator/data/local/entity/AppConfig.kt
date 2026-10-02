package com.drywall.calculator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

import com.drywall.calculator.utils.AppConfigConstants

@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey
    val id: Int = 1,
    val selectedShortcuts: List<String> = listOf("calculator", "inventory", "materials", "projects", "pdf"),
    val dashboardShortcuts: List<String> = listOf(
        "projects", "materials", "calculator", "inventory", "diary",
        "clients", "company", "bank", "orders", "labor", "measurements",
    ),
    val isDarkMode: Boolean? = null, // null means system default
    val decimalPrecision: Int = 2,
    @ColumnInfo(name = "minProfitMargin")
    val minProfitMargin: Double = AppConfigConstants.DEFAULT_MIN_PROFIT,
    @ColumnInfo(name = "maxProfitMargin")
    val maxProfitMargin: Double = AppConfigConstants.DEFAULT_MAX_PROFIT
) {
    companion object {
        fun createDefault() = AppConfig()
    }
}
