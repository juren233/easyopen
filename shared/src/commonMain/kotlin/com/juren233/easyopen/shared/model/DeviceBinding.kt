package com.juren233.easyopen.shared.model

/**
 * Local platform binding for a logical opener profile.
 *
 * These values are local discovery handles. They must not be used as the
 * cross-platform identity in QR sharing or backup payloads.
 */
sealed interface DeviceBinding {
    data class AndroidMac(val address: String) : DeviceBinding
    data class IosPeripheral(val identifier: String) : DeviceBinding
}
