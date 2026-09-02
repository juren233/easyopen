package com.juren233.easyopen.shared.state

import com.juren233.easyopen.shared.model.CoreDeviceProfile

/** Device data required by the shared home surface. */
data class HomeDeviceSnapshot(
    val id: String,
    val identifierLabel: String,
    val profile: CoreDeviceProfile,
)

data class HomeUpdateNotice(
    val displayVersion: String,
)

/**
 * Complete platform-neutral input for the home page.
 *
 * The host maps its native controller/store into this snapshot. The common UI
 * does not know whether the identifier is an Android MAC or an iOS UUID.
 */
data class HomePageSnapshot(
    val activeDevice: HomeDeviceSnapshot,
    val connectionStatus: EasyOpenConnectionStatus = EasyOpenConnectionStatus.NOT_FOUND,
    val batteryLevel: Int? = null,
    val busy: Boolean = false,
    val canUnlock: Boolean = false,
    val availableUpdate: HomeUpdateNotice? = null,
)
