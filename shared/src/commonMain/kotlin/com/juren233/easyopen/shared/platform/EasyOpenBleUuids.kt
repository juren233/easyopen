package com.juren233.easyopen.shared.platform

/**
 * Nordic UART-style GATT UUIDs used by the current opener protocol.
 *
 * Keeping them in shared code prevents the Android BluetoothGatt and iOS
 * CoreBluetooth implementations from silently drifting apart.
 */
object EasyOpenBleUuids {
    const val SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val NOTIFY = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
}
