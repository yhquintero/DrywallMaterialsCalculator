package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "inventory_transactions")
data class InventoryTransaction(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val materialId: String,
    val quantityChange: Double,   // positive for stock-in, negative for stock-out (project consumption)
    val reason: String,           // e.g., "Compra", "Proyecto: Cocina"
    val date: Date = Date()
)
