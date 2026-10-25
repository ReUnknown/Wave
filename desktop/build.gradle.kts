// Wave for computers: Windows, macOS and Linux.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The computer app runs (and ships) on JetBrains Runtime 21, which draws native window chrome best.
val waveRuntime = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
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
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.swing)
    implementation("net.java.dev.jna:jna-platform:5.17.0")
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
    // JetBrains Runtime's window API: Wave's colors under the Windows title bar, like on macOS.
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
}

// The version the computer app shows and checks updates against (jpackage needs plain numbers).
val waveVersion = "1.1.0"
val buildingOnMac = System.getProperty("os.name").lowercase().contains("mac")

compose.desktop {
    application {
        mainClass = "app.wave.desktop.MainKt"
        javaHome = (project.findProperty("waveJavaHome") as String?) ?: waveRuntime.get().metadata.installationPath.asFile.absolutePath
        // `./gradlew :desktop:run -Ppreview` browses as a guest, for development.
        if (project.hasProperty("preview")) {
            args += "--preview"
            jvmArgs += "-Dwave.debug=true"
            (project.findProperty("gcLog") as String?)?.let { jvmArgs += "-Xlog:gc:file=$it" }
        }
        jvmArgs += listOf(
            // FFmpeg is loaded as a native library.
            "--enable-native-access=ALL-UNNAMED",
            // Smoothness: FFmpeg's native bridge (JavaCPP) asks for a full garbage collection whenever
            // its memory count passes a limit tied to the heap, which froze the window for ~250 ms every
            // half minute. No such limit, and any collection that's asked for runs alongside the app.
            "-Dorg.bytedeco.javacpp.maxBytes=0",
            "-Dorg.bytedeco.javacpp.maxPhysicalBytes=0",
            "-XX:+ExplicitGCInvokesConcurrent",
            // Memory: G1 with short pauses, a small starting heap, and memory handed back to the
            // system while idle (ZGC kept far more and Windows counts its memory several times over).
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=8",
            "-Xms48m",
            "-XX:MinHeapFreeRatio=10",
            "-XX:MaxHeapFreeRatio=25",
            "-XX:G1PeriodicGCInterval=15000",
            "-XX:+UseStringDeduplication",
            "-XX:ReservedCodeCacheSize=64m",
            "-Xss512k",
            "-Dwave.version=$waveVersion",
        )
        if (buildingOnMac) {
            jvmArgs += listOf(
                // The app's name in the macOS menu bar and dock.
                "-Dapple.awt.application.name=Wave",
                "-Xdock:name=Wave",
            )
        }
        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false)
            optimize.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            // Windows gets its own installer (windows/wave.iss, built in CI from this app folder).
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "Wave"
            packageVersion = waveVersion.substringBefore('-')
            description = "Your YouTube Music. Nothing in the way."
            vendor = "Wave"
            copyright = "GPL-3.0"
            // Only what Wave uses (not the JDK's compiler and tools, nor JetBrains Runtime's Chromium).
            modules(
                "java.desktop", "java.instrument", "java.management", "java.logging", "java.naming",
                "java.net.http", "java.prefs", "java.sql", "java.xml", "java.scripting",
                "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.accessibility",
                "jdk.management", "jdk.zipfs",
            )
            macOS {
                bundleID = "app.wave.desktop"
                iconFile.set(project.file("icons/wave.icns"))
                appCategory = "public.app-category.music"
            }
            windows {
                // The icon of Wave.exe; the installer itself is windows/wave.iss.
                iconFile.set(project.file("icons/wave.ico"))
            }
            linux {
                iconFile.set(project.file("icons/wave.png"))
                packageName = "wave"
            }
        }
    }
}

tasks.register<JavaExec>("probe") {
    group = "verification"
    javaLauncher.set(waveRuntime)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.wave.desktop.ProbeKt")
}


// The Google sign-in window: a tiny helper on the web engine each OS already has (see signin/).
// It's built (or copied) into the app's resources for whichever computer this build is for.
val hostOs = System.getProperty("os.name").lowercase()
val hostArm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
val resourceTarget = when {
    hostOs.contains("mac") -> if (hostArm) "macos-arm64" else "macos-x64"
    hostOs.contains("win") -> "windows-x64"
    else -> if (hostArm) "linux-arm64" else "linux-x64"
}
val signInOut = layout.buildDirectory.dir("appResources/$resourceTarget")

val buildSignIn by tasks.registering {
    group = "build"
    description = "Builds the sign-in helper for this computer into the app resources."
    // Plain values only, so the task works with Gradle's configuration cache.
    val os = hostOs
    val out = signInOut.get().asFile
    val swiftSource = file("signin/macos/WaveSignIn.swift")
    val windowsBuilt = file("signin/windows/bin/Release/net48")
    val linuxScript = file("signin/linux/wave-signin.py")
    val exec = providers
    inputs.dir("signin")
    outputs.dir(out)
    doLast {
        out.mkdirs()
        when {
            os.contains("mac") -> {
                val binary = File(out, "WaveSignIn")
                exec.exec { commandLine("swiftc", "-O", "-o", binary.absolutePath, swiftSource.absolutePath) }.result.get().assertNormalExitValue()
                exec.exec { commandLine("codesign", "--force", "-s", "-", binary.absolutePath) }.result.get()
            }
            os.contains("win") -> {
                // Built beforehand with `dotnet build desktop/signin/windows -c Release` (the CI does this).
                check(windowsBuilt.exists()) { "Build the Windows sign-in helper first: dotnet build desktop/signin/windows -c Release" }
                windowsBuilt.copyRecursively(out, overwrite = true)
            }
            else -> linuxScript.copyTo(File(out, "wave-signin.py"), overwrite = true).setExecutable(true)
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(buildSignIn) }

tasks.register<JavaExec>("renderMini") {
    javaLauncher.set(waveRuntime)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.wave.desktop.RenderMiniKt")
    args("blinding lights", "playing", layout.buildDirectory.dir("mini").get().asFile.absolutePath)
}

tasks.register<JavaExec>("renderApp") {
    javaLauncher.set(waveRuntime)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.wave.desktop.RenderApp")
    args(layout.buildDirectory.dir("mini").get().asFile.absolutePath)
}
