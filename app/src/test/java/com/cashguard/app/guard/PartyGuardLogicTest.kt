package com.cashguard.app.guard

import com.cashguard.app.guard.PartyGuardManager.Companion.CapAlert
import com.cashguard.app.guard.PartyGuardManager.Companion.evaluateCap
import com.cashguard.app.guard.PartyGuardManager.Companion.evaluateVelocity
import com.cashguard.app.guard.PartyGuardManager.Companion.isNightTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyGuardLogicTest {

    // ---- Cap thresholds ----

    @Test
    fun `below half cap stays silent`() {
        assertEquals(CapAlert.NONE, evaluateCap(sessionSpent = 3000.0, cap = 8000.0, lastThreshold = 0))
    }

    @Test
    fun `crossing 50 percent warns once`() {
        assertEquals(CapAlert.WARN_50, evaluateCap(4100.0, 8000.0, lastThreshold = 0))
        // already warned at 50 — silent until 80
        assertEquals(CapAlert.NONE, evaluateCap(4500.0, 8000.0, lastThreshold = 50))
    }

    @Test
    fun `crossing 80 percent warns once`() {
        assertEquals(CapAlert.WARN_80, evaluateCap(6500.0, 8000.0, lastThreshold = 50))
        assertEquals(CapAlert.NONE, evaluateCap(7000.0, 8000.0, lastThreshold = 80))
    }

    @Test
    fun `jumping straight past 80 skips the 50 warning`() {
        assertEquals(CapAlert.WARN_80, evaluateCap(7000.0, 8000.0, lastThreshold = 0))
    }

    @Test
    fun `every transaction past the cap alarms`() {
        assertEquals(CapAlert.CAP_EXCEEDED, evaluateCap(8200.0, 8000.0, lastThreshold = 80))
        // still alarms even after the 100 threshold was recorded
        assertEquals(CapAlert.CAP_EXCEEDED, evaluateCap(9000.0, 8000.0, lastThreshold = 100))
    }

    @Test
    fun `zero cap never alerts`() {
        assertEquals(CapAlert.NONE, evaluateCap(5000.0, 0.0, lastThreshold = 0))
    }

    // ---- Night-time window ----

    @Test
    fun `night window covers 8pm to 5am`() {
        assertTrue(isNightTime(20))
        assertTrue(isNightTime(23))
        assertTrue(isNightTime(0))
        assertTrue(isNightTime(4))
        assertFalse(isNightTime(5))
        assertFalse(isNightTime(12))
        assertFalse(isNightTime(19))
    }

    // ---- Velocity ----

    @Test
    fun `three fast debits at night trigger`() {
        assertTrue(evaluateVelocity(debitCount = 3, debitSum = 4500.0, hourOfDay = 23, lastAlertAt = 0, now = 10_000_000))
    }

    @Test
    fun `big sum at night triggers even with few transactions`() {
        assertTrue(evaluateVelocity(debitCount = 1, debitSum = 20_000.0, hourOfDay = 22, lastAlertAt = 0, now = 10_000_000))
    }

    @Test
    fun `daytime spending never triggers velocity`() {
        assertFalse(evaluateVelocity(debitCount = 5, debitSum = 50_000.0, hourOfDay = 14, lastAlertAt = 0, now = 10_000_000))
    }

    @Test
    fun `cooldown suppresses repeat alerts`() {
        val now = 10_000_000L
        val recentAlert = now - 10 * 60 * 1000 // 10 min ago < 30 min cooldown
        assertFalse(evaluateVelocity(3, 20_000.0, 23, lastAlertAt = recentAlert, now = now))
        val oldAlert = now - 40 * 60 * 1000
        assertTrue(evaluateVelocity(3, 20_000.0, 23, lastAlertAt = oldAlert, now = now))
    }

    @Test
    fun `slow small spending at night stays silent`() {
        assertFalse(evaluateVelocity(debitCount = 2, debitSum = 3000.0, hourOfDay = 23, lastAlertAt = 0, now = 10_000_000))
    }
}
