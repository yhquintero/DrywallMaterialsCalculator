package com.drywall.calculator.utils

import java.util.Calendar

/**
 * Utility class for data validation across the application.
 */
object ValidationUtils {

    /**
     * Validates an 11-digit identification number.
     * Format: YYMMDDXXXXX
     * - YY: Year (00-99)
     * - MM: Month (01-12)
     * - DD: Day (Valid for the month/year)
     * - 10th digit: Sex (Even = Male, Odd = Female)
     */
    fun validateIdNumber(ni: String): ValidationResult {
        if (ni.length != 11) {
            return ValidationResult(false, "El número de identidad debe tener exactamente 11 dígitos.")
        }

        if (!ni.all { it.isDigit() }) {
            return ValidationResult(false, "El número de identidad solo debe contener números.")
        }

        val yearStr = ni.substring(0, 2)
        val monthStr = ni.substring(2, 4)
        val dayStr = ni.substring(4, 6)

        val year = yearStr.toInt()
        val month = monthStr.toInt()
        val day = dayStr.toInt()

        if (month < 1 || month > 12) {
            return ValidationResult(false, "Mes inválido ($monthStr).")
        }

        val maxDays = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> {
                // For simplicity, we assume year is in 1900s or 2000s
                // Leap year check: divisible by 4
                val fullYear = if (year > 24) 1900 + year else 2000 + year
                if ((fullYear % 4 == 0 && fullYear % 100 != 0) || (fullYear % 400 == 0)) 29 else 28
            }
            else -> 0
        }

        if (day < 1 || day > maxDays) {
            return ValidationResult(false, "Día inválido ($dayStr) para el mes $monthStr.")
        }

        // El 10mo dígito indica el sexo:
        // Par = Masculino (M), Impar = Femenino (F)
        val sexDigit = ni[9].toString().toInt()
        val sex = if (sexDigit % 2 == 0) "M" else "F"

        return ValidationResult(true, sex = sex)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String? = null,
        val sex: String? = null
    )
}
