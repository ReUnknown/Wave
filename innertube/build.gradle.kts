// YouTube Music (InnerTube) client, taken from Metrolist (GPL-3.0):
// https://github.com/MetrolistGroup/Metrolist/tree/main/innertube
// Builds for Android and for desktop (Windows, macOS, Linux) from the same code.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    }
    androidLibrary {
        namespace = "com.metrolist.innertube"
        compileSdk = 37
        minSdk = 31
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.innertubex)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.encoding)
        }
    }
}
