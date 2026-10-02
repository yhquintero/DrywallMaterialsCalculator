package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "purchase_orders")
data class PurchaseOrder(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val projectName: String,
    val date: Long = System.currentTimeMillis(),
    val status: String = "Pendiente", // Pendiente, Completada
    val notes: String = ""
)

@Entity(tableName = "purchase_order_items")
data class PurchaseOrderItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val purchaseOrderId: String,
    val materialName: String,
    val quantityRequired: Double,
    val quantityAcquired: Double = 0.0
)
