package com.estateflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyUtilTest {

    @Test
    @DisplayName("round sets scale to 2 with HALF_UP")
    void testRound() {
        assertEquals(new BigDecimal("123.46"), MoneyUtil.round(new BigDecimal("123.456")));
        assertEquals(new BigDecimal("123.45"), MoneyUtil.round(new BigDecimal("123.454")));
        assertEquals(new BigDecimal("123.45"), MoneyUtil.round(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("0.00"), MoneyUtil.round(null));
    }

    @Test
    @DisplayName("format produces comma separated 2-decimal strings")
    void testFormat() {
        assertEquals("1,234.50", MoneyUtil.format(new BigDecimal("1234.5")));
        assertEquals("0.00", MoneyUtil.format(BigDecimal.ZERO));
        assertEquals("0.00", MoneyUtil.format(null));
    }

    @Test
    @DisplayName("hasValidScale checks maximum 2 decimal places")
    void testHasValidScale() {
        assertTrue(MoneyUtil.hasValidScale(new BigDecimal("100")));
        assertTrue(MoneyUtil.hasValidScale(new BigDecimal("100.5")));
        assertTrue(MoneyUtil.hasValidScale(new BigDecimal("100.50")));
        assertFalse(MoneyUtil.hasValidScale(new BigDecimal("100.555")));
        assertFalse(MoneyUtil.hasValidScale(null));
    }

    @Test
    @DisplayName("parse correctly handles strings with commas and symbols")
    void testParse() {
        assertEquals(new BigDecimal("1234.50"), MoneyUtil.parse("1,234.50"));
        assertEquals(new BigDecimal("500.00"), MoneyUtil.parse(" 500 "));
        assertNull(MoneyUtil.parse(""));
        assertNull(MoneyUtil.parse(null));
    }
}
