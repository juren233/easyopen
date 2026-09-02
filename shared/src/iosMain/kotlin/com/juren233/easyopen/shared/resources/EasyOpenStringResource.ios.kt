package com.juren233.easyopen.shared.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

/**
 * iOS fallback for shared UI strings.
 *
 * Do not call Compose's iOS ResourceReader here: the device screenshots show
 * that static strings read through that path are corrupted even though the
 * source XML and CVR records are valid. Dynamic strings continue to use their
 * normal Kotlin values.
 */
@Composable
internal actual fun easyOpenStringResource(
    resource: StringResource,
    vararg formatArgs: Any,
): String = easyOpenStringValues[resource.key]
    ?.replaceEasyOpenStringArguments(formatArgs)
    ?: error("Missing iOS fallback string: ${resource.key}")
