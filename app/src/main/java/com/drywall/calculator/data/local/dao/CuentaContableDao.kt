package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.CuentaContable
import kotlinx.coroutines.flow.Flow

@Dao
interface CuentaContableDao {
    @Query("SELECT * FROM cuentas_contables ORDER BY codigo ASC")
    fun getAll(): Flow<List<CuentaContable>>

    @Query("SELECT * FROM cuentas_contables WHERE tipo = :tipo ORDER BY codigo ASC")
    fun getByTipo(tipo: String): Flow<List<CuentaContable>>

    @Query("SELECT * FROM cuentas_contables WHERE subtipo = :subtipo ORDER BY codigo ASC")
    fun getBySubtipo(subtipo: String): Flow<List<CuentaContable>>

    @Query("SELECT * FROM cuentas_contables WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CuentaContable?

    @Query("SELECT * FROM cuentas_contables WHERE codigo = :codigo LIMIT 1")
    suspend fun getByCodigo(codigo: String): CuentaContable?

    @Query("SELECT * FROM cuentas_contables WHERE nombre LIKE '%' || :query || '%' OR codigo LIKE '%' || :query || '%' ORDER BY codigo ASC")
    fun search(query: String): Flow<List<CuentaContable>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cuenta: CuentaContable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cuentas: List<CuentaContable>)

    @Update
    suspend fun update(cuenta: CuentaContable)

    @Delete
    suspend fun delete(cuenta: CuentaContable)

    @Query("DELETE FROM cuentas_contables WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM cuentas_contables")
    suspend fun countAll(): Int

    @Query("SELECT SUM(saldoDebe) FROM cuentas_contables WHERE tipo = :tipo")
    suspend fun getTotalDebeByTipo(tipo: String): Double?

    @Query("SELECT SUM(saldoHaber) FROM cuentas_contables WHERE tipo = :tipo")
    suspend fun getTotalHaberByTipo(tipo: String): Double?

    @Query("SELECT SUM(saldoDebe) FROM cuentas_contables")
    suspend fun getTotalDebe(): Double?

    @Query("SELECT SUM(saldoHaber) FROM cuentas_contables")
    suspend fun getTotalHaber(): Double?

    @Query("SELECT SUM(aft) FROM cuentas_contables WHERE tipo = 'ACTIVO'")
    suspend fun getTotalAFT(): Double?

    @Query("SELECT SUM(depreciacionAcum) FROM cuentas_contables WHERE tipo = 'ACTIVO'")
    suspend fun getTotalDepreciacionAcum(): Double?

    @Query("SELECT * FROM cuentas_contables WHERE tipo IN ('ACTIVO', 'PASIVO', 'PATRIMONIO') ORDER BY codigo ASC")
    fun getBalanceGeneral(): Flow<List<CuentaContable>>
}
