package com.drywall.calculator.data.repository

import androidx.room.withTransaction
import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.PriceHistory
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceHistoryRepository @Inject constructor(
    private val db: AppDatabase
) {
    fun getPriceHistoryForMaterial(materialId: String): Flow<List<PriceHistory>> =
        db.priceHistoryDao().getPriceHistoryForMaterial(materialId)

    fun getAllPriceHistory(): Flow<List<PriceHistory>> =
        db.priceHistoryDao().getAllPriceHistory()

    /**
     * Registers a new purchase price with its acquisition date and updates the material's
     * current purchasePrice/salePrice to the most recent record (by date).
     */
    suspend fun addPrice(materialId: String, price: Double, date: Date) {
        require(price > 0) { "El precio debe ser mayor a 0" }
        db.withTransaction {
            db.priceHistoryDao().insertPriceHistory(
                PriceHistory(materialId = materialId, price = price, date = date)
            )
            syncMaterialCurrentPrice(materialId)
        }
    }

    suspend fun updatePrice(entry: PriceHistory, newPrice: Double, newDate: Date) {
        require(newPrice > 0) { "El precio debe ser mayor a 0" }
        db.withTransaction {
            // Se borra y reinserta para que la modificación quede como la más reciente
            // (mayor rowid) y sea tomada como "última actualización".
            db.priceHistoryDao().deletePriceHistory(entry)
            db.priceHistoryDao().insertPriceHistory(
                entry.copy(price = newPrice, date = newDate)
            )
            syncMaterialCurrentPrice(entry.materialId)
        }
    }

    suspend fun deletePrice(entry: PriceHistory) {
        db.withTransaction {
            val remaining = db.priceHistoryDao().getCountForMaterial(entry.materialId)
            require(remaining > 1) { "Debe existir al menos un Precio de Compra. No se puede eliminar el último." }
            db.priceHistoryDao().deletePriceHistory(entry)
            syncMaterialCurrentPrice(entry.materialId)
        }
    }

    /**
     * Sets the material's purchasePrice to the latest (most recent date) history entry and
     * recalculates the sale price using the material's profit percentage.
     */
    private suspend fun syncMaterialCurrentPrice(materialId: String) {
        val latest = db.priceHistoryDao().getLatestPriceForMaterial(materialId) ?: return
        val material = db.materialDao().getMaterialById(materialId) ?: return
        val profit = material.profitPercentage.coerceIn(
            com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT,
            com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT
        )
        val newSale = latest.price * (1 + profit / 100)
        db.materialDao().updateMaterial(
            material.copy(purchasePrice = latest.price, salePrice = newSale)
        )
    }
}
