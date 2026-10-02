package com.drywall.calculator.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "currency_history")
data class CurrencyHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val day: Int,
    val month: Int,
    val year: Int,
    val timestamp: Long,
    val usdRate: Double,
    val eurRate: Double,
    val mlcRate: Double = 0.0,
    val cupRate: Double = 1.0,
    // Nuevas divisas solicitadas
    val cadRate: Double = 0.0,
    val mexRate: Double = 0.0,
    val zelleRate: Double = 0.0,
    val claRate: Double = 0.0
) {
    companion object {
        fun fromNow(
            usd: Double, 
            eur: Double, 
            mlc: Double, 
            cup: Double,
            cad: Double = 0.0,
            mex: Double = 0.0,
            zelle: Double = 0.0,
            cla: Double = 0.0
        ): CurrencyHistory {
            val cal = Calendar.getInstance()
            return CurrencyHistory(
                day = cal.get(Calendar.DAY_OF_MONTH),
                month = cal.get(Calendar.MONTH) + 1,
                year = cal.get(Calendar.YEAR),
                timestamp = System.currentTimeMillis(),
                usdRate = usd,
                eurRate = eur,
                mlcRate = mlc,
                cupRate = cup,
                cadRate = cad,
                mexRate = mex,
                zelleRate = zelle,
                claRate = cla
            )
        }
    }
}
