package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.PurchaseOrder
import com.drywall.calculator.data.local.entity.PurchaseOrderItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseOrderRepository @Inject constructor(
    private val db: AppDatabase
) {
    fun getAllOrders(): Flow<List<PurchaseOrder>> = db.purchaseOrderDao().getAllOrders()
    
    fun getItemsForOrder(orderId: String): Flow<List<PurchaseOrderItem>> = db.purchaseOrderDao().getItemsForOrder(orderId)

    suspend fun getPendingOrderForProject(projectId: String): PurchaseOrder? = db.purchaseOrderDao().getPendingOrderForProject(projectId)

    suspend fun createOrder(order: PurchaseOrder, items: List<PurchaseOrderItem>) {
        db.purchaseOrderDao().createOrderWithItems(order, items)
    }

    suspend fun deleteOrder(order: PurchaseOrder) {
        db.purchaseOrderDao().deleteOrderWithItems(order)
    }
}
