import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.wave.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.wave.music"
        minSdk = 31
        targetSdk = 37
        versionCode = 114
        versionName = "1.1.0-beta.3"
        // Every phone, tablet and Googlebook Wave targets is 64-bit: no 32-bit native code.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Wave is in English; drop other languages' strings that libraries bring along.
    androidResources { localeFilters += "en" }

    packaging {
        resources.excludes += listOf(
            "META-INF/*.txt", "META-INF/*.md", "META-INF/*.version", "META-INF/*.kotlin_module",
            "META-INF/androidx/**", "DebugProbesKt.bin", "kotlin-tooling-metadata.json",
        )
    }

    // Wave's own signing key (keystore/, never committed). Release builds must always use the
    // same key, or Android won't install them as updates.
    val keystoreProps = rootProject.file("keystore/keystore.properties")
    signingConfigs {
        if (keystoreProps.exists()) {
            val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // The real build: code shrunk and optimized by R8, not debuggable (so Android runs it fully
        // optimized), Compose's startup profiles installed, signed with Wave's own key.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    coreLibraryDesugaring(libs.desugaring)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.timber)
    implementation(libs.reorderable)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.compose.ui.tooling)
}
