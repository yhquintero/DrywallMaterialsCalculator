package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.annotation.Keep
import java.util.UUID

@Keep
@Entity(tableName = "cuentas_contables")
data class CuentaContable(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val codigo: String,
    val nombre: String,
    val tipo: String,
    val subtipo: String = "",
    val saldoDebe: Double = 0.0,
    val saldoHaber: Double = 0.0,
    val aft: Double = 0.0,
    val depreciacionAcum: Double = 0.0,
    val descripcion: String = "",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActualizacion: Long = System.currentTimeMillis()
) {
    fun getSaldoNeto(): Double = saldoDebe - saldoHaber

    companion object {
        const val TIPO_ACTIVO = "ACTIVO"
        const val TIPO_PASIVO = "PASIVO"
        const val TIPO_PATRIMONIO = "PATRIMONIO"
        const val TIPO_INGRESO = "INGRESO"
        const val TIPO_GASTO = "GASTO"

        const val SUBTIPO_ACTIVO_CORRIENTE = "ACTIVO_CORRIENTE"
        const val SUBTIPO_ACTIVO_NO_CORRIENTE = "ACTIVO_NO_CORRIENTE"
        const val SUBTIPO_PASIVO_CORRIENTE = "PASIVO_CORRIENTE"
        const val SUBTIPO_PASIVO_NO_CORRIENTE = "PASIVO_NO_CORRIENTE"

        val TIPOS = listOf(TIPO_ACTIVO, TIPO_PASIVO, TIPO_PATRIMONIO, TIPO_INGRESO, TIPO_GASTO)

        val SUBTIPOS_POR_TIPO = mapOf(
            TIPO_ACTIVO to listOf(SUBTIPO_ACTIVO_CORRIENTE, SUBTIPO_ACTIVO_NO_CORRIENTE),
            TIPO_PASIVO to listOf(SUBTIPO_PASIVO_CORRIENTE, SUBTIPO_PASIVO_NO_CORRIENTE),
            TIPO_PATRIMONIO to emptyList(),
            TIPO_INGRESO to emptyList(),
            TIPO_GASTO to emptyList()
        )

        val TIPO_LABELS = mapOf(
            TIPO_ACTIVO to "Activo",
            TIPO_PASIVO to "Pasivo",
            TIPO_PATRIMONIO to "Patrimonio",
            TIPO_INGRESO to "Ingreso",
            TIPO_GASTO to "Gasto"
        )

        val SUBTIPO_LABELS = mapOf(
            SUBTIPO_ACTIVO_CORRIENTE to "Activo Corriente",
            SUBTIPO_ACTIVO_NO_CORRIENTE to "Activo No Corriente",
            SUBTIPO_PASIVO_CORRIENTE to "Pasivo Corriente",
            SUBTIPO_PASIVO_NO_CORRIENTE to "Pasivo No Corriente"
        )

        fun getCatalogoBase(): List<CuentaContable> = listOf(
            CuentaContable(codigo = "1101", nombre = "Caja", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_CORRIENTE, descripcion = "Efectivo y equivalentes"),
            CuentaContable(codigo = "1102", nombre = "Bancos", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_CORRIENTE, descripcion = "Depósitos bancarios"),
            CuentaContable(codigo = "1103", nombre = "Cuentas por Cobrar", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_CORRIENTE, descripcion = "Créditos a favor"),
            CuentaContable(codigo = "1104", nombre = "Mercancías", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_CORRIENTE, descripcion = "Inventario de materiales"),
            CuentaContable(codigo = "1105", nombre = "IVA Acreditable", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_CORRIENTE, descripcion = "Impuesto al Valor Agregado"),
            CuentaContable(codigo = "1201", nombre = "Terrenos", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Propiedad del terreno"),
            CuentaContable(codigo = "1202", nombre = "Edificios", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Construcciones"),
            CuentaContable(codigo = "1203", nombre = "Maquinaria y Equipo", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Equipos de trabajo"),
            CuentaContable(codigo = "1204", nombre = "Mobiliario y Equipo", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Muebles y enseres"),
            CuentaContable(codigo = "1205", nombre = "Vehículos", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Transporte"),
            CuentaContable(codigo = "1206", nombre = "Herramientas", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Herramientas de trabajo"),
            CuentaContable(codigo = "1299", nombre = "Depreciación Acumulada", tipo = TIPO_ACTIVO, subtipo = SUBTIPO_ACTIVO_NO_CORRIENTE, descripcion = "Depreciación acumulada de activos fijos"),
            CuentaContable(codigo = "2101", nombre = "Cuentas por Pagar", tipo = TIPO_PASIVO, subtipo = SUBTIPO_PASIVO_CORRIENTE, descripcion = "Deudas a proveedores"),
            CuentaContable(codigo = "2102", nombre = "IVA por Pagar", tipo = TIPO_PASIVO, subtipo = SUBTIPO_PASIVO_CORRIENTE, descripcion = "Impuesto pendiente"),
            CuentaContable(codigo = "2103", nombre = "Impuestos por Pagar", tipo = TIPO_PASIVO, subtipo = SUBTIPO_PASIVO_CORRIENTE, descripcion = "Impuestos vencidos"),
            CuentaContable(codigo = "2104", nombre = "Sueldos por Pagar", tipo = TIPO_PASIVO, subtipo = SUBTIPO_PASIVO_CORRIENTE, descripcion = "Nómina pendiente"),
            CuentaContable(codigo = "2201", nombre = "Préstamos a Largo Plazo", tipo = TIPO_PASIVO, subtipo = SUBTIPO_PASIVO_NO_CORRIENTE, descripcion = "Deudas bancarias"),
            CuentaContable(codigo = "3101", nombre = "Capital Social", tipo = TIPO_PATRIMONIO, descripcion = "Aportación de los socios"),
            CuentaContable(codigo = "3102", nombre = "Utilidades Retenidas", tipo = TIPO_PATRIMONIO, descripcion = "Ganancias acumuladas"),
            CuentaContable(codigo = "3103", nombre = "Utilidad del Ejercicio", tipo = TIPO_PATRIMONIO, descripcion = "Resultado del período"),
            CuentaContable(codigo = "4101", nombre = "Ventas", tipo = TIPO_INGRESO, descripcion = "Ingresos por ventas"),
            CuentaContable(codigo = "4102", nombre = "Otros Ingresos", tipo = TIPO_INGRESO, descripcion = "Ingresos varios"),
            CuentaContable(codigo = "5101", nombre = "Costo de Ventas", tipo = TIPO_GASTO, descripcion = "Costo de materiales vendidos"),
            CuentaContable(codigo = "5102", nombre = "Gastos de Nómina", tipo = TIPO_GASTO, descripcion = "Sueldos y salarios"),
            CuentaContable(codigo = "5103", nombre = "Gastos de Rentas", tipo = TIPO_GASTO, descripcion = "Alquileres"),
            CuentaContable(codigo = "5104", nombre = "Gastos de Servicios", tipo = TIPO_GASTO, descripcion = "Luz, agua, teléfono"),
            CuentaContable(codigo = "5105", nombre = "Gastos de Transporte", tipo = TIPO_GASTO, descripcion = "Fletes y acarreos"),
            CuentaContable(codigo = "5106", nombre = "Gastos de Mantenimiento", tipo = TIPO_GASTO, descripcion = "Reparaciones"),
            CuentaContable(codigo = "5107", nombre = "Gastos Financieros", tipo = TIPO_GASTO, descripcion = "Intereses y comisiones"),
            CuentaContable(codigo = "5108", nombre = "Gastos de Depreciación", tipo = TIPO_GASTO, descripcion = "Depreciación del período"),
            CuentaContable(codigo = "5109", nombre = "Impuestos", tipo = TIPO_GASTO, descripcion = "Impuestos sobre la renta"),
        )
    }
}
