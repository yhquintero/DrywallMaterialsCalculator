package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.UnitType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnitTypeRepository @Inject constructor(private val db: AppDatabase) {
    /**
     * Obtiene el flujo de todos los tipos de unidades registrados en la base de datos.
     *
     * @return Flow que emite una lista de objetos UnitType.
     */
    fun getAllUnitTypes(): Flow<List<UnitType>> = db.unitTypeDao().getAllUnitTypes()

    /**
     * Inserta un nuevo tipo de unidad en la base de datos.
     *
     * @param unitType El objeto UnitType a insertar.
     */
    suspend fun insertUnitType(unitType: UnitType) = db.unitTypeDao().insertUnitType(unitType)

    /**
     * Elimina un tipo de unidad específico de la base de datos.
     *
     * @param unitType El objeto UnitType a eliminar.
     */
    suspend fun deleteUnitType(unitType: UnitType) = db.unitTypeDao().deleteUnitType(unitType)
}
