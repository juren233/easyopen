package com.juren233.easyopen.shared.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import top.yukonga.miuix.kmp.basic.TextField

/**
 * Numeric text field shared by Android and iOS pages.
 *
 * Input filtering belongs here because it is UI behavior, not an Android
 * resource or platform capability. Labels remain supplied by the host until
 * the shared text layer is fully migrated.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = label,
        modifier = modifier,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
