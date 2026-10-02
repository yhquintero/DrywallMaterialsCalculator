package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.CuentaContable
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuentaContableRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllCuentas(): Flow<List<CuentaContable>> = db.cuentaContableDao().getAll()

    fun getCuentasByTipo(tipo: String): Flow<List<CuentaContable>> = db.cuentaContableDao().getByTipo(tipo)

    fun getCuentasBySubtipo(subtipo: String): Flow<List<CuentaContable>> = db.cuentaContableDao().getBySubtipo(subtipo)

    fun searchCuentas(query: String): Flow<List<CuentaContable>> = db.cuentaContableDao().search(query)

    fun getBalanceGeneral(): Flow<List<CuentaContable>> = db.cuentaContableDao().getBalanceGeneral()

    suspend fun getCuentaById(id: String): CuentaContable? = db.cuentaContableDao().getById(id)

    suspend fun getCuentaByCodigo(codigo: String): CuentaContable? = db.cuentaContableDao().getByCodigo(codigo)

    suspend fun saveCuenta(cuenta: CuentaContable) = db.cuentaContableDao().insert(cuenta)

    suspend fun saveAllCuentas(cuentas: List<CuentaContable>) = db.cuentaContableDao().insertAll(cuentas)

    suspend fun updateCuenta(cuenta: CuentaContable) = db.cuentaContableDao().update(cuenta)

    suspend fun deleteCuenta(cuenta: CuentaContable) = db.cuentaContableDao().delete(cuenta)

    suspend fun deleteCuentaById(id: String) = db.cuentaContableDao().deleteById(id)

    suspend fun getCount(): Int = db.cuentaContableDao().countAll()

    suspend fun getTotalDebe(): Double = db.cuentaContableDao().getTotalDebe() ?: 0.0

    suspend fun getTotalHaber(): Double = db.cuentaContableDao().getTotalHaber() ?: 0.0

    suspend fun getTotalAFT(): Double = db.cuentaContableDao().getTotalAFT() ?: 0.0

    suspend fun getTotalDepreciacionAcum(): Double = db.cuentaContableDao().getTotalDepreciacionAcum() ?: 0.0

    suspend fun getTotalDebeByTipo(tipo: String): Double = db.cuentaContableDao().getTotalDebeByTipo(tipo) ?: 0.0

    suspend fun getTotalHaberByTipo(tipo: String): Double = db.cuentaContableDao().getTotalHaberByTipo(tipo) ?: 0.0

    suspend fun seedCatalogoBase() {
        val count = db.cuentaContableDao().countAll()
        if (count == 0) {
            db.cuentaContableDao().insertAll(CuentaContable.getCatalogoBase())
        }
    }
}
