package com.juren233.easyopen.shared.state

import com.juren233.easyopen.shared.model.DeviceBinding

/**
 * Pure collection helpers for platform hosts that persist saved openers.
 *
 * The host owns persistence and the native binding type; these helpers only
 * define deterministic replacement and active-device fallback semantics.
 */
fun upsertSavedDevice(
    devices: List<EasyOpenSavedDevice>,
    next: EasyOpenSavedDevice,
): List<EasyOpenSavedDevice> = buildList {
    var replaced = false
    devices.forEach { existing ->
        if (existing.binding.sameLocalIdentityAs(next.binding)) {
            add(next)
            replaced = true
        } else {
            add(existing)
        }
    }
    if (!replaced) add(next)
}

fun activeSavedDevice(
    devices: List<EasyOpenSavedDevice>,
    activeIdentifier: String,
): EasyOpenSavedDevice? = devices.firstOrNull { device ->
    device.binding.displayIdentifier().equals(activeIdentifier.trim(), ignoreCase = true)
} ?: devices.firstOrNull()

private fun DeviceBinding.sameLocalIdentityAs(other: DeviceBinding): Boolean = when {
    this is DeviceBinding.AndroidMac && other is DeviceBinding.AndroidMac ->
        address.equals(other.address, ignoreCase = true)
    this is DeviceBinding.IosPeripheral && other is DeviceBinding.IosPeripheral ->
        identifier.equals(other.identifier, ignoreCase = true)
    else -> false
}
/** Stable, case-insensitive local identities used by common selection UI. */
fun savedDeviceIdentityKeys(devices: List<EasyOpenSavedDevice>): Set<String> =
    devices.mapTo(linkedSetOf()) { it.binding.displayIdentifier().trim().uppercase() }

/** Preserve storage order while applying a platform-neutral saved-device selection. */
fun selectedSavedDevices(
    devices: List<EasyOpenSavedDevice>,
    selectedIdentifiers: Set<String>,
): List<EasyOpenSavedDevice> {
    val normalizedSelection = selectedIdentifiers.mapTo(hashSetOf()) { it.trim().uppercase() }
    return devices.filter { device ->
        device.binding.displayIdentifier().trim().uppercase() in normalizedSelection
    }
}
