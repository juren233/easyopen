package com.juren233.easyopen.shared.platform

import java.security.SecureRandom

private val qrSecureRandom = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(qrSecureRandom::nextBytes)
