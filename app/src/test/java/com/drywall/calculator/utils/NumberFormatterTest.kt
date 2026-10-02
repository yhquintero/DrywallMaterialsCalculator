package com.drywall.calculator.utils

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class NumberFormatterTest {
    @Test fun `format with precision 2`() {
        Locale.setDefault(Locale.US)
        val result = NumberFormatter.format(1234.5678, 2)
        val decimalPart = result.substringAfter(".")
        assertEquals(2, decimalPart.length)
        assertTrue(result.contains("1,234"))
    }
    @Test fun `format zero`() {
        val result = NumberFormatter.format(0.0, 0)
        assertEquals("0", result)
    }
    @Test fun `format negative number`() {
        val result = NumberFormatter.format(-50.5, 1)
        assertTrue(result.startsWith("-"))
    }
    @Test fun `format large number`() {
        val result = NumberFormatter.format(1000000.0, 0)
        assertTrue(result.contains("1,000,000") || result.contains("1000000"))
    }
    @Test fun `format with precision 0 rounds to integer`() {
        val result = NumberFormatter.format(99.9, 0)
        assertFalse(result.contains("."))
    }
    @Test fun `format small decimal`() {
        Locale.setDefault(Locale.US)
        val result = NumberFormatter.format(0.001, 3)
        assertEquals("0.001", result)
    }
    @Test fun `format handles null safety`() {
        val result = NumberFormatter.format(0.0, 2)
        assertNotNull(result)
    }
}