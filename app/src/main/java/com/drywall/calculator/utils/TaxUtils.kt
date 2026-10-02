package com.drywall.calculator.utils

object TaxUtils {
    private val taxMap = mapOf(
        "España" to listOf("IVA (21%)", "IVA Reducido (10%)", "IVA Superreducido (4%)"),
        "México" to listOf("IVA (16%)", "IVA Fronterizo (8%)", "IEPS"),
        "Argentina" to listOf("IVA (21%)", "IVA Reducido (10.5%)"),
        "Chile" to listOf("IVA (19%)"),
        "Colombia" to listOf("IVA (19%)", "IVA Reducido (5%)"),
        "Estados Unidos" to listOf("Sales Tax", "Use Tax"),
        "Reino Unido" to listOf("VAT (20%)", "VAT Reduced (5%)"),
        "Canadá" to listOf("GST", "HST", "PST", "QST"),
        "Perú" to listOf("IGV (18%)"),
        "Ecuador" to listOf("IVA (15%)"),
        "Uruguay" to listOf("IVA (22%)", "IVA Mínimo (10%)"),
        "Paraguay" to listOf("IVA (10%)", "IVA (5%)"),
        "Bolivia" to listOf("IVA (13%)"),
        "Venezuela" to listOf("IVA (16%)"),
        "Panamá" to listOf("ITBMS (7%)"),
        "Costa Rica" to listOf("IVA (13%)"),
        "Guatemala" to listOf("IVA (12%)"),
        "Honduras" to listOf("ISV (15%)"),
        "El Salvador" to listOf("IVA (13%)"),
        "Nicaragua" to listOf("IVA (15%)"),
        "República Dominicana" to listOf("ITBIS (18%)")
    )

    fun getTaxTypesForCountry(country: String): List<String> {
        return taxMap[country] ?: listOf("IVA", "GST", "Sales Tax", "VAT")
    }
}
