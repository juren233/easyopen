package com.juren233.easyopen.ble

/**
 * Tracks this app's startScan calls against Android's per-app scan quota so the
 * scanners can wait instead of issuing a start the system would silently drop.
 */
internal class BleScanStartBudget(private val now: () -> Long) {
    private val starts = ArrayDeque<Long>()

    /** Returns 0 when a scan may start now, otherwise how long to wait. */
    fun delayUntilStartAllowed(rotation: Boolean): Long {
        val current = now()
        val horizon = current - (QUOTA_WINDOW_MS + CLOCK_PAD_MS)
        while (starts.isNotEmpty() && starts.first() < horizon) starts.removeFirst()
        val limit = if (rotation) QUOTA_COUNT - ROTATION_RESERVE else QUOTA_COUNT
        val count = starts.size
        if (count < limit) return 0L
        return starts[count - limit] + QUOTA_WINDOW_MS + CLOCK_PAD_MS - current
    }

    fun recordStart() {
        starts.addLast(now())
    }

    companion object {
        /** Mirrors AOSP AdapterService.DEFAULT_SCAN_QUOTA_COUNT. */
        const val QUOTA_COUNT = 5

        /** Mirrors AOSP AdapterService.DEFAULT_SCAN_QUOTA_WINDOW_MILLIS. */
        const val QUOTA_WINDOW_MS = 30_000L

        /** The system records the start later than our call, so keep a margin. */
        const val CLOCK_PAD_MS = 1_000L

        /** Periodic window rotation must leave this many starts for event-driven ones. */
        const val ROTATION_RESERVE = 2
    }
}
