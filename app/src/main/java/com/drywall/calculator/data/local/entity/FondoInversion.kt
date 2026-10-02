package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import java.util.UUID

@Keep
@Entity(tableName = "fondos_inversion")
data class FondoInversion(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val proyectoId: String,
    val proyectoNombre: String = "",
    val inversionMateriales: Double = 0.0,
    val inversionManoObra: Double = 0.0,
    val inversionTransporte: Double = 0.0,
    val inversionEquipos: Double = 0.0,
    val inversionOtros: Double = 0.0,
    val totalInversion: Double = 0.0,
    val moneda: String = "USD",
    val mes: Int,
    val anio: Int,
    val notas: String = "",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActualizacion: Long = System.currentTimeMillis()
) {
    fun calcularTotal(): Double =
        inversionMateriales + inversionManoObra + inversionTransporte + inversionEquipos + inversionOtros

    companion object {
        val MONTH_LABELS = listOf(
            "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )

        fun getPeriodLabel(mes: Int, anio: Int): String {
            val monthName = MONTH_LABELS.getOrElse(mes) { "?" }
            return "$monthName $anio"
        }
    }
}
