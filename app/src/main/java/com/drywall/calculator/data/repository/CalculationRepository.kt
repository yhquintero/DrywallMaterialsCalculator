package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.entity.MaterialMeasurement
import com.drywall.calculator.domain.calculator.MaterialAdvisoryEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalculationRepository @Inject constructor() {
    data class MaterialRequirement(
        val materialName: String,
        val quantityPerM2: Double,
        val totalQuantity: Double,
        val unitPrice: Double,
        val totalPrice: Double,
        val isInCatalog: Boolean = true,
        val availableStock: Double = 0.0
    )

    fun calculateRequirements(
        constructionType: String,
        areaM2: Double,
        materialsCatalog: List<com.drywall.calculator.data.local.entity.Material>,
        measurements: MaterialMeasurement? = null
    ): List<MaterialRequirement> {
        val consumption = MaterialAdvisoryEngine.getConsumptionRates(constructionType).toMutableMap()
        
        // Adjust rates based on custom measurements if available
        measurements?.let { m ->
            // Plates / Planchas (standard is 1.2x2.4 = 2.88)
            val plateArea = m.width * m.length
            if (plateArea > 0) {
                consumption.keys.filter { it.contains("Plancha", true) || it.contains("Pladur", true) }.forEach { key ->
                    val originalRate = consumption[key] ?: 0.0
                    // If original rate was based on 2.88m2 per plate
                    consumption[key] = originalRate * (2.88 / plateArea)
                }
            }
            
            // Perfiles (linear meters to pieces based on length)
            if (m.length > 0) {
                val profileKeys = listOf("Perfil Primario", "Perfil Omega", "Canal (U)", "Parante (C)", "Angulo Perimetral", "Perfil Principal", "Perfil Secundario", "Perfil Terciario")
                consumption.keys.filter { key -> profileKeys.any { pKey -> key.contains(pKey, true) } }.forEach { key ->
                    val linearMetersPerM2 = consumption[key] ?: 0.0
                    consumption[key] = linearMetersPerM2 / m.length
                }
            }
            
            // Baldosa (Tile)
            val tileArea = m.width * m.length
            if (tileArea > 0) {
                if (consumption.containsKey("Baldosa Cielo Raso")) {
                    consumption["Baldosa Cielo Raso"] = 1.0 / tileArea
                }
            }
            
            // Aislante (Insulation)
            val rollArea = m.width * m.length
            if (rollArea > 0) {
                val insulationKeys = listOf("Lana de Vidrio", "Aislante")
                consumption.keys.filter { key -> insulationKeys.any { iKey -> key.contains(iKey, true) } }.forEach { key ->
                    val originalRate = consumption[key] ?: 0.0
                    consumption[key] = originalRate / rollArea // Assuming engine base was m2
                }
            }
        }

        return consumption.map { (materialName, rate) ->
            val catalogItem = materialsCatalog.find { it.name.equals(materialName, ignoreCase = true) }
            val totalQty = rate * areaM2
            MaterialRequirement(
                materialName = materialName,
                quantityPerM2 = rate,
                totalQuantity = totalQty,
                unitPrice = catalogItem?.salePrice ?: 0.0,
                totalPrice = totalQty * (catalogItem?.salePrice ?: 0.0),
                isInCatalog = catalogItem != null,
                availableStock = catalogItem?.quantity ?: 0.0
            )
        }
    }
}
