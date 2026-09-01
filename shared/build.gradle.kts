plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.juren233.easyopen.shared"
        compileSdk = 37
        minSdk = 33
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "EasyOpenShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            implementation("androidx.navigation3:navigation3-runtime:1.1.4")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            api("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
            api("top.yukonga.miuix.kmp:miuix-blur:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.3")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
