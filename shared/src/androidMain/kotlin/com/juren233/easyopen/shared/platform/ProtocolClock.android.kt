package com.juren233.easyopen.shared.platform

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L
