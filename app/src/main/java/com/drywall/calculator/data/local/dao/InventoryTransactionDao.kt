package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.InventoryTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryTransactionDao {
    @Query("SELECT * FROM inventory_transactions WHERE materialId = :materialId ORDER BY date DESC LIMIT 200")
    fun getTransactionsForMaterial(materialId: String): Flow<List<InventoryTransaction>>

    @Query("SELECT * FROM inventory_transactions WHERE materialId = :materialId ORDER BY date ASC")
    fun getAllTransactionsForMaterial(materialId: String): Flow<List<InventoryTransaction>>

    @Query("SELECT MIN(date) FROM inventory_transactions WHERE materialId = :materialId AND quantityChange > 0")
    suspend fun getFirstEntryDateMillis(materialId: String): Long?

    @Insert
    suspend fun insertTransaction(transaction: InventoryTransaction)

    @Query("DELETE FROM inventory_transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    @Query("SELECT * FROM inventory_transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: String): InventoryTransaction?

    @Update
    suspend fun updateTransaction(transaction: InventoryTransaction)
}
