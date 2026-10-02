package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.MovimientoContable
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovimientoContableRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllMovimientos(): Flow<List<MovimientoContable>> = db.movimientoContableDao().getAll()

    fun getMovimientosByCuenta(cuentaId: String): Flow<List<MovimientoContable>> = db.movimientoContableDao().getByCuentaId(cuentaId)

    fun getMovimientosByPeriodo(mes: Int, anio: Int): Flow<List<MovimientoContable>> = db.movimientoContableDao().getByPeriodo(mes, anio)

    suspend fun getMovimientoById(id: String): MovimientoContable? = db.movimientoContableDao().getById(id)

    suspend fun saveMovimiento(movimiento: MovimientoContable) = db.movimientoContableDao().insert(movimiento)

    suspend fun saveAllMovimientos(movimientos: List<MovimientoContable>) = db.movimientoContableDao().insertAll(movimientos)

    suspend fun updateMovimiento(movimiento: MovimientoContable) = db.movimientoContableDao().update(movimiento)

    suspend fun deleteMovimiento(movimiento: MovimientoContable) = db.movimientoContableDao().delete(movimiento)

    suspend fun deleteMovimientoById(id: String) = db.movimientoContableDao().deleteById(id)

    suspend fun getCount(): Int = db.movimientoContableDao().countAll()

    suspend fun getTotalDebeByCuentaAndPeriodo(cuentaId: String, mes: Int, anio: Int): Double =
        db.movimientoContableDao().getTotalDebeByCuentaAndPeriodo(cuentaId, mes, anio) ?: 0.0

    suspend fun getTotalHaberByCuentaAndPeriodo(cuentaId: String, mes: Int, anio: Int): Double =
        db.movimientoContableDao().getTotalHaberByCuentaAndPeriodo(cuentaId, mes, anio) ?: 0.0

    suspend fun getTotalDebeByPeriodo(mes: Int, anio: Int): Double =
        db.movimientoContableDao().getTotalDebeByPeriodo(mes, anio) ?: 0.0

    suspend fun getTotalHaberByPeriodo(mes: Int, anio: Int): Double =
        db.movimientoContableDao().getTotalHaberByPeriodo(mes, anio) ?: 0.0
}
