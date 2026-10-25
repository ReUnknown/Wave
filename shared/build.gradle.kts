// Wave's app itself (screens, player logic, data), shared by Android and computers.
// Each platform supplies its few native pieces: the audio player, sign-in, files.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
    }
    androidLibrary {
        namespace = "app.wave.shared"
        compileSdk = 37
        minSdk = 31
    }
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-Xexpect-actual-classes",
        )
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":innertube"))
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.ui.backhandler)
            api(libs.cmp.animation)
            api(libs.cmp.material3)
            api(libs.cmp.material.icons)
            api(libs.coil.compose)
            api(libs.coil.network)
            api(libs.androidx.graphics.shapes)
            api(libs.datastore.preferences.core)
            api(libs.reorderable)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.webkit)
            implementation(libs.androidx.core.ktx)
        }
    }
}
