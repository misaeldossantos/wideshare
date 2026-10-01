import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
    implementation("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.2.2")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

tasks.test {
    useJUnitPlatform()
    systemProperty("screenshots", project.findProperty("screenshots") ?: "")
}

compose.desktop {
    application {
        mainClass = "wideshare.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WideShare"
            packageVersion = "1.0.0"
            description = "Share one mouse and keyboard across computers (Windows/Linux)"
            linux { iconFile.set(project.file("packaging/icon.png")) }
            windows { iconFile.set(project.file("packaging/icon.ico")) }
        }
    }
}
