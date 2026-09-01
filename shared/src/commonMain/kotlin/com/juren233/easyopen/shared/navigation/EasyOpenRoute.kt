package com.juren233.easyopen.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface EasyOpenRoute : NavKey {
    @Serializable
    data object Home : EasyOpenRoute

    @Serializable
    data object AddDevice : EasyOpenRoute

    @Serializable
    data object OnboardingPairing : EasyOpenRoute

    @Serializable
    data object ScanImport : EasyOpenRoute

    @Serializable
    data object Settings : EasyOpenRoute
}
