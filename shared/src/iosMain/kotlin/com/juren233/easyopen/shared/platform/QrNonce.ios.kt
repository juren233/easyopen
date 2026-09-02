package com.juren233.easyopen.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0)
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { output ->
        val status = output.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        }
        check(status == 0) { "Unable to generate a secure QR nonce: $status" }
    }
}
