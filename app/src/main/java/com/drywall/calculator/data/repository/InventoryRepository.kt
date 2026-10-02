package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.InventoryTransaction
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val materialRepository: MaterialRepository
) {
    fun getTransactionsForMaterial(materialId: String): Flow<List<InventoryTransaction>> =
        db.inventoryTransactionDao().getTransactionsForMaterial(materialId)

    fun getAllTransactionsForMaterial(materialId: String): Flow<List<InventoryTransaction>> =
        db.inventoryTransactionDao().getAllTransactionsForMaterial(materialId)

    suspend fun addStock(materialId: String, quantity: Double, reason: String, date: Date = Date()) {
        db.withTransaction {
            materialRepository.updateStock(materialId, quantity)
            db.inventoryTransactionDao().insertTransaction(
                InventoryTransaction(
                    materialId = materialId,
                    quantityChange = quantity,
                    reason = reason,
                    date = date
                )
            )
        }
    }

    sealed class ConsumeResult {
        object Success : ConsumeResult()
        data class Error(val message: String) : ConsumeResult()
    }

    private fun dayStartMillis(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun consumeStock(materialId: String, quantity: Double, reason: String, date: Date = Date()): ConsumeResult {
        require(quantity > 0)
        return db.withTransaction {
            val material = db.materialDao().getMaterialById(materialId)
            if (material == null) return@withTransaction ConsumeResult.Error("Material no encontrado")
            
            if (material.quantity < quantity) {
                return@withTransaction ConsumeResult.Error("Stock insuficiente. Disponible: ${material.quantity}")
            }

            val firstEntryMillis = db.inventoryTransactionDao().getFirstEntryDateMillis(materialId)
            if (firstEntryMillis != null && dayStartMillis(date.time) < dayStartMillis(firstEntryMillis)) {
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                return@withTransaction ConsumeResult.Error(
                    "La fecha de salida no puede ser menor a la fecha de entrada del material (" +
                        fmt.format(Date(firstEntryMillis)) + ")"
                )
            }

            materialRepository.updateStock(materialId, -quantity)
            db.inventoryTransactionDao().insertTransaction(
                InventoryTransaction(
                    materialId = materialId,
                    quantityChange = -quantity,
                    reason = reason,
                    date = date
                )
            )
            ConsumeResult.Success
        }
    }

    sealed class DeleteResult {
        data class Success(val message: String) : DeleteResult()
        data class Error(val message: String, val currentStock: Double, val quantityToRevert: Double) : DeleteResult()
    }

    suspend fun deleteTransaction(transactionId: String, materialId: String): DeleteResult {
        val transaction = db.inventoryTransactionDao().getTransactionById(transactionId)
            ?: return DeleteResult.Error("Transacción no encontrada", 0.0, 0.0)

        val material = db.materialDao().getMaterialById(materialId)
            ?: return DeleteResult.Error("Material no encontrado", 0.0, 0.0)

        val currentStock = material.quantity
        val quantityToRevert = transaction.quantityChange

        // If deleting an Entrada (positive quantityChange), check if stock would go negative
        if (quantityToRevert > 0) {
            val newStock = currentStock - quantityToRevert
            if (newStock < 0) {
                val consumed = quantityToRevert - currentStock
                return DeleteResult.Error(
                    message = "No se puede eliminar esta entrada porque ya se consumieron $consumed ${material.unitType} " +
                            "en salidas posteriores. Si se eliminara, el stock quedaría en ${String.format("%.2f", newStock)} ${material.unitType} (negativo).",
                    currentStock = currentStock,
                    quantityToRevert = quantityToRevert
                )
            }
        }

        db.withTransaction {
            // Revert original change (if it was -5, we add 5)
            materialRepository.updateStock(materialId, -quantityToRevert)
            db.inventoryTransactionDao().deleteTransactionById(transactionId)
        }
        return DeleteResult.Success("Transacción eliminada correctamente")
    }

    suspend fun updateTransactionReason(transactionId: String, newReason: String): Boolean {
        val existing = db.inventoryTransactionDao().getTransactionById(transactionId) ?: return false
        val updated = existing.copy(reason = newReason)
        db.withTransaction {
            db.inventoryTransactionDao().deleteTransactionById(transactionId)
            db.inventoryTransactionDao().insertTransaction(updated)
        }
        return true
    }

    sealed class UpdateQuantityResult {
        data class Success(val message: String) : UpdateQuantityResult()
        data class Error(val message: String, val currentStock: Double) : UpdateQuantityResult()
    }

    suspend fun updateTransactionQuantity(transactionId: String, materialId: String, newQuantity: Double): UpdateQuantityResult {
        val transaction = db.inventoryTransactionDao().getTransactionById(transactionId)
            ?: return UpdateQuantityResult.Error("Transacción no encontrada", 0.0)
        val material = db.materialDao().getMaterialById(materialId)
            ?: return UpdateQuantityResult.Error("Material no encontrado", 0.0)

        val originalQty = kotlin.math.abs(transaction.quantityChange)
        val diff = newQuantity - originalQty

        if (transaction.quantityChange > 0) {
            // Entrada
            val newStock = material.quantity + diff
            if (newStock < 0) {
                return UpdateQuantityResult.Error("No se puede reducir: el stock quedaría negativo (${String.format("%.2f", newStock)})", material.quantity)
            }
        } else {
            // Salida
            if (diff > 0) {
                val newStock = material.quantity - diff
                if (newStock < 0) {
                    return UpdateQuantityResult.Error("No se puede aumentar la salida: stock insuficiente (${String.format("%.2f", material.quantity)} ${material.unitType})", material.quantity)
                }
            }
        }

        val newChange = if (transaction.quantityChange > 0) newQuantity else -newQuantity
        val delta = newChange - transaction.quantityChange
        
        val updated = transaction.copy(quantityChange = newChange)
        db.withTransaction {
            // Apply only the difference to avoid temporary negative stock
            materialRepository.updateStock(materialId, delta)
            db.inventoryTransactionDao().updateTransaction(updated)
        }
        return UpdateQuantityResult.Success("Cantidad actualizada correctamente")
    }

    data class StockConsumeRequest(val materialId: String, val quantity: Double, val projectName: String)

    suspend fun consumeMultipleStock(requests: List<StockConsumeRequest>): Int {
        if (requests.isEmpty()) return 0
        return db.withTransaction {
            var count = 0
            for (req in requests) {
                if (req.quantity <= 0) continue
                val material = db.materialDao().getMaterialById(req.materialId)
                if (material != null && material.quantity >= req.quantity) {
                    db.materialDao().updateStock(req.materialId, -req.quantity)
                    db.inventoryTransactionDao().insertTransaction(
                        InventoryTransaction(
                            materialId = req.materialId,
                            quantityChange = -req.quantity,
                            reason = req.projectName
                        )
                    )
                    count++
                }
            }
            count
        }
    }
}
