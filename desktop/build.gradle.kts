// Offscreen previews of Wave Prisma, rendered on a computer (the computer app itself was retired
// at Wave 1.2.1; Wave is Android only now). `./gradlew :desktop:renderApp -Pw=412 -Ph=900 -Pmode=home`
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
}

tasks.register<JavaExec>("renderApp") {
    group = "verification"
    description = "Renders Wave at -Pw x -Ph dp in -Pmode (home, playing, player, queue, lyrics, menu, settings…)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.wave.desktop.RenderApp")
    args(layout.buildDirectory.dir("mini").get().asFile.absolutePath, project.findProperty("w") ?: "412", project.findProperty("h") ?: "900", project.findProperty("mode") ?: "home")
}
