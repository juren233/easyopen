package com.juren233.easyopen.shared.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun easyOpenStringResource(
    resource: StringResource,
    vararg formatArgs: Any,
): String = stringResource(resource, *formatArgs)
