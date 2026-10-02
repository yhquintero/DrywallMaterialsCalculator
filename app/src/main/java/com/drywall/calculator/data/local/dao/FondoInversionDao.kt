package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.FondoInversion
import kotlinx.coroutines.flow.Flow

@Dao
interface FondoInversionDao {
    @Query("SELECT * FROM fondos_inversion ORDER BY fechaCreacion DESC")
    fun getAll(): Flow<List<FondoInversion>>

    @Query("SELECT * FROM fondos_inversion WHERE proyectoId = :proyectoId ORDER BY fechaCreacion DESC")
    fun getByProyectoId(proyectoId: String): Flow<List<FondoInversion>>

    @Query("SELECT * FROM fondos_inversion WHERE mes = :mes AND anio = :anio ORDER BY fechaCreacion DESC")
    fun getByPeriodo(mes: Int, anio: Int): Flow<List<FondoInversion>>

    @Query("SELECT * FROM fondos_inversion WHERE proyectoId = :proyectoId AND mes = :mes AND anio = :anio LIMIT 1")
    suspend fun getByProyectoYPeriodo(proyectoId: String, mes: Int, anio: Int): FondoInversion?

    @Query("SELECT * FROM fondos_inversion WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FondoInversion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fondo: FondoInversion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fondos: List<FondoInversion>)

    @Update
    suspend fun update(fondo: FondoInversion)

    @Delete
    suspend fun delete(fondo: FondoInversion)

    @Query("DELETE FROM fondos_inversion WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT SUM(totalInversion) FROM fondos_inversion WHERE proyectoId = :proyectoId")
    suspend fun getTotalInversionByProyecto(proyectoId: String): Double?

    @Query("SELECT SUM(totalInversion) FROM fondos_inversion WHERE mes = :mes AND anio = :anio")
    suspend fun getTotalInversionByPeriodo(mes: Int, anio: Int): Double?

    @Query("SELECT SUM(inversionMateriales) FROM fondos_inversion WHERE proyectoId = :proyectoId")
    suspend fun getTotalMaterialesByProyecto(proyectoId: String): Double?

    @Query("SELECT SUM(inversionManoObra) FROM fondos_inversion WHERE proyectoId = :proyectoId")
    suspend fun getTotalManoObraByProyecto(proyectoId: String): Double?

    @Query("SELECT COUNT(*) FROM fondos_inversion")
    suspend fun countAll(): Int
}
