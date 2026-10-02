package com.drywall.calculator.presentation.ui.incomestatement

import com.drywall.calculator.data.local.entity.IncomeStatement
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object IncomeStatementUtils {
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val weekFormat = SimpleDateFormat("'Sem' w 'de' yyyy", Locale.getDefault())
    
    fun getMonthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    fun getMonthEnd(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
    
    fun getWeekStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    fun getWeekEnd(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek() + 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
    
    fun getQuarterStart(): Long {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val quarterStartMonth = (month / 3) * 3
        cal.set(Calendar.MONTH, quarterStartMonth)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    fun getQuarterEnd(): Long {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val quarterEndMonth = ((month / 3) * 3) + 2
        cal.set(Calendar.MONTH, quarterEndMonth)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
    
    fun getSemesterStart(): Long {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val semesterStartMonth = if (month < 6) 0 else 6
        cal.set(Calendar.MONTH, semesterStartMonth)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    fun getSemesterEnd(): Long {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val semesterEndMonth = if (month < 6) 5 else 11
        cal.set(Calendar.MONTH, semesterEndMonth)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
    
    fun getYearStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    fun getYearEnd(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
    
    fun generatePeriodLabel(periodType: String, startDate: Long, endDate: Long): String {
        return when (periodType) {
            IncomeStatement.PERIOD_DIARIO -> dateFormat.format(Date(startDate))
            IncomeStatement.PERIOD_SEMANAL -> weekFormat.format(Date(startDate))
            IncomeStatement.PERIOD_MENSUAL -> monthFormat.format(Date(startDate))
            IncomeStatement.PERIOD_TRIMESTRAL -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = startDate
                val quarter = (cal.get(Calendar.MONTH) / 3) + 1
                "Q$quarter ${yearFormat.format(Date(startDate))}"
            }
            IncomeStatement.PERIOD_SEMESTRAL -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = startDate
                val semester = if (cal.get(Calendar.MONTH) < 6) "1er Semestre" else "2do Semestre"
                "$semester ${yearFormat.format(Date(startDate))}"
            }
            IncomeStatement.PERIOD_ANUAL -> yearFormat.format(Date(startDate))
            IncomeStatement.PERIOD_PERSONALIZADO -> "${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}"
            else -> "${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}"
        }
    }
    
    fun getDefaultDateRange(periodType: String): Pair<Long, Long> {
        return when (periodType) {
            IncomeStatement.PERIOD_DIARIO -> {
                val now = System.currentTimeMillis()
                Pair(now, now)
            }
            IncomeStatement.PERIOD_SEMANAL -> Pair(getWeekStart(), getWeekEnd())
            IncomeStatement.PERIOD_MENSUAL -> Pair(getMonthStart(), getMonthEnd())
            IncomeStatement.PERIOD_TRIMESTRAL -> Pair(getQuarterStart(), getQuarterEnd())
            IncomeStatement.PERIOD_SEMESTRAL -> Pair(getSemesterStart(), getSemesterEnd())
            IncomeStatement.PERIOD_ANUAL -> Pair(getYearStart(), getYearEnd())
            IncomeStatement.PERIOD_PERSONALIZADO -> Pair(getMonthStart(), getMonthEnd())
            else -> Pair(getMonthStart(), getMonthEnd())
        }
    }
    
    fun formatCurrency(amount: Double, currency: String = "USD"): String {
        val formatter = java.text.NumberFormat.getCurrencyInstance(Locale.getDefault())
        // Configurar símbolo según moneda
        when (currency) {
            "USD" -> formatter.currency = java.util.Currency.getInstance("USD")
            "EUR" -> formatter.currency = java.util.Currency.getInstance("EUR")
            "CUP" -> formatter.currency = java.util.Currency.getInstance("CUP")
            "MLC" -> {
                formatter.currency = java.util.Currency.getInstance("USD")
                return "${formatter.format(amount).replace("\$", "")} MLC"
            }
            else -> formatter.currency = java.util.Currency.getInstance("USD")
        }
        return formatter.format(amount)
    }
    
    fun formatPercentage(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f%%", value)
    }
    
    fun getPeriodDisplayName(periodType: String): String {
        return IncomeStatement.PERIOD_LABELS[periodType] ?: periodType
    }
    
    fun getAllPeriodTypes(): List<String> = IncomeStatement.PERIOD_TYPES
    
    // Cálculos financieros adicionales
    fun calculateGrossMargin(ventas: Double, utilidadBruta: Double): Double {
        return if (ventas > 0) (utilidadBruta / ventas) * 100 else 0.0
    }
    
    fun calculateOperatingMargin(ventas: Double, utilidadOperativa: Double): Double {
        return if (ventas > 0) (utilidadOperativa / ventas) * 100 else 0.0
    }
    
    fun calculateNetMargin(ventas: Double, utilidadNeta: Double): Double {
        return if (ventas > 0) (utilidadNeta / ventas) * 100 else 0.0
    }
}