package com.juren233.easyopen.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.util.Log
import com.juren233.easyopen.BuildConfig
import java.security.MessageDigest
import java.util.UUID

/**
 * Debug-only BLE identity inspection.
 *
 * This deliberately does not participate in release behavior. It records the
 * advertising payload and the complete GATT shape, then lets BleGattSession
 * read readable characteristics one at a time so we can determine whether the
 * opener exposes a hardware serial/identity that can be shared across Android
 * and iOS. Generic characteristic values are logged as hashes, not plaintext,
 * to avoid leaking an unknown configuration/password field into logcat.
 */
internal object BleIdentityDiagnostics {
    private const val TAG = "EasyOpenBleIdentity"
    private const val MAX_SCAN_SIGNATURES = 128

    private val loggedScanSignatures = LinkedHashSet<String>()
    private val standardIdentityCharacteristicUuids = setOf(
        "00002a23-0000-1000-8000-00805f9b34fb", // System ID
        "00002a24-0000-1000-8000-00805f9b34fb", // Model Number String
        "00002a25-0000-1000-8000-00805f9b34fb", // Serial Number String
        "00002a26-0000-1000-8000-00805f9b34fb", // Firmware Revision String
        "00002a27-0000-1000-8000-00805f9b34fb", // Hardware Revision String
        "00002a28-0000-1000-8000-00805f9b34fb", // Software Revision String
        "00002a29-0000-1000-8000-00805f9b34fb", // Manufacturer Name String
    )
    private const val DEVICE_INFORMATION_SERVICE_UUID =
        "0000180a-0000-1000-8000-00805f9b34fb"

    @SuppressLint("MissingPermission")
    fun logScanResult(result: ScanResult, resolvedName: String) {
        if (!BuildConfig.DEBUG) return
        val record = result.scanRecord ?: return
        val address = runCatching { result.device.address }.getOrDefault("<unknown>")
        val serviceUuids = record.serviceUuids.orEmpty()
            .joinToString(",") { it.uuid.toString() }
        val name = resolvedName.trim().ifBlank { record.deviceName.orEmpty() }
        val isTarget = name.contains("YILA", ignoreCase = true) ||
            serviceUuids.contains(BleGattSession.SERVICE_UUID.toString(), ignoreCase = true)
        if (!isTarget) return

        val manufacturerData = buildString {
            for (index in 0 until record.manufacturerSpecificData.size()) {
                if (index > 0) append(';')
                append(record.manufacturerSpecificData.keyAt(index))
                append('=')
                append(record.manufacturerSpecificData.valueAt(index).toHex())
            }
        }.ifBlank { "<none>" }
        val serviceData = record.serviceData.orEmpty()
            .entries
            .joinToString(";") { (uuid, bytes) -> "${uuid.uuid}=${bytes.toHex()}" }
            .ifBlank { "<none>" }
        val raw = record.bytes.toHex().ifBlank { "<none>" }
        val signature = "$address|$raw|$serviceUuids|$manufacturerData|$serviceData"
        synchronized(loggedScanSignatures) {
            if (!loggedScanSignatures.add(signature)) return
            if (loggedScanSignatures.size > MAX_SCAN_SIGNATURES) {
                loggedScanSignatures.iterator().apply { next(); remove() }
            }
        }

        Log.d(
            TAG,
            "BLE_ID_SCAN address=$address name=${name.ifBlank { "<none>" }} " +
                "recordName=${record.deviceName.orEmpty().ifBlank { "<none>" }} " +
                "rssi=${result.rssi} txPower=${record.txPowerLevel} flags=${record.advertiseFlags} " +
                "services=${serviceUuids.ifBlank { "<none>" }} " +
                "manufacturerData=$manufacturerData serviceData=$serviceData raw=$raw",
        )
    }

    /** Logs all services/characteristics and returns readable characteristics. */
    @SuppressLint("MissingPermission")
    fun logGattTable(address: String, gatt: BluetoothGatt): List<BluetoothGattCharacteristic> {
        if (!BuildConfig.DEBUG) return emptyList()
        val readable = mutableListOf<BluetoothGattCharacteristic>()
        val services = gatt.services.orEmpty()
        Log.d(TAG, "BLE_ID_GATT address=$address serviceCount=${services.size}")
        services.forEach { service ->
            val serviceUuid = service.uuid.toString().lowercase()
            val characteristics = service.characteristics.orEmpty()
            Log.d(
                TAG,
                "BLE_ID_SERVICE address=$address uuid=$serviceUuid " +
                    "type=${service.type} characteristicCount=${characteristics.size}",
            )
            characteristics.forEach { characteristic ->
                val properties = describeProperties(characteristic.properties)
                Log.d(
                    TAG,
                    "BLE_ID_CHARACTERISTIC address=$address service=$serviceUuid " +
                        "uuid=${characteristic.uuid.toString().lowercase()} properties=$properties " +
                        "permissions=${characteristic.permissions}",
                )
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    readable += characteristic
                }
            }
        }
        Log.d(
            TAG,
            "BLE_ID_READABLE address=$address count=${readable.size} " +
                "standardIdentityCandidates=${readable.count(::isStandardIdentityCharacteristic)}",
        )
        return readable
    }

    fun logReadStart(address: String, characteristic: BluetoothGattCharacteristic) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "BLE_ID_READ_START address=$address service=${serviceUuid(characteristic)} " +
                "characteristic=${characteristic.uuid.toString().lowercase()} " +
                "likelyIdentity=${isStandardIdentityCharacteristic(characteristic)}",
        )
    }

    fun logReadResult(
        address: String,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        if (!BuildConfig.DEBUG) return
        val identity = isStandardIdentityCharacteristic(characteristic)
        val text = if (identity) value.toSafeAscii() else "<redacted>"
        Log.d(
            TAG,
            "BLE_ID_READ_RESULT address=$address service=${serviceUuid(characteristic)} " +
                "characteristic=${characteristic.uuid.toString().lowercase()} status=$status " +
                "length=${value.size} sha256=${value.sha256()} ascii=$text",
        )
    }

    fun logReadSkipped(address: String, characteristic: BluetoothGattCharacteristic, reason: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "BLE_ID_READ_SKIPPED address=$address service=${serviceUuid(characteristic)} " +
                "characteristic=${characteristic.uuid.toString().lowercase()} reason=$reason",
        )
    }

    fun isStandardIdentityCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean =
        characteristic.uuid.toString().lowercase() in standardIdentityCharacteristicUuids ||
            characteristic.service?.uuid?.toString()?.lowercase() == DEVICE_INFORMATION_SERVICE_UUID

    private fun serviceUuid(characteristic: BluetoothGattCharacteristic): String =
        characteristic.service?.uuid?.toString()?.lowercase() ?: "<unknown>"

    private fun describeProperties(properties: Int): String = buildList {
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BROADCAST")
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("SIGNED_WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("EXTENDED_PROPS")
    }.joinToString("|").ifBlank { "NONE" }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun ByteArray.toSafeAscii(): String = decodeToString()
        .filter { it == '\t' || it == '\n' || it == '\r' || it in ' '..'~' }
        .trim()
        .take(96)
        .ifBlank { "<non-printable>" }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .toHex()
}
