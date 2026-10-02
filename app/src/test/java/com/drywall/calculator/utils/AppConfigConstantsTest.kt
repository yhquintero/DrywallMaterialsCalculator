package com.drywall.calculator.utils

import org.junit.Assert.*
import org.junit.Test

class AppConfigConstantsTest {
    @Test fun `default min profit is 25`() {
        assertEquals(25.0, AppConfigConstants.DEFAULT_MIN_PROFIT, 0.001)
    }
    @Test fun `default max profit is 30`() {
        assertEquals(30.0, AppConfigConstants.DEFAULT_MAX_PROFIT, 0.001)
    }
    @Test fun `default profit is midpoint`() {
        assertEquals(27.5, AppConfigConstants.DEFAULT_PROFIT, 0.001)
    }
    @Test fun `min is less than max`() {
        assertTrue(AppConfigConstants.DEFAULT_MIN_PROFIT < AppConfigConstants.DEFAULT_MAX_PROFIT)
    }
    @Test fun `default is within valid range`() {
        assertTrue(AppConfigConstants.DEFAULT_PROFIT in AppConfigConstants.DEFAULT_MIN_PROFIT..AppConfigConstants.DEFAULT_MAX_PROFIT)
    }
}