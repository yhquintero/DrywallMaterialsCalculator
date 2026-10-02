package com.drywall.calculator.domain.measure

data class MeasureValue(
    val dimension: String,
    val value: Double,
    val unit: MeasurementUnit
) {
    fun formatted(): String = "%.2f %s".format(value, unit.symbol)
}

data class MaterialMeasure(
    val id: String,
    val materialId: String,
    val name: String,
    val category: MaterialCategory,
    val values: List<MeasureValue>,
    val tags: List<String>,
    val standard: StandardRegion = StandardRegion.EU,
    val imageRef: String? = null,
    val consumptionRate: Double? = null,
    val description: String = ""
)

data class MaterialItem(
    val id: String,
    val name: String,
    val description: String,
    val colorDescription: String = "",
    val imageRef: String,
    val measures: List<MaterialMeasure>
)

data class MaterialGroup(
    val id: String,
    val name: String,
    val description: String,
    val imageRef: String,
    val category: MaterialCategory,
    val materials: List<MaterialItem>
)
