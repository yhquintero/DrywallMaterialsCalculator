package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import java.util.UUID

@Keep
@Entity(tableName = "movimientos_contables")
data class MovimientoContable(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val cuentaContableId: String,
    val fecha: Long = System.currentTimeMillis(),
    val descripcion: String,
    val montoDebe: Double = 0.0,
    val montoHaber: Double = 0.0,
    val referencia: String = "",
    val mes: Int,
    val anio: Int,
    val fechaCreacion: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromFecha(fecha: Long): Pair<Int, Int> {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = fecha
            return Pair(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))
        }
    }
}
