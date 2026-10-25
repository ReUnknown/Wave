// Wave for computers: Windows, macOS and Linux.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// FFmpeg's native libraries for the computer this is built on (each installer bundles its own).
val javacppPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> if (arm) "macosx-arm64" else "macosx-x86_64"
        os.contains("win") -> "windows-x86_64"
        else -> if (arm) "linux-arm64" else "linux-x86_64"
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.material.icons)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(project(":innertube"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.javacv) {
        // Only FFmpeg is used; the rest of JavaCV's computer-vision libraries stay out.
        listOf("opencv", "flycapture", "libdc1394", "libfreenect", "libfreenect2", "librealsense", "librealsense2",
            "videoinput", "artoolkitplus", "flandmark", "leptonica", "tesseract", "openblas").forEach { exclude(group = "org.bytedeco", module = it) }
    }
    implementation("${libs.ffmpeg.get()}:$javacppPlatform")
    implementation("${libs.javacpp.get()}:$javacppPlatform")
}

compose.desktop {
    application {
        mainClass = "app.wave.desktop.MainKt"
        // FFmpeg is loaded as a native library.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Wave"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<JavaExec>("probe") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.wave.desktop.ProbeKt")
}
