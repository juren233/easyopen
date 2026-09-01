package com.juren233.easyopen.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juren233.easyopen.data.AppSettings
import easyopen.shared.generated.resources.Res
import easyopen.shared.generated.resources.back
import easyopen.shared.generated.resources.backup_title
import easyopen.shared.generated.resources.data_category
import easyopen.shared.generated.resources.monet_color_title
import easyopen.shared.generated.resources.personalization_category
import easyopen.shared.generated.resources.restore_title
import easyopen.shared.generated.resources.settings_title
import easyopen.shared.generated.resources.theme_color_title
import easyopen.shared.generated.resources.theme_dark
import easyopen.shared.generated.resources.theme_light
import easyopen.shared.generated.resources.theme_system
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/**
 * Platform-free settings surface. File creation, file selection and toast
 * feedback stay in the Android/iOS host; this function only renders settings
 * and emits user intents.
 */
@Composable
fun SettingsPageContent(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onMonetChange: (Boolean) -> Unit,
    onAutoUnlockOnAppOpenChange: (Boolean) -> Unit,
    onAutoConnectEnabledChange: (Boolean) -> Unit,
    onAutoConnectRangeChange: (Int) -> Unit,
    onCustomAutoConnectRssiChange: (Int) -> Unit,
    onBackupRequested: () -> Unit,
    onRestoreRequested: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val themeOptions = listOf(
        stringResource(Res.string.theme_system),
        stringResource(Res.string.theme_light),
        stringResource(Res.string.theme_dark),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { SmallTitle(text = stringResource(Res.string.personalization_category)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        WindowDropdownPreference(
                            title = stringResource(Res.string.theme_color_title),
                            items = themeOptions,
                            selectedIndex = settings.themeMode,
                            onSelectedIndexChange = onThemeModeChange,
                        )
                        SwitchPreference(
                            title = stringResource(Res.string.monet_color_title),
                            checked = settings.monetEnabled,
                            onCheckedChange = onMonetChange,
                        )
                    }
                }
            }
            item {
                AutomationSettingsSection(
                    settings = settings,
                    onAutoUnlockOnAppOpenChange = onAutoUnlockOnAppOpenChange,
                    onAutoConnectEnabledChange = onAutoConnectEnabledChange,
                    onAutoConnectRangeChange = onAutoConnectRangeChange,
                    onCustomAutoConnectRssiChange = onCustomAutoConnectRssiChange,
                )
            }
            item { SmallTitle(text = stringResource(Res.string.data_category)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        ArrowPreference(
                            title = stringResource(Res.string.backup_title),
                            onClick = onBackupRequested,
                        )
                        ArrowPreference(
                            title = stringResource(Res.string.restore_title),
                            onClick = onRestoreRequested,
                        )
                    }
                }
            }
        }
    }
}
