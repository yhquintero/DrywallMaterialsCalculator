package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.PurchaseOrder
import com.drywall.calculator.data.local.entity.PurchaseOrderItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseOrderDao {
    @Query("SELECT * FROM purchase_orders ORDER BY date DESC")
    fun getAllOrders(): Flow<List<PurchaseOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: PurchaseOrder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PurchaseOrderItem>)

    @Query("SELECT * FROM purchase_order_items WHERE purchaseOrderId = :orderId")
    fun getItemsForOrder(orderId: String): Flow<List<PurchaseOrderItem>>

    @Query("SELECT * FROM purchase_orders WHERE projectId = :projectId AND status = 'Pendiente' LIMIT 1")
    suspend fun getPendingOrderForProject(projectId: String): PurchaseOrder?

    @Delete
    suspend fun deleteOrder(order: PurchaseOrder)
    
    @Query("DELETE FROM purchase_order_items WHERE purchaseOrderId = :orderId")
    suspend fun deleteItemsForOrder(orderId: String)

    @Transaction
    suspend fun createOrderWithItems(order: PurchaseOrder, items: List<PurchaseOrderItem>) {
        insertOrder(order)
        insertItems(items)
    }

    @Transaction
    suspend fun deleteOrderWithItems(order: PurchaseOrder) {
        deleteItemsForOrder(order.id)
        deleteOrder(order)
    }
}
