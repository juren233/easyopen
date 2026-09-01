package com.juren233.easyopen.shared.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Stores iOS opener passwords in the system Keychain.
 *
 * Device metadata remains in NSUserDefaults, but the six-digit opener secret is
 * removed from that metadata whenever the Keychain operation succeeds. If a
 * device reports a Keychain error, the caller keeps the legacy value in memory
 * instead of silently losing the user's opener configuration.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosKeychainStore {
    private const val SERVICE = "com.juren233.easyopen.ios.device-password"

    fun readPassword(identifier: String): String? {
        val account = normalizeAccount(identifier)
        if (account.isBlank()) return null
        return withQuery(account, includeData = true) { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr.reinterpret())
                if (status != errSecSuccess || result.value == null) return@memScoped null
                (CFBridgingRelease(result.value) as? NSData)
                    ?.toByteArray()
                    ?.decodeToString()
            }
        }
    }

    /** Returns false when the system rejected the write. */
    fun writePassword(identifier: String, password: String): Boolean {
        val account = normalizeAccount(identifier)
        if (account.isBlank()) return false
        return runCatching {
            if (password.isBlank()) {
                val status = withQuery(account, includeData = false) { query ->
                    SecItemDelete(query)
                }
                return@runCatching status == errSecSuccess || status == errSecItemNotFound
            }

            val passwordData = password.toNSData()
            withRetained(passwordData) { retained ->
                val value = retained.single()
                val status = withQuery(account, includeData = false) { query ->
                    val addQuery = memScoped {
                        cfDictionaryOf(
                            kSecClass to kSecClassGenericPassword,
                            kSecAttrService to queryValue(query, kSecAttrService),
                            kSecAttrAccount to queryValue(query, kSecAttrAccount),
                            kSecValueData to value,
                        )
                    }
                    try {
                        SecItemAdd(addQuery, null)
                    } finally {
                        CFRelease(addQuery)
                    }
                }
                if (status == errSecSuccess) {
                    true
                } else if (status == errSecDuplicateItem) {
                    withQuery(account, includeData = false) { query ->
                        val update = memScoped { cfDictionaryOf(kSecValueData to value) }
                        try {
                            SecItemUpdate(query, update) == errSecSuccess
                        } finally {
                            CFRelease(update)
                        }
                    }
                } else {
                    false
                }
            }
        }.getOrDefault(false)
    }

    fun deletePassword(identifier: String): Boolean = writePassword(identifier, "")

    private fun <T> withQuery(
        account: String,
        includeData: Boolean,
        block: (CFDictionaryRef?) -> T,
    ): T = withRetained(SERVICE, account) { retained ->
        val service = retained[0]
        val accountRef = retained[1]
        val query = memScoped {
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to accountRef,
                *(if (includeData) {
                    arrayOf(
                        kSecReturnData to kCFBooleanTrue,
                        kSecMatchLimit to kSecMatchLimitOne,
                    )
                } else {
                    emptyArray()
                }),
            )
        }
        try {
            block(query)
        } finally {
            CFRelease(query)
        }
    }

    /** Reads an already-retained attribute from a temporary query dictionary. */
    private fun queryValue(query: CFDictionaryRef?, key: CFStringRef?): CFTypeRef? =
        CFDictionaryGetValue(query, key)

    private inline fun <T> withRetained(
        vararg values: Any?,
        block: (Array<CFTypeRef?>) -> T,
    ): T {
        val retained = Array(values.size) { index -> CFBridgingRetain(values[index]) }
        return try {
            block(retained)
        } finally {
            retained.forEach { reference ->
                if (reference != null) CFBridgingRelease(reference)
            }
        }
    }

    private fun MemScope.cfDictionaryOf(
        vararg items: Pair<CFStringRef?, CFTypeRef?>,
    ): CFDictionaryRef? {
        val keys = allocArrayOf(*items.map { it.first }.toTypedArray())
        val values = allocArrayOf(*items.map { it.second }.toTypedArray())
        return CFDictionaryCreate(
            kCFAllocatorDefault,
            keys.reinterpret(),
            values.reinterpret(),
            items.size.convert(),
            null,
            null,
        )
    }

    private fun normalizeAccount(identifier: String): String = identifier.trim().lowercase()

    private fun String.toNSData(): NSData = encodeToByteArray().usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = length.toULong())
    }

    private fun NSData.toByteArray(): ByteArray =
        bytes?.reinterpret<ByteVar>()?.readBytes(length.toInt()) ?: ByteArray(0)
}
