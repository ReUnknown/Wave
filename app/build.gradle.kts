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
        versionCode = 100
        versionName = "0.9.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // A test build with release-level speed: code shrunk and optimized by R8, not debuggable
        // (so Android runs it fully optimized), Compose's startup profiles installed. Signed with
        // the debug key, so it installs over (and keeps the data of) the debug build.
        // 1.0 will get a real release build with its own key.
        create("fast") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
    implementation(project(":innertube"))
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
    implementation(libs.androidx.palette)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.compose.ui.tooling)
}
