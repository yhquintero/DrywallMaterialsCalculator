package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.Material
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialRepository @Inject constructor(
    private val db: AppDatabase
) {
    fun getAllMaterials(): Flow<List<Material>> = db.materialDao().getAllMaterials()

    suspend fun getMaterialById(id: String): Material? = db.materialDao().getMaterialById(id)

    fun getMaterialsByProvider(providerId: Int): Flow<List<Material>> = db.materialDao().getMaterialsByProvider(providerId)

    suspend fun insertMaterial(material: Material) {
        require(material.isValidProfitMargin()) { "El margen de beneficio debe estar entre ${com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT}% y ${com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT}%" }
        db.materialDao().insertMaterial(material)
    }

    suspend fun updateMaterial(material: Material) {
        require(material.isValidProfitMargin()) { "El margen de beneficio debe estar entre ${com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MIN_PROFIT}% y ${com.drywall.calculator.utils.AppConfigConstants.DEFAULT_MAX_PROFIT}%" }
        db.materialDao().updateMaterial(material)
    }

    suspend fun deleteMaterial(material: Material) = db.materialDao().deleteMaterial(material)

    suspend fun updateStock(materialId: String, delta: Double) {
        if (delta < 0) {
            val current = db.materialDao().getMaterialById(materialId)
            val newStock = (current?.quantity ?: 0.0) + delta
            require(newStock >= 0) { "No se puede tener stock negativo" }
        }
        db.materialDao().updateStock(materialId, delta)
    }

    suspend fun deleteAllMaterials() = db.materialDao().deleteAllMaterials()
}
