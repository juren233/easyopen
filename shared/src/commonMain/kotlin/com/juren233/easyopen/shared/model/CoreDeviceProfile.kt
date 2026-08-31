package com.juren233.easyopen.shared.model

/**
 * Cross-platform opener configuration.
 *
 * Platform identifiers are intentionally not part of this model. Android MAC
 * addresses and iOS CBPeripheral identifiers belong to DeviceBinding instead.
 */
data class CoreDeviceProfile(
    val name: String = "我的开门器",
    val password: String = "",
    val attribute: Int = 0,
    val openTimeMs: Int = 650,
    val waitTimeMs: Int = 2_000,
    val closeTimeMs: Int = 600,
    val batteryLevel: Int? = null,
) {
    fun normalized(): CoreDeviceProfile = copy(
        name = name.trim().ifBlank { "我的开门器" },
        attribute = attribute.coerceIn(0, 1),
        openTimeMs = openTimeMs.coerceIn(0, 60_000),
        waitTimeMs = waitTimeMs.coerceIn(0, 60_000),
        closeTimeMs = closeTimeMs.coerceIn(0, 60_000),
        batteryLevel = batteryLevel?.takeIf { it in 1..5 },
    )
}
