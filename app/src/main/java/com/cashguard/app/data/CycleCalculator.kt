package com.cashguard.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Computes the "current spending cycle" — the window budget items and
 * monthly stats reset on.
 *
 * Different people get paid differently: once a month, twice a month on
 * fixed dates, weekly, or on no fixed schedule at all. So this is opt-in —
 * with no payday configured it behaves exactly like a plain calendar month.
 * Configuring one or more payday-of-month values switches the cycle to run
 * payday-to-payday instead, so budgets reset when money actually arrives
 * rather than on the 1st.
 */
object CycleCalculator {

    data class Cycle(val startMillis: Long, val endMillis: Long, val key: String)

    fun currentCycle(payDays: List<Int>, nowMillis: Long): Cycle {
        val validDays = payDays.filter { it in 1..31 }.distinct().sorted()
        if (validDays.isEmpty()) return calendarMonthCycle(nowMillis)

        // Candidate payday timestamps spanning last/this/next month so we can
        // find the one immediately before "now" regardless of wraparound.
        val candidates = mutableListOf<Long>()
        for (monthOffset in -1..1) {
            for (day in validDays) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1) // avoid day-31-rollover before shifting months
                    add(Calendar.MONTH, monthOffset)
                    val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                candidates.add(cal.timeInMillis)
            }
        }
        candidates.sort()

        val start = candidates.lastOrNull { it <= nowMillis } ?: candidates.first()
        val end = candidates.firstOrNull { it > start } ?: (start + 30L * 24 * 60 * 60 * 1000)

        val key = "pd-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(start))
        return Cycle(start, end, key)
    }

    private fun calendarMonthCycle(nowMillis: Long): Cycle {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        val key = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(start))
        return Cycle(start, end, key)
    }
}
