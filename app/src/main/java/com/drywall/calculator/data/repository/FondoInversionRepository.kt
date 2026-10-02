package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.FondoInversion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FondoInversionRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllFondos(): Flow<List<FondoInversion>> = db.fondoInversionDao().getAll()

    fun getFondosByProyecto(proyectoId: String): Flow<List<FondoInversion>> = db.fondoInversionDao().getByProyectoId(proyectoId)

    fun getFondosByPeriodo(mes: Int, anio: Int): Flow<List<FondoInversion>> = db.fondoInversionDao().getByPeriodo(mes, anio)

    suspend fun getFondoById(id: String): FondoInversion? = db.fondoInversionDao().getById(id)

    suspend fun getFondoByProyectoYPeriodo(proyectoId: String, mes: Int, anio: Int): FondoInversion? =
        db.fondoInversionDao().getByProyectoYPeriodo(proyectoId, mes, anio)

    suspend fun saveFondo(fondo: FondoInversion) = db.fondoInversionDao().insert(fondo)

    suspend fun saveAllFondos(fondos: List<FondoInversion>) = db.fondoInversionDao().insertAll(fondos)

    suspend fun updateFondo(fondo: FondoInversion) = db.fondoInversionDao().update(fondo)

    suspend fun deleteFondo(fondo: FondoInversion) = db.fondoInversionDao().delete(fondo)

    suspend fun deleteFondoById(id: String) = db.fondoInversionDao().deleteById(id)

    suspend fun getCount(): Int = db.fondoInversionDao().countAll()

    suspend fun getTotalInversionByProyecto(proyectoId: String): Double =
        db.fondoInversionDao().getTotalInversionByProyecto(proyectoId) ?: 0.0

    suspend fun getTotalInversionByPeriodo(mes: Int, anio: Int): Double =
        db.fondoInversionDao().getTotalInversionByPeriodo(mes, anio) ?: 0.0
}
