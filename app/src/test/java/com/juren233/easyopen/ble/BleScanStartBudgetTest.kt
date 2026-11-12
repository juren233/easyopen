package com.juren233.easyopen.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class BleScanStartBudgetTest {
    private var clock = 100_000L
    private val budget = BleScanStartBudget { clock }

    private fun start(count: Int, gapMs: Long = 0L) {
        repeat(count) {
            budget.recordStart()
            clock += gapMs
        }
    }

    @Test
    fun firstFiveStartsAreAllowedAndSixthWaitsForOldestToExpire() {
        repeat(5) {
            assertEquals(0L, budget.delayUntilStartAllowed(rotation = false))
            budget.recordStart()
            clock += 1_000L
        }
        // Oldest start was 5s ago, so it expires 26s from now (30s window + 1s pad).
        assertEquals(26_000L, budget.delayUntilStartAllowed(rotation = false))
    }

    @Test
    fun startsAreAllowedAgainOnceOldRecordsExpire() {
        start(5)
        val wait = budget.delayUntilStartAllowed(rotation = false)
        assertEquals(31_000L, wait)
        clock += wait
        assertEquals(0L, budget.delayUntilStartAllowed(rotation = false))
    }

    @Test
    fun rotationIsAllowedOnlyWhenFewerThanThreeStartsAreInWindow() {
        start(2)
        assertEquals(0L, budget.delayUntilStartAllowed(rotation = true))
        start(1)
        assertEquals(31_000L, budget.delayUntilStartAllowed(rotation = true))
        assertEquals(0L, budget.delayUntilStartAllowed(rotation = false))
    }

    @Test
    fun rotationWaitsForEnoughOldRecordsNotJustTheOldest() {
        start(1, gapMs = 10_000L)
        start(1, gapMs = 10_000L)
        start(1, gapMs = 1_000L)
        // limit 3: starts[0] is 21s old -> waits 31s - 21s = 10s.
        assertEquals(10_000L, budget.delayUntilStartAllowed(rotation = true))
    }
}
