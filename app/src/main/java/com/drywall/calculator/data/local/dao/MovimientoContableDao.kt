package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.MovimientoContable
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoContableDao {
    @Query("SELECT * FROM movimientos_contables ORDER BY fecha DESC")
    fun getAll(): Flow<List<MovimientoContable>>

    @Query("SELECT * FROM movimientos_contables WHERE cuentaContableId = :cuentaId ORDER BY fecha DESC")
    fun getByCuentaId(cuentaId: String): Flow<List<MovimientoContable>>

    @Query("SELECT * FROM movimientos_contables WHERE mes = :mes AND anio = :anio ORDER BY fecha DESC")
    fun getByPeriodo(mes: Int, anio: Int): Flow<List<MovimientoContable>>

    @Query("SELECT * FROM movimientos_contables WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MovimientoContable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movimiento: MovimientoContable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movimientos: List<MovimientoContable>)

    @Update
    suspend fun update(movimiento: MovimientoContable)

    @Delete
    suspend fun delete(movimiento: MovimientoContable)

    @Query("DELETE FROM movimientos_contables WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT SUM(montoDebe) FROM movimientos_contables WHERE cuentaContableId = :cuentaId AND mes = :mes AND anio = :anio")
    suspend fun getTotalDebeByCuentaAndPeriodo(cuentaId: String, mes: Int, anio: Int): Double?

    @Query("SELECT SUM(montoHaber) FROM movimientos_contables WHERE cuentaContableId = :cuentaId AND mes = :mes AND anio = :anio")
    suspend fun getTotalHaberByCuentaAndPeriodo(cuentaId: String, mes: Int, anio: Int): Double?

    @Query("SELECT SUM(montoDebe) FROM movimientos_contables WHERE mes = :mes AND anio = :anio")
    suspend fun getTotalDebeByPeriodo(mes: Int, anio: Int): Double?

    @Query("SELECT SUM(montoHaber) FROM movimientos_contables WHERE mes = :mes AND anio = :anio")
    suspend fun getTotalHaberByPeriodo(mes: Int, anio: Int): Double?

    @Query("SELECT COUNT(*) FROM movimientos_contables")
    suspend fun countAll(): Int
}
