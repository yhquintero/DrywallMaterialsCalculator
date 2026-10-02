package com.drywall.calculator.domain.calculator

import org.junit.Assert.*
import org.junit.Test

class ProfitValidatorTest {
    @Test fun `profit below minimum returns false`() {
        assertFalse(ProfitValidator.isValidProfit(100.0, 105.0))
    }
    @Test fun `profit at minimum returns true`() {
        assertTrue(ProfitValidator.isValidProfit(100.0, 125.0))
    }
    @Test fun `profit at maximum returns true`() {
        assertTrue(ProfitValidator.isValidProfit(100.0, 130.0))
    }
    @Test fun `zero cost returns false`() {
        assertFalse(ProfitValidator.isValidProfit(0.0, 10.0))
    }
    @Test fun `negative values return false`() {
        assertFalse(ProfitValidator.isValidProfit(100.0, 95.0))
    }
    @Test fun `enforceProfit clamps below minimum`() {
        val salePrice = ProfitValidator.enforceProfit(100.0, 10.0)
        assertEquals(125.0, salePrice, 0.001)
    }
    @Test fun `enforceProfit clamps above maximum`() {
        val salePrice = ProfitValidator.enforceProfit(100.0, 50.0)
        assertEquals(130.0, salePrice, 0.001)
    }
    @Test fun `enforceProfit uses desired within range`() {
        val salePrice = ProfitValidator.enforceProfit(100.0, 27.5)
        assertEquals(127.5, salePrice, 0.001)
    }
    @Test fun `enforceProfit handles zero cost`() {
        val salePrice = ProfitValidator.enforceProfit(0.0, 25.0)
        assertEquals(0.0, salePrice, 0.001)
    }
    @Test fun `enforceProfit at exact minimum`() {
        val salePrice = ProfitValidator.enforceProfit(200.0, 25.0)
        assertEquals(250.0, salePrice, 0.001)
    }
}