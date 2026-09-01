package com.juren233.easyopen.data

import kotlin.math.abs

/** Cross-platform automatic connection preferences and RSSI normalization. */
object AutoConnectSettings {
    const val RANGE_NEAR = 0
    const val RANGE_MODERATE = 1
    const val RANGE_FAR = 2
    const val RANGE_CUSTOM = 3

    const val DEFAULT_RANGE = RANGE_MODERATE
    const val DEFAULT_RSSI_THRESHOLD = -90
    const val MIN_RSSI_MAGNITUDE = 1
    const val MAX_RSSI_MAGNITUDE = 127

    fun normalizeRange(range: Int): Int = range.coerceIn(RANGE_NEAR, RANGE_CUSTOM)

    fun normalizeRssiThreshold(rssi: Int): Int =
        -abs(rssi).coerceIn(MIN_RSSI_MAGNITUDE, MAX_RSSI_MAGNITUDE)

    fun thresholdFor(range: Int, customRssiThreshold: Int): Int = when (normalizeRange(range)) {
        RANGE_NEAR -> -80
        RANGE_MODERATE -> DEFAULT_RSSI_THRESHOLD
        RANGE_FAR -> -100
        RANGE_CUSTOM -> normalizeRssiThreshold(customRssiThreshold)
        else -> DEFAULT_RSSI_THRESHOLD
    }

    fun inputMagnitudeFor(rssiThreshold: Int): Int =
        abs(normalizeRssiThreshold(rssiThreshold))
}
