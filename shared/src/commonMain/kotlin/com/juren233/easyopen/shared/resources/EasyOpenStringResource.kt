package com.juren233.easyopen.shared.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

/**
 * Reads a shared UI string while allowing iOS to avoid the broken runtime
 * Compose Resources path observed in build 51.
 */
@Composable
internal expect fun easyOpenStringResource(
    resource: StringResource,
    vararg formatArgs: Any,
): String
