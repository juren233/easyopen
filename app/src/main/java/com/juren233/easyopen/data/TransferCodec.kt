package com.juren233.easyopen.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import com.juren233.easyopen.shared.model.CoreDeviceProfile
import com.juren233.easyopen.shared.transfer.EasyOpenQrCodec
import com.juren233.easyopen.shared.transfer.EasyOpenTransferProfile
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes opener profiles for QR sharing and local backup.
 *
 * QR payloads are authenticated and encrypted with AES-GCM. The fixed app key
 * is intentionally only a transport-obfuscation key: the QR code itself is
 * still a secret and must only be shown to a trusted recipient.
 */
object TransferCodec {
    private const val LEGACY_SHARE_PREFIX = "EASYOPEN-SHARE:1:"
    private const val SHARE_PREFIX = "EASYOPEN-SHARE:2:"
    private const val COMPACT_SHARE_VERSION = 1
    private const val MAX_SHARE_DEVICES = 255
    private const val MAX_SHARE_NAME_BYTES = 255
    private const val BACKUP_VERSION = 1
    private const val KEY_SEED = "EasyOpen opener transfer v1"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
    }
    private val key: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(KEY_SEED.toByteArray(StandardCharsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    data class BackupSnapshot(
        val devices: List<DeviceProfile>,
        val activeAddress: String,
        val themeMode: Int,
        val monetEnabled: Boolean,
        val autoUnlockOnAppOpen: Boolean = false,
        val autoConnectEnabled: Boolean = true,
        val autoConnectRange: Int = AutoConnectSettings.DEFAULT_RANGE,
        val customAutoConnectRssi: Int = AutoConnectSettings.DEFAULT_RSSI_THRESHOLD,
    )

    /**
     * Encodes shares as a compact binary envelope instead of repeating JSON
     * field names. This keeps normal one-device QR codes at a low QR version
     * while preserving encryption and every opener setting.
     */
    fun encodeShare(devices: List<DeviceProfile>): String =
        EasyOpenQrCodec.encode(devices.map { it.toCoreProfile() })

    fun decodeShare(payload: String): List<DeviceProfile>? {
        if (EasyOpenQrCodec.isPayload(payload)) {
            return EasyOpenQrCodec.decode(payload)?.map { it.toDeviceProfile() }
        }
        return when {
            payload.startsWith(SHARE_PREFIX) -> decodeEncryptedShare(
                payload.removePrefix(SHARE_PREFIX),
                ::decodeCompactShare,
            )
            payload.startsWith(LEGACY_SHARE_PREFIX) -> decodeEncryptedShare(
                payload.removePrefix(LEGACY_SHARE_PREFIX),
                ::decodeLegacyShare,
            )
            else -> null
        }
    }

    fun encodeBackup(
        devices: List<DeviceProfile>,
        activeAddress: String,
        themeMode: Int,
        monetEnabled: Boolean,
        autoUnlockOnAppOpen: Boolean = false,
        autoConnectEnabled: Boolean = true,
        autoConnectRange: Int = AutoConnectSettings.DEFAULT_RANGE,
        customAutoConnectRssi: Int = AutoConnectSettings.DEFAULT_RSSI_THRESHOLD,
    ): String {
        return json.encodeToString(
            BackupEnvelope(
                version = BACKUP_VERSION,
                activeAddress = null,
                activeAndroidMac = DeviceStore.normalizeAddress(activeAddress),
                themeMode = themeMode.coerceIn(0, 2),
                monetEnabled = monetEnabled,
                autoUnlockOnAppOpen = autoUnlockOnAppOpen,
                autoConnectEnabled = autoConnectEnabled,
                autoConnectRange = AutoConnectSettings.normalizeRange(autoConnectRange),
                customAutoConnectRssi = AutoConnectSettings.normalizeRssiThreshold(customAutoConnectRssi),
                devices = devices.map { it.toTransferProfile() },
            ),
        )
    }

    fun decodeBackup(raw: String): BackupSnapshot? = runCatching {
        val envelope = json.decodeFromString<BackupEnvelope>(raw)
        require(envelope.version == BACKUP_VERSION)
        val devices = decodeProfiles(envelope.devices, allowUnbound = true)
        require(devices.isNotEmpty())
        BackupSnapshot(
            devices = devices,
            activeAddress = DeviceStore.normalizeAddress(
                (envelope.activeAndroidMac ?: envelope.activeAddress).orEmpty(),
            )
                .takeIf { address -> devices.any { it.address.equals(address, ignoreCase = true) } }
                ?: devices.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
            themeMode = envelope.themeMode.coerceIn(0, 2),
            monetEnabled = envelope.monetEnabled,
            autoUnlockOnAppOpen = envelope.autoUnlockOnAppOpen,
            autoConnectEnabled = envelope.autoConnectEnabled,
            autoConnectRange = AutoConnectSettings.normalizeRange(envelope.autoConnectRange),
            customAutoConnectRssi = AutoConnectSettings.normalizeRssiThreshold(envelope.customAutoConnectRssi),
        )
    }.getOrNull()

    private fun encryptShare(plaintext: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(plaintext)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return SHARE_PREFIX + encoder.encodeToString(iv) + "." + encoder.encodeToString(encrypted)
    }

    private fun decodeEncryptedShare(
        encodedPayload: String,
        decodePlaintext: (ByteArray) -> List<DeviceProfile>?,
    ): List<DeviceProfile>? = runCatching {
        val parts = encodedPayload.split('.', limit = 2)
        require(parts.size == 2)
        val decoder = Base64.getUrlDecoder()
        val iv = decoder.decode(parts[0])
        require(iv.size == IV_BYTES)
        val encrypted = decoder.decode(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        decodePlaintext(cipher.doFinal(encrypted))
    }.getOrNull()

    private fun decodeLegacyShare(plaintext: ByteArray): List<DeviceProfile>? {
        val envelope = json.decodeFromString<ShareEnvelope>(
            String(plaintext, StandardCharsets.UTF_8),
        )
        if (envelope.version != 1) return null
        return decodeProfiles(envelope.devices).takeIf { it.isNotEmpty() }
    }

    private fun writeCompactProfile(output: DataOutputStream, profile: DeviceProfile) {
        val addressBytes = profile.address
            .trim()
            .split(':')
            .map { it.toInt(16) }
        require(addressBytes.size == 6) { "Invalid Bluetooth address" }
        addressBytes.forEach(output::writeByte)

        val passwordBytes = profile.password.toByteArray(StandardCharsets.US_ASCII)
        require(passwordBytes.size == 6 && passwordBytes.all { it in '0'.code..'9'.code }) {
            "Invalid opener password"
        }
        output.write(passwordBytes)
        output.writeByte(profile.attribute.coerceIn(0, 1))
        output.writeShort(profile.openTimeMs.coerceIn(0, 60_000))
        output.writeShort(profile.waitTimeMs.coerceIn(0, 60_000))
        output.writeShort(profile.closeTimeMs.coerceIn(0, 60_000))
        output.writeByte(profile.batteryLevel?.coerceIn(1, 5) ?: 0)

        val nameBytes = profile.name.ifBlank { DeviceStore.DEFAULT_NAME }
            .toByteArray(StandardCharsets.UTF_8)
        require(nameBytes.size <= MAX_SHARE_NAME_BYTES) { "Opener name is too long" }
        output.writeByte(nameBytes.size)
        output.write(nameBytes)
    }

    private fun decodeCompactShare(plaintext: ByteArray): List<DeviceProfile>? = runCatching {
        DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
            require(input.readUnsignedByte() == COMPACT_SHARE_VERSION)
            val count = input.readUnsignedByte()
            require(count in 1..MAX_SHARE_DEVICES)
            val profiles = buildList(count) {
                repeat(count) {
                    val addressBytes = ByteArray(6).also(input::readFully)
                    val address = addressBytes.joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
                    val passwordBytes = ByteArray(6).also(input::readFully)
                    val password = String(passwordBytes, StandardCharsets.US_ASCII)
                    val attribute = input.readUnsignedByte()
                    val openTimeMs = input.readUnsignedShort()
                    val waitTimeMs = input.readUnsignedShort()
                    val closeTimeMs = input.readUnsignedShort()
                    val batteryLevel = input.readUnsignedByte().takeIf { it in 1..5 }
                    val nameLength = input.readUnsignedByte()
                    require(nameLength <= MAX_SHARE_NAME_BYTES)
                    val nameBytes = ByteArray(nameLength).also(input::readFully)
                    add(
                        DeviceProfile(
                            name = String(nameBytes, StandardCharsets.UTF_8).ifBlank { DeviceStore.DEFAULT_NAME },
                            address = address,
                            password = password,
                            attribute = attribute.coerceIn(0, 1),
                            openTimeMs = openTimeMs.coerceIn(0, 60_000),
                            waitTimeMs = waitTimeMs.coerceIn(0, 60_000),
                            closeTimeMs = closeTimeMs.coerceIn(0, 60_000),
                            batteryLevel = batteryLevel,
                            hardwareMac = address,
                        ),
                    )
                }
            }
            require(input.available() == 0)
            decodeProfiles(profiles.map { it.toTransferProfile() }).takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    private fun DeviceProfile.toCoreProfile(): CoreDeviceProfile = CoreDeviceProfile(
        name = name,
        password = password,
        attribute = attribute,
        openTimeMs = openTimeMs,
        waitTimeMs = waitTimeMs,
        closeTimeMs = closeTimeMs,
        batteryLevel = batteryLevel,
        hardwareMac = DeviceStore.normalizeHardwareMac(hardwareMac ?: address),
    )

    private fun CoreDeviceProfile.toDeviceProfile(): DeviceProfile {
        val hardwareMac = DeviceStore.normalizeHardwareMac(this.hardwareMac)
        return DeviceProfile(
            name = name.ifBlank { DeviceStore.DEFAULT_NAME },
            address = hardwareMac.orEmpty(),
            password = password,
            attribute = attribute.coerceIn(0, 1),
            openTimeMs = openTimeMs.coerceIn(0, 60_000),
            waitTimeMs = waitTimeMs.coerceIn(0, 60_000),
            closeTimeMs = closeTimeMs.coerceIn(0, 60_000),
            batteryLevel = batteryLevel?.takeIf { it in 1..5 },
            hardwareMac = hardwareMac,
        )
    }

    private fun DeviceProfile.toTransferProfile(): EasyOpenTransferProfile =
        EasyOpenTransferProfile.fromCoreProfile(
            profile = com.juren233.easyopen.shared.model.CoreDeviceProfile(
                name = name,
                password = password,
                attribute = attribute,
                openTimeMs = openTimeMs,
                waitTimeMs = waitTimeMs,
                closeTimeMs = closeTimeMs,
                batteryLevel = batteryLevel,
                hardwareMac = DeviceStore.normalizeHardwareMac(hardwareMac ?: address),
            ),
            androidMac = DeviceStore.normalizeHardwareMac(hardwareMac ?: address),
        )

    private fun decodeProfiles(
        profiles: List<EasyOpenTransferProfile>,
        allowUnbound: Boolean = false,
    ): List<DeviceProfile> {
        return profiles.mapNotNull { item ->
            val address = DeviceStore.normalizeAddress(item.resolvedAndroidMac().orEmpty())
            val password = item.password
            val addressIsValid = isValidAddress(address)
            if ((!allowUnbound && !addressIsValid) || (allowUnbound && address.isNotBlank() && !addressIsValid)) {
                return@mapNotNull null
            }
            if (password.length != 6 || password.any { !it.isDigit() }) return@mapNotNull null
            DeviceProfile(
                name = item.name.ifBlank { DeviceStore.DEFAULT_NAME },
                address = address,
                password = password,
                attribute = item.attribute.coerceIn(0, 1),
                openTimeMs = item.openTimeMs.coerceIn(0, 60_000),
                waitTimeMs = item.waitTimeMs.coerceIn(0, 60_000),
                closeTimeMs = item.closeTimeMs.coerceIn(0, 60_000),
                batteryLevel = item.batteryLevel?.takeIf { it in 1..5 },
                hardwareMac = address.takeIf(String::isNotBlank),
            )
        }.let { decoded ->
            val seenBoundAddresses = mutableSetOf<String>()
            decoded.filter { profile ->
                profile.address.isBlank() || seenBoundAddresses.add(profile.address)
            }
        }
    }

    private fun isValidAddress(address: String): Boolean {
        return address.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))
    }

    @Serializable
    private data class ShareEnvelope(
        val version: Int,
        val devices: List<EasyOpenTransferProfile>,
    )

    @Serializable
    private data class BackupEnvelope(
        val version: Int,
        val activeAddress: String? = null,
        val activeAndroidMac: String? = null,
        val themeMode: Int,
        val monetEnabled: Boolean,
        val autoUnlockOnAppOpen: Boolean = false,
        val autoConnectEnabled: Boolean = true,
        val autoConnectRange: Int = AutoConnectSettings.DEFAULT_RANGE,
        val customAutoConnectRssi: Int = AutoConnectSettings.DEFAULT_RSSI_THRESHOLD,
        val devices: List<EasyOpenTransferProfile>,
    )
}
