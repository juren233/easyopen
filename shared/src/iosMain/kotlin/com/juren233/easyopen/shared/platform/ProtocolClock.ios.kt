package com.juren233.easyopen.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun currentEpochSeconds(): Long = time(null)
