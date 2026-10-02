package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import java.util.UUID

@Keep
@Entity(tableName = "income_statements")
data class IncomeStatement(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val periodType: String,
    val periodLabel: String,

    val startDate: Long,
    val endDate: Long,

    val proyectoId: String = "",
    val proyectoNombre: String = "",

    val ventas: Double = 0.0,

    val costosVentas: Double = 0.0,
    val utilidadBruta: Double = 0.0,

    val gastosOperativos: Double = 0.0,
    val utilidadOperativa: Double = 0.0,

    val gastosFinancieros: Double = 0.0,
    val utilidadAntesImpuestos: Double = 0.0,

    val impuestos: Double = 0.0,
    val impuestoPorcentaje: Double = 0.0,
    val impuestoCalculadoAutomatico: Boolean = true,
    val utilidadNeta: Double = 0.0,

    val moneda: String = "USD",

    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActualizacion: Long = System.currentTimeMillis(),
    val notas: String = ""
) {
    fun recalcular(): IncomeStatement {
        val utilidadBrutaCalc = ventas - costosVentas
        val utilidadOperativaCalc = utilidadBrutaCalc - gastosOperativos
        val utilidadAntesImpuestosCalc = utilidadOperativaCalc - gastosFinancieros

        val impuestosCalc = if (impuestoCalculadoAutomatico && impuestoPorcentaje > 0) {
            utilidadAntesImpuestosCalc * (impuestoPorcentaje / 100)
        } else {
            impuestos
        }

        val utilidadNetaCalc = utilidadAntesImpuestosCalc - impuestosCalc

        return copy(
            utilidadBruta = utilidadBrutaCalc,
            utilidadOperativa = utilidadOperativaCalc,
            utilidadAntesImpuestos = utilidadAntesImpuestosCalc,
            impuestos = impuestosCalc,
            utilidadNeta = utilidadNetaCalc,
            fechaActualizacion = System.currentTimeMillis()
        )
    }

    fun validar(): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (ventas < 0) errors.add("Las ventas no pueden ser negativas")
        if (costosVentas < 0) errors.add("Los costos de ventas no pueden ser negativos")
        if (gastosOperativos < 0) errors.add("Los gastos operativos no pueden ser negativos")
        if (gastosFinancieros < 0) errors.add("Los gastos financieros no pueden ser negativos")
        if (impuestos < 0) errors.add("Los impuestos no pueden ser negativos")
        if (impuestoPorcentaje < 0 || impuestoPorcentaje > 100) errors.add("El porcentaje de impuesto debe estar entre 0 y 100")

        if (costosVentas > ventas) warnings.add("Los costos de ventas superan a las ventas (pérdida bruta)")
        if (gastosOperativos > utilidadBruta) warnings.add("Los gastos operativos superan la utilidad bruta (pérdida operativa)")
        if (gastosFinancieros > utilidadOperativa) warnings.add("Los gastos financieros superan la utilidad operativa")
        if (impuestos > utilidadAntesImpuestos) warnings.add("Los impuestos superan la utilidad antes de impuestos")

        return ValidationResult(errors, warnings)
    }

    data class ValidationResult(
        val errors: List<String>,
        val warnings: List<String>
    ) {
        val isValid: Boolean = errors.isEmpty()
        val hasWarnings: Boolean = warnings.isNotEmpty()
    }

    companion object {
        const val PERIOD_DIARIO = "DIARIO"
        const val PERIOD_SEMANAL = "SEMANAL"
        const val PERIOD_MENSUAL = "MENSUAL"
        const val PERIOD_TRIMESTRAL = "TRIMESTRAL"
        const val PERIOD_SEMESTRAL = "SEMESTRAL"
        const val PERIOD_ANUAL = "ANUAL"
        const val PERIOD_PERSONALIZADO = "PERSONALIZADO"

        val PERIOD_TYPES = listOf(
            PERIOD_DIARIO, PERIOD_SEMANAL, PERIOD_MENSUAL,
            PERIOD_TRIMESTRAL, PERIOD_SEMESTRAL, PERIOD_ANUAL, PERIOD_PERSONALIZADO
        )

        val PERIOD_LABELS = mapOf(
            PERIOD_DIARIO to "Diario",
            PERIOD_SEMANAL to "Semanal",
            PERIOD_MENSUAL to "Mensual",
            PERIOD_TRIMESTRAL to "Trimestral",
            PERIOD_SEMESTRAL to "Semestral",
            PERIOD_ANUAL to "Anual",
            PERIOD_PERSONALIZADO to "Personalizado"
        )

        fun getAllPeriodTypes(): List<String> = PERIOD_TYPES

        fun getPeriodLabel(periodType: String): String =
            PERIOD_LABELS[periodType] ?: periodType
    }
}

enum class PeriodType(val label: String, val description: String) {
    DIARIO("Diario", "Análisis día a día"),
    SEMANAL("Semanal", "Análisis semana a semana"),
    MENSUAL("Mensual", "Análisis mes a mes"),
    TRIMESTRAL("Trimestral", "Análisis trimestre a trimestre"),
    SEMESTRAL("Semestral", "Análisis semestre a semestre"),
    ANUAL("Anual", "Análisis año a año"),
    PERSONALIZADO("Personalizado", "Rango de fechas específico");

    companion object {
        fun fromString(value: String): PeriodType =
            entries.firstOrNull { it.name == value } ?: PERSONALIZADO

        fun allLabels(): List<String> = entries.map { it.label }
    }
}
