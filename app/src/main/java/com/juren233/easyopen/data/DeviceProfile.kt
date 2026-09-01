package com.juren233.easyopen.data

/**
 * Parameters used by the original Macronum/YiLa BLE protocol.
 *
 * The original app's model calls the lock password a password, but it is
 * actually a local secret used to derive the per-command authentication token.
 */
data class DeviceProfile(
    val name: String = "我的开门器",
    val address: String = "",
    val password: String = "",
    val attribute: Int = 0,
    val openTimeMs: Int = 650,
    val waitTimeMs: Int = 2000,
    val closeTimeMs: Int = 600,
    val batteryLevel: Int? = null,
    /** MAC mirrored in Manufacturer Data, used for cross-platform matching. */
    val hardwareMac: String? = null,
)
