package com.drywall.calculator.data.local.dao

import androidx.room.*
import com.drywall.calculator.data.local.entity.IncomeStatement
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeStatementDao {
    @Query("SELECT * FROM income_statements ORDER BY startDate DESC")
    fun getAll(): Flow<List<IncomeStatement>>

    @Query("SELECT * FROM income_statements WHERE periodType = :periodType ORDER BY startDate DESC")
    fun getByPeriodType(periodType: String): Flow<List<IncomeStatement>>

    @Query("SELECT * FROM income_statements WHERE startDate >= :startDate AND endDate <= :endDate ORDER BY startDate DESC")
    fun getByDateRange(startDate: Long, endDate: Long): Flow<List<IncomeStatement>>

    @Query("SELECT * FROM income_statements WHERE proyectoId = :proyectoId ORDER BY startDate DESC")
    fun getByProjectId(proyectoId: String): Flow<List<IncomeStatement>>

    @Query("SELECT * FROM income_statements WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): IncomeStatement?

    @Query("SELECT * FROM income_statements WHERE periodLabel = :label AND periodType = :type LIMIT 1")
    suspend fun getByPeriodLabel(label: String, type: String): IncomeStatement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(statement: IncomeStatement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statements: List<IncomeStatement>)

    @Update
    suspend fun update(statement: IncomeStatement)

    @Delete
    suspend fun delete(statement: IncomeStatement)

    @Query("DELETE FROM income_statements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM income_statements")
    suspend fun countAll(): Int

    @Query("SELECT SUM(utilidadNeta) FROM income_statements WHERE startDate >= :startDate AND endDate <= :endDate")
    suspend fun getTotalNetProfitInRange(startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(ventas) FROM income_statements WHERE startDate >= :startDate AND endDate <= :endDate")
    suspend fun getTotalSalesInRange(startDate: Long, endDate: Long): Double?

    @Query("SELECT * FROM income_statements WHERE periodType = :periodType ORDER BY startDate DESC LIMIT :limit")
    suspend fun getLatestByPeriodType(periodType: String, limit: Int): List<IncomeStatement>

    // Agregación para Balance General - obtiene totales del período
    @Query("""
        SELECT 
            SUM(ventas) as totalVentas,
            SUM(costosVentas) as totalCostos,
            SUM(gastosOperativos) as totalGastosOperativos,
            SUM(gastosFinancieros) as totalGastosFinancieros,
            SUM(impuestos) as totalImpuestos,
            SUM(utilidadNeta) as totalUtilidadNeta
        FROM income_statements 
        WHERE startDate >= :startDate AND endDate <= :endDate
    """)
    suspend fun getAggregatedForBalanceSheet(startDate: Long, endDate: Long): AggregatedIncomeStatement?

    @Query("SELECT * FROM income_statements ORDER BY fechaCreacion DESC LIMIT 1")
    suspend fun getLastSaved(): IncomeStatement?
}

data class AggregatedIncomeStatement(
    val totalVentas: Double?,
    val totalCostos: Double?,
    val totalGastosOperativos: Double?,
    val totalGastosFinancieros: Double?,
    val totalImpuestos: Double?,
    val totalUtilidadNeta: Double?
)