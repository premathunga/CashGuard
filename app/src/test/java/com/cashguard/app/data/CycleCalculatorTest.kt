package com.cashguard.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CycleCalculatorTest {

    private fun millisFor(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ---- No paydays configured: behaves exactly like a calendar month ----

    @Test
    fun `no paydays falls back to calendar month`() {
        val now = millisFor(2026, 7, 15)
        val cycle = CycleCalculator.currentCycle(emptyList(), now)
        assertEquals(millisFor(2026, 7, 1, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 8, 1, 0), cycle.endMillis)
        assertEquals("2026-07", cycle.key)
    }

    // ---- Payday-to-payday cycles ----

    @Test
    fun `mid-cycle date falls between the two paydays`() {
        val now = millisFor(2026, 7, 15)
        val cycle = CycleCalculator.currentCycle(listOf(10, 25), now)
        assertEquals(millisFor(2026, 7, 10, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 7, 25, 0), cycle.endMillis)
        assertEquals("pd-2026-07-10", cycle.key)
    }

    @Test
    fun `date after the second payday rolls into next month`() {
        val now = millisFor(2026, 7, 27)
        val cycle = CycleCalculator.currentCycle(listOf(10, 25), now)
        assertEquals(millisFor(2026, 7, 25, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 8, 10, 0), cycle.endMillis)
    }

    @Test
    fun `date before the first payday rolls back into previous month`() {
        val now = millisFor(2026, 7, 5)
        val cycle = CycleCalculator.currentCycle(listOf(10, 25), now)
        assertEquals(millisFor(2026, 6, 25, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 7, 10, 0), cycle.endMillis)
    }

    @Test
    fun `exactly on payday starts the new cycle`() {
        val now = millisFor(2026, 7, 10, hour = 0)
        val cycle = CycleCalculator.currentCycle(listOf(10, 25), now)
        assertEquals(millisFor(2026, 7, 10, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 7, 25, 0), cycle.endMillis)
    }

    @Test
    fun `single payday produces month-long cycle`() {
        val now = millisFor(2026, 7, 20)
        val cycle = CycleCalculator.currentCycle(listOf(1), now)
        assertEquals(millisFor(2026, 7, 1, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 8, 1, 0), cycle.endMillis)
    }

    @Test
    fun `day 31 coerces safely in short months`() {
        // Payday "31" in February should coerce to the 28th (2026 not a leap year)
        val now = millisFor(2026, 2, 15)
        val cycle = CycleCalculator.currentCycle(listOf(31), now)
        assertEquals(millisFor(2026, 1, 31, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 2, 28, 0), cycle.endMillis)
    }

    @Test
    fun `unsorted duplicate paydays are normalised`() {
        val now = millisFor(2026, 7, 15)
        val cycle = CycleCalculator.currentCycle(listOf(25, 10, 10, 25), now)
        assertEquals(millisFor(2026, 7, 10, 0), cycle.startMillis)
        assertEquals(millisFor(2026, 7, 25, 0), cycle.endMillis)
    }

    @Test
    fun `out-of-range paydays are ignored`() {
        val now = millisFor(2026, 7, 15)
        val cycle = CycleCalculator.currentCycle(listOf(0, 32, 10, 25), now)
        assertEquals(millisFor(2026, 7, 10, 0), cycle.startMillis)
    }
}
