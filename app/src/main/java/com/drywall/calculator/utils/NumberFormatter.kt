package com.drywall.calculator.utils

import java.util.Locale

object NumberFormatter {
    fun format(value: Double, precision: Int = 4): String {
        return try {
            val format = "%,.${precision}f"
            String.format(Locale.getDefault(), format, value)
        } catch (e: Exception) {
            value.toString()
        }
    }

    /**
     * Money format for PDF/reports: 2 decimals, "$" prefix, thousands separated with comma,
     * decimals with dot. Example: $1,234.56
     */
    fun money(value: Double): String {
        return try {
            "$" + String.format(Locale.US, "%,.2f", value)
        } catch (e: Exception) {
            "$" + value.toString()
        }
    }
}
