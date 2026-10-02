package com.drywall.calculator.domain.calculator

object MaterialAdvisoryEngine {
    // Professional consumption rates per m² based on international standards (e.g., USG, Knauf, Placo)
    private val consumptionRates = mapOf(
        "Techo ST (Estandar)" to mapOf(
            "Planchas de Pladur" to 0.35,      // 1 sheet (1.2x2.4) = 2.88m2. 1/2.88 = 0.347
            "Perfil Primario" to 0.85,         // linear meters
            "Perfil Omega" to 1.1,             // linear meters
            "Tornillo T1 (comecocos)" to 18.0, 
            "Tornillo T2 (plancha)" to 24.0,
            "Cinta de Papel" to 1.5,           // meters
            "Masilla" to 0.9,                  // kg
            "Fulminantes y Clavos" to 1.5      // sets
        ),
        "Techo RH (Humedad)" to mapOf(
            "Planchas RH (Verde)" to 0.35,
            "Perfil Primario" to 0.85,
            "Perfil Omega" to 1.1,
            "Tornillo T1" to 18.0,
            "Tornillo T2 (Zincado)" to 24.0,
            "Cinta de Papel" to 1.5,
            "Masilla RH" to 1.0,
            "Fulminantes y Clavos" to 1.5
        ),
        "Muro Sencillo (Una cara)" to mapOf(
            "Planchas de Pladur" to 0.35,
            "Canal (U)" to 0.8,
            "Parante (C)" to 1.2,
            "Tornillo T1" to 12.0,
            "Tornillo T2" to 20.0,
            "Cinta de Papel" to 1.5,
            "Masilla" to 0.8
        ),
        "Tabique Divisor (Dos caras)" to mapOf(
            "Planchas de Pladur" to 0.7,
            "Canal (U)" to 0.8,
            "Parante (C)" to 1.8,
            "Tornillo T1" to 14.0,
            "Tornillo T2" to 42.0,
            "Cinta de Papel" to 3.0,
            "Masilla" to 1.6,
            "Lana de Vidrio" to 1.05           // m2
        ),
        "Muro RF (Fuego)" to mapOf(
            "Planchas RF (Roja)" to 0.7,
            "Canal (U)" to 0.8,
            "Parante (C)" to 2.4,
            "Tornillo T2 (Largo)" to 48.0,
            "Cinta de Fibra de Vidrio" to 3.0,
            "Masilla RF" to 1.8,
            "Sello Cortafuego" to 0.1
        ),
        "Plafón Reticulado (60x60)" to mapOf(
            "Baldosa Cielo Raso" to 2.8,       // units (1 / 0.36m2)
            "Perfil Principal 3.66m" to 0.23,  // linear m per m2
            "Perfil Secundario 1.22m" to 1.4,
            "Perfil Terciario 0.61m" to 0.7,
            "Angulo Perimetral" to 0.6,
            "Alambre Galvanizado #12" to 0.8,  // meters
            "Fijaciones (Clavo/Pin)" to 1.2
        ),
        "Cajillo / Viga Drywall" to mapOf(
            "Planchas de Pladur" to 0.45,
            "Canal (U)" to 1.5,
            "Parante (C)" to 2.0,
            "Esquinero Metálico" to 1.2,       // meters
            "Tornillo T1" to 25.0,
            "Tornillo T2" to 30.0,
            "Masilla" to 1.2
        ),
        "Fachada EIFS (Global)" to mapOf(
            "Plancha OSB 11.1mm" to 0.35,
            "Barrera de Humedad (Tyvek)" to 1.1,
            "Plancha EPS (Poliestireno)" to 1.05,
            "Malla de Fibra de Vidrio" to 1.2,
            "Base Coat (Adhesivo)" to 5.0,      // kg
            "Arandelas de Fijación" to 8.0,
            "Acabado Texturizado" to 2.5       // kg
        ),
        "Steel Framing (Estructural)" to mapOf(
            "Perfil PGC (C) 100x0.9" to 1.5,
            "Perfil PGU (U) 100x0.9" to 0.8,
            "Tornillo Hexagonal 14x3/4" to 15.0,
            "Cruz de San Andrés (Fleje)" to 0.5,
            "Anclaje Químico" to 0.05
        ),
        "Especial (Multi-Partes)" to emptyMap()
    )

    fun getConsumptionRates(constructionType: String): Map<String, Double> {
        return consumptionRates[constructionType] ?: emptyMap()
    }

    fun getAllConstructionTypes(): List<String> {
        return consumptionRates.keys.toList()
    }

    fun getAdvisoryText(constructionType: String, areaM2: Double): String {
        return when {
            constructionType.contains("Techo") -> "Para techos, asegure una nivelación láser. Los parantes primarios deben estar a 1.22m y los omegas a 0.407m (estándar internacional)."
            constructionType.contains("Muro") || constructionType.contains("Tabique") -> "En muros, los parantes deben colocarse cada 0.407m o 0.61m según la altura. No olvide el refuerzo para puertas."
            constructionType.contains("Reticulado") -> "Verifique la escuadra de la habitación antes de comenzar. El primer perfil principal define la alineación de toda la rejilla."
            else -> "Asegure la estructura con fijaciones adecuadas al sustrato (concreto o metal). Siempre use cinta para evitar fisuras."
        } + " Datos calculados para ${areaM2}m² (incluye 5% de desperdicio técnico)."
    }
}
