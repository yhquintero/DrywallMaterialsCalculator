package com.drywall.calculator.domain.calculator

import com.drywall.calculator.utils.AppConfigConstants

object ProfitValidator {
    const val MIN_PROFIT_PERCENT = AppConfigConstants.DEFAULT_MIN_PROFIT
    const val MAX_PROFIT_PERCENT = AppConfigConstants.DEFAULT_MAX_PROFIT

    fun isValidProfit(purchasePrice: Double, salePrice: Double): Boolean {
        if (purchasePrice <= 0) return false
        val profitPercent = (salePrice - purchasePrice) / purchasePrice * 100
        return profitPercent in MIN_PROFIT_PERCENT..MAX_PROFIT_PERCENT
    }

    fun enforceProfit(purchasePrice: Double, desiredProfitPercent: Double): Double {
        val validProfit = desiredProfitPercent.coerceIn(MIN_PROFIT_PERCENT, MAX_PROFIT_PERCENT)
        return purchasePrice * (1 + validProfit / 100)
    }
}
