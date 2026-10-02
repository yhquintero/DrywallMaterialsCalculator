package com.drywall.calculator.domain.measure

enum class MeasurementUnit(val symbol: String, val description: String) {
    MM("mm", "Mil\u00edmetros"),
    CM("cm", "Cent\u00edmetros"),
    M("m", "Metros"),
    M2("m\u00b2", "Metros cuadrados"),
    M3("m\u00b3", "Metros c\u00fabicos"),
    KG("kg", "Kilogramos"),
    L("L", "Litros"),
    PZA("pza", "Piezas"),
    ROLLO("rollo", "Rollo"),
    CAJA("caja", "Caja"),
    SACO("saco", "Saco"),
    CUBO("cubo", "Cubo"),
    IN("in", "Pulgadas"),
    FT("ft", "Pies"),
    GA("ga", "Gauge (calibre)"),
    ML("ml", "Metros lineales"),
    UNKNOWN("\u2014", "Sin especificar")
}

enum class StandardRegion(val code: String, val displayName: String) {
    EU("EU", "Europeo (EN)"),
    USA("US", "Americano (ASTM)"),
    ASIA("AS", "Asi\u00e1tico (JIS/GB)"),
    ALL("ALL", "Todas las regiones")
}

enum class MaterialCategory(
    val id: String,
    val displayName: String,
    val iconName: String,
    val description: String
) {
    PLASTERBOARD("CAT-001", "Placas de Yeso", "ic_category_plasterboard",
        "Placas de yeso laminado (Pladur) para revestimiento de muros, techos y tabiques"),
    PROFILES("CAT-002", "Perfiles Met\u00e1licos", "ic_category_profiles",
        "Perfiles de acero galvanizado para estructuras de muros y techos"),
    FASTENERS("CAT-003", "Torniller\u00eda", "ic_category_fasteners",
        "Tornillos, clavos, anclajes y fijaciones para sistemas Drywall"),
    INSULATION("CAT-004", "Aislamientos", "ic_category_insulation",
        "Lanas minerales, paneles r\u00edgidos y barreras para aislamiento t\u00e9rmico y ac\u00fastico"),
    COMPOUNDS("CAT-005", "Masillas y Pastas", "ic_category_compounds",
        "Masillas de juntas, acabados, pastas niveladoras y adhesivos"),
    TAPES("CAT-006", "Cintas", "ic_category_tapes",
        "Cintas de papel, fibra de vidrio, malla autoadhesiva y sellado"),
    BEADS("CAT-007", "Esquineros", "ic_category_beads",
        "Perfiles esquinero, guardacantos y protecciones de borde"),
    CEILINGS("CAT-008", "Techos Registrables", "ic_category_ceilings",
        "Sistemas de techos suspendidos, perfiles T y baldosas de cielo raso"),
    ACCESSORIES("CAT-009", "Complementos", "ic_category_accessories",
        "Anclajes, alambres, adhesivos y accesorios diversos")
}
