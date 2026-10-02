package com.drywall.calculator.data.repository

import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.IncomeStatement
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomeStatementRepository @Inject constructor(private val db: AppDatabase) {
    fun getAllStatements(): Flow<List<IncomeStatement>> = db.incomeStatementDao().getAll()
    
    fun getStatementsByPeriodType(periodType: String): Flow<List<IncomeStatement>> = 
        db.incomeStatementDao().getByPeriodType(periodType)
    
    fun getStatementsByDateRange(startDate: Long, endDate: Long): Flow<List<IncomeStatement>> = 
        db.incomeStatementDao().getByDateRange(startDate, endDate)
    
    fun getStatementsByProject(proyectoId: String): Flow<List<IncomeStatement>> = 
        db.incomeStatementDao().getByProjectId(proyectoId)
    
    suspend fun getStatementById(id: String): IncomeStatement? = 
        db.incomeStatementDao().getById(id)
    
    suspend fun getStatementByPeriodLabel(label: String, type: String): IncomeStatement? = 
        db.incomeStatementDao().getByPeriodLabel(label, type)
    
    suspend fun saveStatement(statement: IncomeStatement) = 
        db.incomeStatementDao().insert(statement.recalcular())
    
    suspend fun saveAllStatements(statements: List<IncomeStatement>) = 
        db.incomeStatementDao().insertAll(statements.map { it.recalcular() })
    
    suspend fun updateStatement(statement: IncomeStatement) = 
        db.incomeStatementDao().update(statement.recalcular())
    
    suspend fun deleteStatement(statement: IncomeStatement) = 
        db.incomeStatementDao().delete(statement)
    
    suspend fun deleteStatementById(id: String) = 
        db.incomeStatementDao().deleteById(id)
    
    suspend fun getTotalCount(): Int = 
        db.incomeStatementDao().countAll()
    
    suspend fun getTotalNetProfit(startDate: Long, endDate: Long): Double = 
        db.incomeStatementDao().getTotalNetProfitInRange(startDate, endDate) ?: 0.0
    
    suspend fun getTotalSales(startDate: Long, endDate: Long): Double = 
        db.incomeStatementDao().getTotalSalesInRange(startDate, endDate) ?: 0.0
    
    suspend fun getLatestStatements(periodType: String, limit: Int): List<IncomeStatement> = 
        db.incomeStatementDao().getLatestByPeriodType(periodType, limit)
    
    suspend fun getAggregatedForBalanceSheet(startDate: Long, endDate: Long) = 
        db.incomeStatementDao().getAggregatedForBalanceSheet(startDate, endDate)
    
    suspend fun getLastSavedStatement(): IncomeStatement? = 
        db.incomeStatementDao().getLastSaved()
}